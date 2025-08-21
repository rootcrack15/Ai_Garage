// Kamera ekranının durumunu yönetir, fotoğraf çekme ve Vertex AI işlemlerini içerir. API 24+ uyumlu.

package com.rootcrack.aigarage.screens

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.google.auth.oauth2.GoogleCredentials // Google Cloud kimlik doğrulama için gerekli
import com.rootcrack.aigarage.utils.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

@Composable
fun rememberCameraScreenStateHolder(): CameraScreenStateHolder {
    return remember { CameraScreenStateHolder() }
}

class CameraScreenStateHolder {
    val capturedImageUri = mutableStateOf<Uri?>(null)
    val lastSavedImageUri = mutableStateOf<Uri?>(null)
    val showPhotoConfirmationDialog = mutableStateOf(false)

    var takePhoto: () -> Unit = {}

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // Google Cloud Proje ID'niz
    // Bu ID, hem Vertex AI hem de Cloud Translation API için kullanılacaktır.
    private val GOOGLE_CLOUD_PROJECT_ID = "webgame-bb4ed" // <<<<<<<<<<<<< BURAYI KENDİ PROJE ID'NİZLE DÜZENLEYİN!
    private val GOOGLE_CLOUD_REGION_VERTEX_AI = "us-central1" // Vertex AI genellikle bir bölgeye özeldir
    private val GOOGLE_CLOUD_LOCATION_TRANSLATION_API = "global" // Translation API genellikle 'global' konumdadır

    suspend fun processImageWithAI(context: Context, imageUri: String, instruction: String, base64Mask: String?): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                val file = FileUtil.copyUriToAppSpecificDirectory(
                    context,
                    Uri.parse(imageUri),
                    FileUtil.DIRECTORY_NAME_APP_SPECIFIC_SUBFOLDER
                ) ?: return@withContext null

