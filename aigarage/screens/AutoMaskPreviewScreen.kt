// AutoMaskPreviewScreen.kt
package com.rootcrack.aigarage.screens

// ... (diğer importlar)
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PorterDuff
import android.net.Uri
import android.util.Base64 // bitmapToBase64 için
import android.util.Log
import androidx.compose.animation.core.copy
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color // Compose Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rootcrack.aigarage.navigation.NavArgs
import com.rootcrack.aigarage.navigation.Screen
import com.rootcrack.aigarage.segmentation.DeepLabV3XceptionSegmenter
import com.rootcrack.aigarage.segmentation.SegmentationResult // Data class'ı import et
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException


private const val TAG_AUTO_MASK_PREVIEW = "AutoMaskPreviewScreen"
// private const val DEFAULT_OBJECT_TO_MASK = "person" // Galeri'den gelirken kullanılacak
private const val MODEL_ASSET_NAME = "main.tflite" // Model dosyasının adı
private val MASK_OVERLAY_COLOR_PREVIEW = Color.Blue.copy(alpha = 0.4f)


// Nesne adını sınıf indeksine çeviren map (Navigations.kt'deki private fonksiyon yerine)
// Bu haritanın modelinizin label'larıyla tam olarak eşleştiğinden emin olun.
// Örnek: "person" -> 0, "car" -> 1 vb. Modelinizin çıktı sınıflarına göre güncelleyin.
val objectLabelToClassIndexMap: Map<String, Int> = mapOf(
    "person" to 0, "people" to 0,
    "bicycle" to 1,
    "car" to 2, "automobile" to 2, "vehicle" to 2,
    "motorcycle" to 3, "motorbike" to 3,
    "airplane" to 4,
    "bus" to 5,
    "train" to 6,
    "truck" to 7,
    "boat" to 8,
    "traffic light" to 9,
    "fire hydrant" to 10,
    "stop sign" to 11,
    "parking meter" to 12,
    "bench" to 13,
    "bird" to 14,
    "cat" to 15,
    "dog" to 16,
    "horse" to 17,
    "sheep" to 18,
    "cow" to 19,
    "elephant" to 20,
    "bear" to 21,
    "zebra" to 22,
    "giraffe" to 23,
    "backpack" to 24,
    "umbrella" to 25,
    "handbag" to 26,
    "tie" to 27,
    "suitcase" to 28,
    "frisbee" to 29,
    "skis" to 30,
    "snowboard" to 31,
    "sports ball" to 32,
    "kite" to 33,
    "baseball bat" to 34,
    "baseball glove" to 35,
    "skateboard" to 36,
    "surfboard" to 37,
    "tennis racket" to 38,
    "bottle" to 39,
    "wine glass" to 40,
    "cup" to 41,
    "fork" to 42,
    "knife" to 43,
    "spoon" to 44,
    "bowl" to 45,
    "banana" to 46,
    "apple" to 47,
    "sandwich" to 48,
    "orange" to 49,
    "broccoli" to 50,
    "carrot" to 51,
    "hot dog" to 52,
    "pizza" to 53,
    "donut" to 54,
    "cake" to 55,
    "chair" to 56, "couch" to 57, "potted plant" to 58, "bed" to 59,
    "dining table" to 60, "toilet" to 61, "tv" to 62, "laptop" to 63,
    "mouse" to 64, "remote" to 65, "keyboard" to 66, "cell phone" to 67,
    "microwave" to 68, "oven" to 69, "toaster" to 70, "sink" to 71,
    "refrigerator" to 72, "book" to 73, "clock" to 74, "vase" to 75,
    "scissors" to 76, "teddy bear" to 77, "hair drier" to 78, "toothbrush" to 79,
    // Ekstra etiketler (modelinize göre)
    "road" to 80, // Örnek, modelinizde varsa
    "building" to 81, // Örnek
    "sky" to 82 // Örnek
    // Diğer etiketleri modelinizin labelmap.txt veya benzeri bir dosyadan ekleyin.
    // Varsayılan olarak bilinmeyen bir nesne için 0 (genellikle "person" veya "background") kullanılabilir.
).withDefault { 0 } // Bilinmeyen etiketler için varsayılan indeks

