// CameraScreenStateHolder.kt: Kamera ekranının durumunu yönetir, fotoğraf çekme ve Vertex AI işlemlerini içerir. API 24+ uyumlu.

package com.rootcrack.aigarage.screens

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.google.auth.oauth2.GoogleCredentials
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

                val translatedInstruction = translateInstruction(instruction)
                Log.d("CameraScreenStateHolder", "Orijinal talimat: $instruction, Çevrilmiş talimat: $translatedInstruction")

                // --- JSON Payload OLUŞTURMA - DÜZELTİLMİŞ BÖLÜM ---
                val jsonBody = JSONObject().apply {
                    put("instances", JSONArray().put(JSONObject().apply {
                        put("prompt", translatedInstruction)
                        put("image", JSONObject().apply { // Orijinal resim
                            put("bytesBase64Encoded", base64Image)
                        })
                        if (base64Mask != null) {
                            put("mask", JSONObject().apply {      // Ana 'mask' objesi
                                put("image", JSONObject().apply { // 'mask' içinde 'image' objesi
                                    put("bytesBase64Encoded", base64Mask) // Maskenin Base64 verisi 'image' objesinin altında
                                })
                            })
                        }
                    }))
                    put("parameters", JSONObject().apply {
                        put("sampleCount", 1)
                        // put("editGenerationCount", 1) // veya modelinize uygun başka bir parametre
                        // put("guidanceScale", 9)      // modelinize uygun başka bir parametre
                        // Modelinizin desteklediği parametreleri buraya ekleyin.
                        // "aspectRatio" parametresi "imagegeneration@006" modeli için geçerli olmayabilir.
                        // Gerekirse dokümantasyonu kontrol edin veya bu satırı kaldırın/değiştirin.
                        // put("aspectRatio", "1:1") // Örnek, modelinize göre kontrol edin
                    })
                }
                Log.d("CameraScreenStateHolder", "JSON Payload (DÜZELTİLMİŞ): ${jsonBody.toString()}")
                // --- --- --- --- --- --- --- --- --- --- --- --- ---

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
                    .url("https://us-central1-aiplatform.googleapis.com/v1/projects/webgame-bb4ed/locations/us-central1/publishers/google/models/imagegeneration@006:predict")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .post(requestBody)
                    .build()

                val maxRetries = 3
                var retryCount = 0
                var response: okhttp3.Response? = null
                var lastErrorBody: String? = null // Hata detaylarını saklamak için

                while (retryCount < maxRetries) {
                    try {
                        response = client.newCall(request).execute()
                        if (response.isSuccessful) break

                        lastErrorBody = response.body?.string() // Hata gövdesini al

                        if (response.code == 429) { // Too Many Requests
                            retryCount++
                            val delayMillis = 1000L * (1 shl retryCount) // Exponential backoff
                            Log.w("CameraScreenStateHolder", "Vertex AI 429 hatası. ${delayMillis}ms sonra yeniden denenecek. Deneme: $retryCount/$maxRetries")
                            kotlinx.coroutines.delay(delayMillis)
                            response.close() // Önceki response'u kapat
                            continue
                        }
                        // Diğer 4xx veya 5xx hataları için döngüden çık
                        Log.e("CameraScreenStateHolder", "Vertex AI hatası (deneme $retryCount): ${response.code} - ${response.message} - $lastErrorBody")
                        break // Yeniden deneme yapma
                    } catch (e: Exception) {
                        Log.e("CameraScreenStateHolder", "Vertex AI çağrısında istisna (deneme $retryCount): ${e.message}", e)
                        lastErrorBody = "İstek sırasında istisna: ${e.message}"
                        retryCount++ // İstisna durumunda da yeniden deneme sayısını artır
                        if(retryCount >= maxRetries) break
                        kotlinx.coroutines.delay(1000L * (1 shl retryCount)) // Exponential backoff
                        continue // İstisna sonrası yeniden dene
                    }
                }


                response?.use { // use bloğu response'u otomatik olarak kapatır
                    if (it.isSuccessful) {
                        val responseBody = it.body?.string()
                        if (responseBody != null) {
                            val jsonResponse = JSONObject(responseBody)
                            // "predictions" anahtarının var olup olmadığını kontrol et
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

                            // Modelinize göre "image" veya "bytesBase64Encoded" anahtarlarını kontrol edin.
                            // Genellikle doğrudan bytesBase64Encoded olur, ancak bazen iç içe bir "image" objesi olabilir.
                            // Örnek: predictions[0].getString("bytesBase64Encoded")
                            // VEYA  predictions[0].getJSONObject("image").getString("bytesBase64Encoded")
                            // Bu kısım modelinizin çıktısına göre ayarlanmalı.
                            // imagegeneration@006 için genellikle doğrudan string gelir.

                            val firstPrediction = predictionsArray.getJSONObject(0)
                            val generatedImageBase64: String?

                            // imagegeneration@006 modeli için çıktı yapısı genellikle şöyledir:
                            // { "predictions": [ { "bytesBase64Encoded": "...", "mimeType": "image/png" } ] }
                            // Ancak bazen "image" { "bytesBase64Encoded": "..." } yapısı da görülebilir.
                            // Esnek olmak adına ikisini de kontrol edelim.

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
                            val outputFile = File(
                                outputDir,
                                "processed_${System.currentTimeMillis()}.png" // PNG olarak kaydetmeyi deneyin, mimeType'a göre ayarlayabilirsiniz
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
                        // Hata durumu zaten yukarıdaki while döngüsünde loglandı (lastErrorBody ile).
                        // Burada sadece kullanıcıya genel bir mesaj gösterilebilir.
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Fotoğraf işlenemedi. Hata kodu: ${it.code}. Detaylar logda.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                null // Eğer response null ise veya başarılı değilse null dön
            } catch (e: Exception) {
                // Bu blok, try bloğunun en dışındaki genel istisnaları yakalar (örn: JSONException, IOException vb.)
                // client.newCall(request).execute() dışındaki istisnalar için.
                Log.e("CameraScreenStateHolder", "Vertex AI işleme sırasında genel bir hata oluştu: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Fotoğraf işlenemedi: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
                null
            }
        }
    }

    private suspend fun getAccessToken(context: Context): String {
        return withContext(Dispatchers.IO) {
            try {
                // Geliştirme ortamında servis hesabı anahtarı kullanmak yaygındır.
                // Üretim ortamında Workload Identity Federation veya benzeri daha güvenli yöntemler tercih edilmelidir.
                val credentials = GoogleCredentials.fromStream(
                    context.assets.open("vertex-ai-credentials.json") // Dosya adınızın doğru olduğundan emin olun
                ).createScoped("https://www.googleapis.com/auth/cloud-platform")
                credentials.refreshIfExpired()
                credentials.accessToken.tokenValue
            } catch (e: Exception) {
                Log.e("CameraScreenStateHolder", "OAuth 2.0 token alma hatası: ${e.message}", e)
                withContext(Dispatchers.Main) { // Hata mesajını kullanıcıya göstermek için ana thread'e geç
                    Toast.makeText(context, "Kimlik doğrulama anahtarı yüklenemedi. Ayarları kontrol edin.", Toast.LENGTH_LONG).show()
                }
                ""
            }
        }
    }

    // Basit bir çeviri fonksiyonu, daha karmaşık bir yapıya (örn: enum veya map) dönüştürülebilir.
    private fun translateInstruction(instruction: String): String {
        return when (instruction.trim().lowercase()) {
            "arka planı yarış pisti yap" -> "Edit the input image to replace its background with a race track scene"
            "arka planı kaldır" -> "Generate an image with the background removed based on the input image" // Bu inpainting/outpainting veya segmentation olabilir
            "arka planı plaj yap" -> "Edit the input image to replace its background with a beach scene"
            // Diğer özel talimatlar buraya eklenebilir
            else -> instruction // Eğer eşleşen yoksa orijinal talimatı kullan
        }
    }
}

// CameraScreenStateHolder.kt: Dosya sonu