                val imageBytes = file.readBytes()
                val base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT)
                if (base64Image.isEmpty()) {
                    Log.e("CameraScreenStateHolder", "Base64 image empty")
                    return@withContext null
                }

                // Kullanıcının talimatını İngilizceye çevir (Google Cloud Translation API ile)
                // Kaynak dil otomatik olarak algılanacak, hedef dil İngilizce.
                // Çeviri başarısız olursa orijinal talimatı kullan
                val translatedInstruction = translateTextToEnglish(context, instruction) ?: instruction
                Log.d("CameraScreenStateHolder", "Orijinal talimat: '$instruction', Çevrilmiş talimat (İngilizce): '$translatedInstruction'")

                // Vertex AI için JSON Payload
                val jsonBody = JSONObject().apply {
                    put("instances", JSONArray().put(JSONObject().apply {
                        put("prompt", translatedInstruction) // Çevrilmiş İngilizce prompt
                        put("image", JSONObject().apply { // Orijinal resim
                            put("bytesBase64Encoded", base64Image)
                        })
                        if (!base64Mask.isNullOrEmpty()) {
                            put("mask", JSONObject().apply {
                                put("image", JSONObject().apply {
                                    put("bytesBase64Encoded", base64Mask)
                                })
                            })
                        }
                    }))
                    put("parameters", JSONObject().apply {
                        put("sampleCount", 1)
                        // Modelinizin desteklediği diğer parametreleri buraya ekleyebilirsiniz.
                        // Örn: put("editGenerationCount", 1)
                        // Örn: put("guidanceScale", 9)
                        // Örn: put("aspectRatio", "1:1") // Modelinize göre kontrol edin
                    })
                }
                Log.d("CameraScreenStateHolder", "Gönderilen JSON Payload: ${jsonBody.toString()}")

                // Vertex AI ve Translation API için ortak erişim token'ı
                val accessToken = getAccessToken(context)
                if (accessToken.isEmpty()) {
                    Log.e("CameraScreenStateHolder", "Erişim token'ı alınamadı")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Kimlik doğrulama hatası", Toast.LENGTH_LONG).show()
                    }
                    return@withContext null
                }

                val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://$GOOGLE_CLOUD_REGION_VERTEX_AI-aiplatform.googleapis.com/v1/projects/$GOOGLE_CLOUD_PROJECT_ID/locations/$GOOGLE_CLOUD_REGION_VERTEX_AI/publishers/google/models/imagegeneration@006:predict")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .post(requestBody)
                    .build()

                val maxRetries = 3
                var retryCount = 0
                var response: okhttp3.Response? = null
                var lastErrorBody: String? = null

                while (retryCount < maxRetries) {
                    try {
                        response = client.newCall(request).execute()
                        if (response.isSuccessful) break

                        lastErrorBody = response.body?.string()

                        if (response.code == 429) { // Too Many Requests - Hız sınırı
                            retryCount++
                            val delayMillis = 1000L * (1 shl retryCount) // Exponential backoff
                            Log.w("CameraScreenStateHolder", "Vertex AI 429 hatası. ${delayMillis}ms sonra yeniden denenecek. Deneme: $retryCount/$maxRetries")
                            kotlinx.coroutines.delay(delayMillis)
                            response.close() // Önceki yanıtı kapat
                            continue
                        }
                        // Diğer HTTP hataları için döngüden çık
                        Log.e("CameraScreenStateHolder", "Vertex AI hatası (deneme $retryCount): ${response.code} - ${response.message} - $lastErrorBody")
                        break
                    } catch (e: Exception) {
                        Log.e("CameraScreenStateHolder", "Vertex AI çağrısında istisna (deneme $retryCount): ${e.message}", e)
                        lastErrorBody = "İstek sırasında istisna: ${e.message}"
                        retryCount++
                        if(retryCount >= maxRetries) break // Max deneme sayısına ulaşıldıysa çık
                        kotlinx.coroutines.delay(1000L * (1 shl retryCount)) // Exponential backoff for network issues
                        continue
                    }
                }

                response?.use {
                    if (it.isSuccessful) {
                        val responseBody = it.body?.string()
                        if (responseBody != null) {
                            val jsonResponse = JSONObject(responseBody)
                            if (!jsonResponse.has("predictions")) {
                                Log.e("CameraScreenStateHolder", "Vertex AI yanıtında 'predictions' anahtarı bulunamadı. Yanıt: $responseBody")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Fotoğraf işlenemedi: Beklenmeyen yanıt formatı.", Toast.LENGTH_LONG).show()
                                }
                                return@withContext null
                            }
                            val predictionsArray = jsonResponse.getJSONArray("predictions")
                            if (predictionsArray.length() == 0) {
                                Log.e("CameraScreenStateHolder", "Vertex AI yanıtındaki 'predictions' dizisi boş. Yanıt: $responseBody")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Fotoğraf işlenemedi: Model bir sonuç üretmedi.", Toast.LENGTH_LONG).show()
                                }
                                return@withContext null
                            }

                            val firstPrediction = predictionsArray.getJSONObject(0)
                            val generatedImageBase64: String?

                            // Bazı modeller 'image' -> 'bytesBase64Encoded' yapısını kullanabilir.
                            if (firstPrediction.has("bytesBase64Encoded")) {
                                generatedImageBase64 = firstPrediction.getString("bytesBase64Encoded")
                            } else if (firstPrediction.has("image") && firstPrediction.getJSONObject("image").has("bytesBase64Encoded")) {
                                generatedImageBase64 = firstPrediction.getJSONObject("image").getString("bytesBase64Encoded")
                            } else {
                                Log.e("CameraScreenStateHolder", "Vertex AI yanıtında 'bytesBase64Encoded' anahtarı bulunamadı. Yanıt: $responseBody")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Fotoğraf işlenemedi: Beklenmeyen resim verisi formatı.", Toast.LENGTH_LONG).show()
                                }
                                return@withContext null
                            }

                            val imageBytesDecoded = Base64.decode(generatedImageBase64, Base64.DEFAULT)
                            val outputDir = context.getExternalFilesDir(FileUtil.DIRECTORY_NAME_APP_SPECIFIC_SUBFOLDER)
                            if (outputDir != null && !outputDir.exists()) {
                                outputDir.mkdirs()
                            }
                            // Benzersiz bir dosya adı oluştur
                            val outputFile = File(
                                outputDir,
                                "processed_${System.currentTimeMillis()}.png"
                            )
                            outputFile.writeBytes(imageBytesDecoded)
                            Log.d("CameraScreenStateHolder", "Vertex AI işlenmiş resim kaydedildi: ${outputFile.absolutePath}")
                            return@withContext Uri.fromFile(outputFile)
                        } else {
                            Log.e("CameraScreenStateHolder", "Vertex AI başarılı yanıt gövdesi boş.")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Fotoğraf işlenemedi: Boş yanıt.", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        // Hata durumunda loglanan 'lastErrorBody' Toast mesajında da gösterilebilir (kullanıcıya daha fazla bilgi vermek için)
                        Log.e("CameraScreenStateHolder", "Vertex AI son hata yanıtı: $lastErrorBody")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Fotoğraf işlenemedi. Hata kodu: ${it.code}. Tekrar Deneyiniz.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                null // Hiçbir koşul karşılanmazsa veya hata olursa null dön
            } catch (e: Exception) {
                Log.e("CameraScreenStateHolder", "Vertex AI işleme sırasında genel bir hata oluştu: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Fotoğraf işlenemedi: ${e.localizedMessage}. Tekrar Deneyiniz." , Toast.LENGTH_LONG).show()
                }
                null
            }
        }
    }

    private suspend fun getAccessToken(context: Context): String {
        return withContext(Dispatchers.IO) {
            try {
                // Bu credential'lar hem Vertex AI hem de Translation API için kullanılabilir
                val credentials = GoogleCredentials.fromStream(
                    context.assets.open("vertex-ai-credentials.json") // BU DOSYANIN assets KLASÖRÜNDE OLDUĞUNDAN EMİN OLUN
                ).createScoped("https://www.googleapis.com/auth/cloud-platform")
                credentials.refreshIfExpired()
                credentials.accessToken.tokenValue
            } catch (e: Exception) {
                Log.e("CameraScreenStateHolder", "OAuth 2.0 token alma hatası: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Kimlik doğrulamadı İnternet Lütfen Ayarlarınızı Kontrol Ediniz.", Toast.LENGTH_LONG).show()
                }
                "" // Başarısızlık durumunda boş string dön
            }
        }
    }

    // Google Cloud Translation API (v3) ile herhangi bir dilden İngilizceye çeviri fonksiyonu
    private suspend fun translateTextToEnglish(
        context: Context,
        text: String
    ): String? {
        if (text.isBlank()) return "" // Boş metin çevrilmeye çalışılmasın

        // Hedef dil her zaman İngilizce ("en")
        val targetLanguageCode = "en"

        return withContext(Dispatchers.IO) {
            try {
                val accessToken = getAccessToken(context)
                if (accessToken.isEmpty()) {
                    Log.e("CameraScreenStateHolder", "Çeviri için erişim token'ı alınamadı.")
                    return@withContext null
                }

                // Cloud Translation API v3 isteği JSON yapısı
                val requestBodyJson = JSONObject().apply {
                    put("contents", JSONArray().put(text))
                    put("targetLanguageCode", targetLanguageCode)
                    // Kaynak dil belirtilmediğinde API otomatik olarak algılar.
                    // put("sourceLanguageCode", "tr") // Artık buna gerek yok, API algılayacak
                    put("mimeType", "text/plain") // Metin içeriği için mimeType
                }

                val requestBody = requestBodyJson.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    // Translation API v3 endpoint'i
                    .url("https://translation.googleapis.com/v3/projects/$GOOGLE_CLOUD_PROJECT_ID/locations/$GOOGLE_CLOUD_LOCATION_TRANSLATION_API:translateText")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        if (responseBody != null) {
                            val jsonResponse = JSONObject(responseBody)
                            // API yanıtında, otomatik algılanan kaynak dil bilgisi de döner ("detectedLanguageCode")
                            // Bu bilgiyi loglayabilir veya analiz için kullanabilirsiniz.
                            val detectedLanguageCode = jsonResponse.optJSONArray("translations")?.optJSONObject(0)?.optString("detectedLanguageCode", "N/A")
                            Log.d("CameraScreenStateHolder", "Otomatik Algılanan Kaynak Dil: $detectedLanguageCode")

                            val translationsArray = jsonResponse.optJSONArray("translations")
                            if (translationsArray != null && translationsArray.length() > 0) {
                                val translatedText = translationsArray.getJSONObject(0).getString("translatedText")
                                Log.d("CameraScreenStateHolder", "Cloud Translation Başarılı: '$text' ($detectedLanguageCode) -> '$translatedText' (en)")
                                return@withContext translatedText
                            } else {
                                Log.e("CameraScreenStateHolder", "Cloud Translation yanıtında 'translations' dizisi boş veya yok. Yanıt: $responseBody")
                            }
                        } else {
                            Log.e("CameraScreenStateHolder", "Cloud Translation başarılı yanıt gövdesi boş.")
                        }
                    }
                    // Hata durumunda daha detaylı loglama
                    val errorBody = response.body?.string() // Hata detaylarını yakalamak için (yanıtı consume edebilir, dikkatli kullanın)
                    Log.e("CameraScreenStateHolder", "Cloud Translation API hatası: ${response.code} - ${response.message} - Yanıt: $errorBody")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Çeviri yapılamadı. Orijinal metin kullanıldı.", Toast.LENGTH_SHORT).show()
                    }
                    return@withContext null // Başarısızlık durumunda null dön
                }

            } catch (e: Exception) {
                Log.e("CameraScreenStateHolder", "Cloud Translation sırasında istisna: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Çeviri sırasında bir hata oluştu.", Toast.LENGTH_SHORT).show()
                }
                return@withContext null // İstisna durumunda null dön
            }
        }
    }
}