fun getTargetClassIndexForObject(objectName: String): Int {
    return objectLabelToClassIndexMap[objectName.lowercase().trim()] ?: 0 // Varsayılan olarak ilk sınıfı kullan
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoMaskPreviewScreen(
    navController: NavController,
    imageUriString: String, // Navigasyondan gelen String URI
    objectsToMaskCommaSeparated: String, // "person,car" gibi
    // cameraScreenState: CameraScreenStateHolder // Eğer AI işlemleri için gerekliyse
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var generatedMaskBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var displayBitmapWithMask by remember { mutableStateOf<Bitmap?>(null) } // Önizleme için maske uygulanmış bitmap
    var errorText by remember { mutableStateOf<String?>(null) }
    var segmenter by remember { mutableStateOf<DeepLabV3XceptionSegmenter?>(null) }
    var segmentationResultState by remember { mutableStateOf<SegmentationResult?>(null) }
    var base64MaskForEditing by remember { mutableStateOf<String?>(null) }

    val imageUri = remember(imageUriString) { Uri.parse(imageUriString) }

    DisposableEffect(Unit) {
        onDispose {
            Log.d(TAG_AUTO_MASK_PREVIEW, "Disposing AutoMaskPreviewScreen, closing segmenter.")
            segmenter?.close()
        }
    }

    LaunchedEffect(imageUri, objectsToMaskCommaSeparated) {
        isLoading = true
        errorText = null
        originalBitmap = null
        generatedMaskBitmap = null
        displayBitmapWithMask = null
        segmentationResultState = null
        base64MaskForEditing = null

        Log.d(TAG_AUTO_MASK_PREVIEW, "Loading image: $imageUri, objects to mask: $objectsToMaskCommaSeparated")

        // 1. Orijinal Bitmap'i Yükle
        val loadedBitmap = withContext(Dispatchers.IO) {
            loadBitmapFromUri(context, imageUri)
        }
        if (loadedBitmap == null) {
            errorText = "Görsel yüklenemedi."
            isLoading = false
            Log.e(TAG_AUTO_MASK_PREVIEW, "Bitmap could not be loaded from URI: $imageUri")
            return@LaunchedEffect
        }
        originalBitmap = loadedBitmap
        Log.d(TAG_AUTO_MASK_PREVIEW, "Original bitmap loaded: ${loadedBitmap.width}x${loadedBitmap.height}")


        // Şimdilik ilk nesneyi alıyoruz. İleride çoklu nesne desteği eklenebilir.
        val firstObjectToMask = objectsToMaskCommaSeparated.split(",").firstOrNull()?.trim() ?: "person"
        val targetClassIndex = getTargetClassIndexForObject(firstObjectToMask)
        Log.d(TAG_AUTO_MASK_PREVIEW, "Target object: '$firstObjectToMask', Class index: $targetClassIndex")


        // 2. Segmenter'ı oluştur ve başlat
        val newSegmenter = DeepLabV3XceptionSegmenter(
            context = context,
            modelAssetPath = MODEL_ASSET_NAME,
            targetClassIndex = targetClassIndex,
            acceptThreshold = 0.5f // !!! DİKKAT: DeepLabV3XceptionSegmenter'daki parametre adı 'acceptThreshold'
            // 'confidenceThreshold' DEĞİL!
        )
        segmenter = newSegmenter // for onDispose

        val initSuccess = withContext(Dispatchers.IO) { newSegmenter.initialize() }
        if (!initSuccess) {
            errorText = "Segmentasyon modeli başlatılamadı."
            isLoading = false
            Log.e(TAG_AUTO_MASK_PREVIEW, "Segmenter initialization failed.")
            return@LaunchedEffect
        }
        Log.d(TAG_AUTO_MASK_PREVIEW, "Segmenter initialized successfully.")

        // 3. Segmentasyonu Çalıştır
        Log.d(TAG_AUTO_MASK_PREVIEW, "Starting segmentation...")
        val result = withContext(Dispatchers.Default) { // CPU yoğun işlem için Default dispatcher
            newSegmenter.segment(loadedBitmap)
        }
        segmentationResultState = result
        Log.d(TAG_AUTO_MASK_PREVIEW, "Segmentation result: Mean Confidence=${result.meanConfidence}, Accepted=${result.acceptedAutomatically}, Mask Null=${result.maskBitmap == null}")


        if (result.maskBitmap != null) {
            generatedMaskBitmap = result.maskBitmap
            base64MaskForEditing = bitmapToBase64(generatedMaskBitmap) // Düzenleme için base64 maske

            // Önizleme için orijinal resme maskeyi uygula
            displayBitmapWithMask = originalBitmap?.let { ob ->
                generatedMaskBitmap?.let { mask ->
                    overlayMaskOnBitmap(ob, mask, MASK_OVERLAY_COLOR_PREVIEW)
                }
            }
            Log.d(TAG_AUTO_MASK_PREVIEW, "Mask generated and applied for preview. Base64 mask created.")
        } else {
            errorText = "Maske oluşturulamadı. Ortalama güven: ${result.meanConfidence}"
            Log.w(TAG_AUTO_MASK_PREVIEW, "Mask bitmap is null. Mean confidence: ${result.meanConfidence}")
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Otomatik Maske Önizleme") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            if (!isLoading && (displayBitmapWithMask != null || originalBitmap != null)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Button(onClick = {
                        // Düzenleme ekranına git (maske ile veya maskesiz)
                        val route = Screen.EditPhoto.route
                            .replace("{${NavArgs.IMAGE_URI}}", Uri.encode(imageUri.toString()))
                            .replace("{${NavArgs.INSTRUCTION}}", Uri.encode("")) // Başlangıç talimatı boş
                            .replace("{${NavArgs.MASK}}", Uri.encode(base64MaskForEditing ?: "")) // Opsiyonel maske

                        Log.d(TAG_AUTO_MASK_PREVIEW, "Navigating to EditPhoto: $route")
                        navController.navigate(route) {
                            popUpTo(Screen.Gallery.route) // Galeriye kadar olanları temizle
                        }
                    }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Düzenle")
                        Spacer(Modifier.width(8.dp))
                        Text(if (base64MaskForEditing != null) "Maske ile Düzenle" else "Düzenle")
                    }

                    // Eğer maske otomatik olarak kabul edildiyse veya kullanıcı bir şekilde onaylarsa:
                    if (segmentationResultState?.acceptedAutomatically == true && base64MaskForEditing != null) {
                        Button(onClick = {
                            // Burada doğrudan kaydetme veya başka bir işlem yapılabilir.
                            // Şimdilik sadece düzenlemeye yönlendiriyoruz.
                            val route = Screen.EditPhoto.route
                                .replace("{${NavArgs.IMAGE_URI}}", Uri.encode(imageUri.toString()))
                                .replace("{${NavArgs.INSTRUCTION}}", Uri.encode(""))
                                .replace("{${NavArgs.MASK}}", Uri.encode(base64MaskForEditing!!)) // Maske var
                            Log.d(TAG_AUTO_MASK_PREVIEW, "Navigating to EditPhoto with accepted mask: $route")
                            navController.navigate(route) {
                                popUpTo(Screen.Gallery.route)
                            }

                        }) {
                            Icon(Icons.Filled.Check, contentDescription = "Onayla ve Devam Et")
                            Spacer(Modifier.width(8.dp))
                            Text("Onayla")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> CircularProgressIndicator()
                displayBitmapWithMask != null -> {
                    Image(
                        bitmap = displayBitmapWithMask!!.asImageBitmap(),
                        contentDescription = "Maskelenmiş Görsel Önizlemesi",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                originalBitmap != null -> { // Maske yoksa orijinali göster
                    Image(
                        bitmap = originalBitmap!!.asImageBitmap(),
                        contentDescription = "Orijinal Görsel",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    if (errorText != null) { // Hata varsa orijinalin üstünde göster
                        Box(modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center){
                            Text(text = errorText!!, color = Color.White, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(
                                16.dp
                            ))
                        }
                    }
                }
                errorText != null -> Text(text = errorText!!, color = Color.White, style = MaterialTheme.typography.headlineSmall)
                else -> Text("Görsel yükleniyor veya bulunamadı.", color = Color.White)
            }
        }
    }
}

// Bitmap'i Uri'den yükleyen yardımcı fonksiyon (IO thread'inde çalıştırılmalı)
private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        }
    } catch (e: Exception) {
        Log.e(TAG_AUTO_MASK_PREVIEW, "Error loading bitmap from URI: $uri", e)
        null
    }
}

