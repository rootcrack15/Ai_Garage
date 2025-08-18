// C:/Users/amilcakmak/AndroidStudioProjects/AIGarage/app/src/main/java/com/rootcrack/aigarage/screens/AutoMaskPreviewScreen.kt
package com.rootcrack.aigarage.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.Uri
import android.util.Log
import androidx.annotation.ColorInt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.rootcrack.aigarage.navigation.EditTypeValues
import com.rootcrack.aigarage.navigation.NavArgs
import com.rootcrack.aigarage.navigation.Screen
import com.rootcrack.aigarage.segmentation.DeepLabV3XceptionSegmenter
import com.rootcrack.aigarage.segmentation.SegmentationResult
import com.rootcrack.aigarage.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint

private const val TAG_AUTO_MASK_PREVIEW = "AutoMaskPreviewScreen"
private val MASK_OVERLAY_COLOR_PREVIEW = Color.Blue.copy(alpha = 0.4f)
private val BACKGROUND_MASK_OVERLAY_COLOR_PREVIEW = Color.Green.copy(alpha = 0.4f)


val CITYSCAPES_LABELS = arrayOf(
    "road", "sidewalk", "building", "wall", "fence", "pole", "traffic light",
    "traffic sign", "vegetation", "terrain", "sky", "person", "rider", "car",
    "truck", "bus", "train", "motorcycle", "bicycle"
)

const val DEFAULT_TARGET_CLASS_NAME = "car"

fun getCityscapesIndexForObject(className: String): Int {
    val index = CITYSCAPES_LABELS.indexOfFirst { it.equals(className.trim(), ignoreCase = true) }
    return if (index != -1) {
        Log.d(TAG_AUTO_MASK_PREVIEW, "Resolved '$className' to index $index.")
        index
    } else {
        val defaultIndex = CITYSCAPES_LABELS.indexOf(DEFAULT_TARGET_CLASS_NAME)
        Log.w(TAG_AUTO_MASK_PREVIEW, "Class name '$className' not found. Defaulting to '$DEFAULT_TARGET_CLASS_NAME' (index $defaultIndex).")
        defaultIndex
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoMaskPreviewScreen(
    navController: NavController,
    imageUriString: String,
    objectsToMaskCommaSeparated: String,
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var displayedOriginalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var foregroundMaskBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var backgroundMaskBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var displayBitmapWithForegroundMask by remember { mutableStateOf<Bitmap?>(null) }
    var displayBitmapWithBackgroundMask by remember { mutableStateOf<Bitmap?>(null) }

    var errorText by remember { mutableStateOf<String?>(null) }

    val targetObjectName = remember(objectsToMaskCommaSeparated) {
        objectsToMaskCommaSeparated.split(',').firstOrNull { it.isNotBlank() }?.trim() ?: DEFAULT_TARGET_CLASS_NAME
    }
    val targetClassIndexForSegmenter = remember(targetObjectName) {
        getCityscapesIndexForObject(targetObjectName)
    }

    val segmenter = remember {
        DeepLabV3XceptionSegmenter(context = context)
    }

    val imageUri = remember(imageUriString) { imageUriString.toUri() }

    DisposableEffect(Unit) {
        onDispose {
            Log.d(TAG_AUTO_MASK_PREVIEW, "Disposing AutoMaskPreviewScreen, closing segmenter.")
            segmenter.close()
            // Bitmap'leri de burada null'a çekip recycle etmek iyi bir pratik olabilir,
            // özellikle büyük görsellerle çalışıyorsanız ve hafıza sorunları yaşıyorsanız.
            // Ancak LaunchedEffect içinde zaten yeniden null'a çekiliyorlar.
            // originalBitmap?.recycle() // Örnek, gerekiyorsa aktif edin
            // foregroundMaskBitmap?.recycle()
            // backgroundMaskBitmap?.recycle()
        }
    }

    LaunchedEffect(imageUri, targetClassIndexForSegmenter) {
        isLoading = true
        errorText = null
        // Önceki bitmap'leri temizle
        originalBitmap = null
        displayedOriginalBitmap = null
        foregroundMaskBitmap = null
        backgroundMaskBitmap = null
        displayBitmapWithForegroundMask = null
        displayBitmapWithBackgroundMask = null

        Log.d(TAG_AUTO_MASK_PREVIEW, "Attempting to mask object: '$targetObjectName' (index: $targetClassIndexForSegmenter) from URI: $imageUri")

        val initSuccess = segmenter.initialize()
        if (!initSuccess) {
            errorText = "Otomatik maskeleme motoru başlatılamadı."
            isLoading = false
            return@LaunchedEffect
        }

        val loadedBitmap = withContext(Dispatchers.IO) { loadBitmapFromUri(context, imageUri) }
        if (loadedBitmap == null) {
            errorText = "Görsel yüklenemedi: $imageUriString"
            isLoading = false
            return@LaunchedEffect
        }
        originalBitmap = loadedBitmap
        displayedOriginalBitmap = loadedBitmap // Orijinali de gösterim için ayarla
        Log.d(TAG_AUTO_MASK_PREVIEW, "Original bitmap loaded: ${loadedBitmap.width}x${loadedBitmap.height}")

        val segmentationResult = withContext(Dispatchers.Default) {
            try {
                segmenter.segment(loadedBitmap, targetClassIndexForSegmenter)
            } catch (e: Exception) {
                Log.e(TAG_AUTO_MASK_PREVIEW, "Segmentation failed", e)
                SegmentationResult(maskBitmap = null, processingTimeMs = 0L, maskedPixelRatio = 0.0f, acceptedAutomatically = false)
            }
        }

        if (segmentationResult.maskBitmap != null) {
            val fgMask = segmentationResult.maskBitmap
            foregroundMaskBitmap = fgMask

            // Arka plan maskesini oluştur
            val bgMask = withContext(Dispatchers.IO) { ImageUtils.invertMaskBitmap(fgMask) }
            backgroundMaskBitmap = bgMask

            // Önizleme için overlay'leri oluştur
            displayBitmapWithForegroundMask = withContext(Dispatchers.Default) {
                overlayMaskOnBitmap(loadedBitmap, fgMask, MASK_OVERLAY_COLOR_PREVIEW.toArgb())
            }
            displayBitmapWithBackgroundMask = withContext(Dispatchers.Default) {
                overlayMaskOnBitmap(loadedBitmap, bgMask, BACKGROUND_MASK_OVERLAY_COLOR_PREVIEW.toArgb())
            }
            Log.d(TAG_AUTO_MASK_PREVIEW, "Foreground and background masks generated and overlaid for preview.")
        } else {
            errorText = "Maske oluşturulamadı. '$targetObjectName' nesnesi resimde bulunamadı veya segmentasyon başarısız oldu."
            Log.w(TAG_AUTO_MASK_PREVIEW, "Segmentation failed or object not found for '$targetObjectName'.")
        }
        isLoading = false
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Maske Seçenekleri: ${targetObjectName.replaceFirstChar { it.titlecase() }}") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                //  colors = TopAppBarDefaults.topAppBarColors(
                //                    containerColor = Color.Transparent,
                //                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                //                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,) // Yarı saydam örnek
                //            )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Maskeler oluşturuluyor...", style = MaterialTheme.typography.bodyLarge)
                }
            } else if (errorText != null) {
                Text(
                    text = errorText!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Orijinal Resim (isteğe bağlı, gerekirse)
                    displayedOriginalBitmap?.let {
                        Column(modifier = Modifier.weight(0.4f)) { // Bu Column'a bir ağırlık veriyoruz
                            Text(
                                "Orijinal Resim",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .align(Alignment.CenterHorizontally) // Başlığı ortala
                            )
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "Orijinal Resim",
                                modifier = Modifier
                                    .padding(horizontal = 45.dp, vertical = 75.dp) // Resmin etrafına boşluk ekle
                                    .fillMaxWidth()
                                    .weight(1f) // Bu weight, üstteki Column'un (0.4f ağırlıklı) içindeki alanı doldurur
                                    .aspectRatio(it.width.toFloat() / it.height.toFloat())
                                    .background(Color.LightGray),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(195.dp)) // Orijinal resim ile maskeler arasında boşluk


                    // Maskelenmiş Resimler Yan Yana
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Nesne Maskeli Önizleme
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Text(
                                text = "Nesne Maskesi",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            displayBitmapWithForegroundMask?.let { previewBitmap ->
                                Image(
                                    bitmap = previewBitmap.asImageBitmap(),
                                    contentDescription = "$targetObjectName maskeli",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(previewBitmap.width.toFloat() / previewBitmap.height.toFloat())
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = {
                                    foregroundMaskBitmap?.let { mask ->
                                        navigateToEditScreen(navController, imageUri, mask, targetObjectName, EditTypeValues.FOREGROUND, context)
                                    }
                                }) {
                                    Text("Bu Maskeyle Düzenle")
                                }
                            } ?: Box( // Placeholder if no bitmap
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.BrokenImage, "Maske Yok", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Arka Plan Maskeli Önizleme
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        ) {
                            Text(
                                text = "Arka Plan Maskesi",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            displayBitmapWithBackgroundMask?.let { previewBitmap ->
                                Image(
                                    bitmap = previewBitmap.asImageBitmap(),
                                    contentDescription = "Arka plan maskeli",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(previewBitmap.width.toFloat() / previewBitmap.height.toFloat())
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = {
                                    backgroundMaskBitmap?.let { mask ->
                                        navigateToEditScreen(navController, imageUri, mask, "background", EditTypeValues.BACKGROUND, context)
                                    }
                                }) {
                                    Text("Bu Maskeyle Düzenle")
                                }
                            } ?: Box( // Placeholder if no bitmap
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ){
                                Icon(Icons.Default.BrokenImage, "Maske Yok", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.weight(0.1f)) // Biraz boşluk bırakır en altta

                    // Maskesiz Düzenle butonu
                    Button(
                        onClick = {
                            navigateToEditScreen(navController, imageUri, null, "maskesiz", EditTypeValues.NONE, context)
                        },
                        modifier = Modifier.fillMaxWidth(0.8f) // Butonun genişliğini ayarla
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Düzenle")
                        Spacer(Modifier.size(8.dp)) // İkon ile yazı arasında boşluk
                        Text("Maskesiz Düzenle")
                    }
                }
            }
        }
    }
}

private fun navigateToEditScreen(
    navController: NavController,
    imageUri: Uri,
    maskBitmap: Bitmap?,
    instructionPrefix: String,
    editType: String,
    context: Context // Context artık burada gereksiz, ImageUtils'e taşınabilir veya ViewModel'den alınabilir
) {
    val encodedImageUri = Uri.encode(imageUri.toString())
    // Maskeyi Base64'e çevirme işlemi potansiyel olarak uzun sürebilir,
    // Dispatchers.Default veya Dispatchers.IO'da yapılabilir, ama navigate çağrısı ana thread'de olmalı.
    // Şimdilik burada bırakıyorum, performans sorunu olursa optimize edilebilir.
    val base64Mask = maskBitmap?.let { ImageUtils.convertBitmapToBase64(it) } ?: "null"
    val encodedMask = Uri.encode(base64Mask) // "null" stringi de encode edilecek, bu sorun değil.
    val initialInstruction = Uri.encode("$instructionPrefix ") // Kullanıcının devam etmesi için boşluk bırak

    val route = Screen.EditPhoto.route
        .replace("{${NavArgs.IMAGE_URI}}", encodedImageUri)
        .replace("{${NavArgs.INSTRUCTION}}", initialInstruction)
        .replace("{${NavArgs.MASK}}", encodedMask)
        .replace("{${NavArgs.EDIT_TYPE}}", editType)

    Log.d(TAG_AUTO_MASK_PREVIEW, "Navigating to EditScreen. Route: $route")
    navController.navigate(route)
}


private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        }
    } catch (e: FileNotFoundException) {
        Log.e(TAG_AUTO_MASK_PREVIEW, "File not found for URI: $uri", e)
        null
    } catch (e: IOException) {
        Log.e(TAG_AUTO_MASK_PREVIEW, "IOException when loading bitmap from URI: $uri", e)
        null
    } catch (e: SecurityException) {
        Log.e(TAG_AUTO_MASK_PREVIEW, "SecurityException: Permission denied for URI: $uri", e)
        null
    }
    catch (e: Exception) {
        Log.e(TAG_AUTO_MASK_PREVIEW, "Error loading bitmap from URI: $uri", e)
        null
    }
}

private fun overlayMaskOnBitmap(original: Bitmap, mask: Bitmap, @ColorInt color: Int): Bitmap {
    // Sonuç bitmap'ini orijinalin kopyası olarak oluştur, böylece orijinal bitmap değiştirilmez.
    val resultBitmap = original.copy(Bitmap.Config.ARGB_8888, true)
    // Maskeyi, orijinal bitmap'in boyutlarına ölçekle.
    // filter=false, maskeler için genellikle daha keskin sonuçlar verir.
    val scaledMask = mask.scale(original.width, original.height, filter = false)
    val canvas = AndroidCanvas(resultBitmap)
    val paint = AndroidPaint().apply {
        // PorterDuff.Mode.SRC_ATOP: Kaynak pikselleri (maske rengi), hedef piksellerin (orijinal resim) üzerine,
        // hedef piksellerin alfa değerlerini koruyarak çizer.
        // Yani maskenin olduğu yerler belirtilen renkle boyanır, olmadığı yerler orijinal kalır.
        colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP)
    }
    canvas.drawBitmap(scaledMask, 0f, 0f, paint)
    // scaledMask.recycle() // scaledMask geçici bir bitmap ise ve artık kullanılmayacaksa recycle edilebilir.
    // Ancak bu örnekte scaledMask'i başka bir yerde kullanmıyoruz, bu yüzden recycle etmek iyi bir pratik.
    // Eğer mask'in kendisi (fonksiyon parametresi olan) artık kullanılmayacaksa, çağrıldığı yerde recycle edilmeli.
    // Bu fonksiyon içinde recycle edilmemeli çünkü mask başka yerlerde de kullanılabilir.
    return resultBitmap
}