// Orijinal bitmap üzerine maskeyi renkli bir katman olarak uygular (önizleme için)
private fun overlayMaskOnBitmap(original: Bitmap, mask: Bitmap, overlayColor: Color): Bitmap {
    if (original.isRecycled || mask.isRecycled) {
        Log.e(TAG_AUTO_MASK_PREVIEW, "Original or mask bitmap is recycled.")
        return original // veya bir hata durumu yönetimi
    }
    val resultBitmap = original.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(resultBitmap)
    val paint = Paint().apply {
        color = overlayColor.toArgb() // Android Color int
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP) // Maskenin olduğu yere rengi uygula
    }

    // Maskeyi orijinal boyutuna ölçekle (gerekirse)
    val scaledMask = if (mask.width != original.width || mask.height != original.height) {
        Bitmap.createScaledBitmap(mask, original.width, original.height, true)
    } else {
        mask
    }

    // Maskeyi (beyaz kısımlarını) kullanarak boyama yap
    // Önce maskeden bir alfa kanalı oluşturalım (beyaz = opak, siyah = transparan)
    val alphaMaskPaint = Paint().apply {
        alpha = (overlayColor.alpha * 255).toInt() // Compose Color'dan alpha al
    }
    // canvas.drawBitmap(scaledMask, 0f, 0f, alphaMaskPaint) // Bu direkt maskeyi çizer

    // Daha iyi bir yaklaşım: Maskenin beyaz olduğu yerlere overlayColor ile boya
    val maskPaint = Paint().apply {
        colorFilter = PorterDuffColorFilter(overlayColor.toArgb(), PorterDuff.Mode.SRC_IN)
    }
    canvas.drawBitmap(scaledMask, 0f, 0f, maskPaint)


    return resultBitmap
}


// Bitmap'i Base64 string'e çevirir (PNG formatında)
fun bitmapToBase64(bitmap: Bitmap?): String? {
    if (bitmap == null || bitmap.isRecycled) {
        Log.e(TAG_AUTO_MASK_PREVIEW, "bitmapToBase64: Bitmap null veya zaten recycle edilmiş.")
        return null
    }
    return try {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream) // PNG kayıpsız ve alfa kanalını destekler
        val byteArray = byteArrayOutputStream.toByteArray()
        Base64.encodeToString(byteArray, Base64.NO_WRAP) // NO_WRAP önemli
    } catch (e: Exception) {
        Log.e(TAG_AUTO_MASK_PREVIEW, "Error converting bitmap to Base64", e)
        null
    }
}
