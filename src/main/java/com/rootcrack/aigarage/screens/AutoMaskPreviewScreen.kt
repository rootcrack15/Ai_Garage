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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import kotlinx.coroutines.ensureActive
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
        index
    } else {
        CITYSCAPES_LABELS.indexOf(DEFAULT_TARGET_CLASS_NAME)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoMaskPreviewScreen(
    navController: NavController,
    imageUriString: String,
    objectsToMaskCommaSeparated: String,
    modelPath: String
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

    val imageUri = remember(imageUriString) { imageUriString.toUri() }

    // HATA DÜZELTMESİ: Segmenter'ın yaşam döngüsü tamamen LaunchedEffect içine alındı.
    LaunchedEffect(imageUri, targetClassIndexForSegmenter, modelPath) {
        isLoading = true
        errorText = null
        // Önceki bitmap'leri temizle
        originalBitmap = null; displayedOriginalBitmap = null; foregroundMaskBitmap = null
        backgroundMaskBitmap = null; displayBitmapWithForegroundMask = null; displayBitmapWithBackgroundMask = null

        var segmenter: DeepLabV3XceptionSegmenter? = null
        try {
            Log.d(TAG_AUTO_MASK_PREVIEW, "Yüklenen maskeleme modeli: $modelPath")
            // 1. Segmenter'ı coroutine içinde oluştur
            segmenter = DeepLabV3XceptionSegmenter(context = context, modelAssetPath = modelPath)
            
            val initSuccess = segmenter.initialize()
            ensureActive() // Coroutine iptal edildi mi kontrol et
            if (!initSuccess) {
                errorText = "Otomatik maskeleme motoru başlatılamadı."
                isLoading = false // Yüklemeyi durdur
                return@LaunchedEffect
            }

            val loadedBitmap = withContext(Dispatchers.IO) { loadBitmapFromUri(context, imageUri) }
            ensureActive()
            if (loadedBitmap == null) {
                errorText = "Görsel yüklenemedi: $imageUriString"
                isLoading = false
                return@LaunchedEffect
            }
            originalBitmap = loadedBitmap
            displayedOriginalBitmap = loadedBitmap

            // 2. Segmenter'ı kullan
            val segmentationResult = withContext(Dispatchers.Default) {
                ensureActive()
                segmenter.segment(loadedBitmap, targetClassIndexForSegmenter)
            }
            
            ensureActive()
            if (segmentationResult.maskBitmap != null) {
                val fgMask = segmentationResult.maskBitmap
                foregroundMaskBitmap = fgMask
                val bgMask = withContext(Dispatchers.IO) { ImageUtils.invertMaskBitmap(fgMask) }
                backgroundMaskBitmap = bgMask
                displayBitmapWithForegroundMask = withContext(Dispatchers.Default) { overlayMaskOnBitmap(loadedBitmap, fgMask, MASK_OVERLAY_COLOR_PREVIEW.toArgb()) }
                displayBitmapWithBackgroundMask = withContext(Dispatchers.Default) { overlayMaskOnBitmap(loadedBitmap, bgMask, BACKGROUND_MASK_OVERLAY_COLOR_PREVIEW.toArgb()) }
            } else {
                errorText = "Maske oluşturulamadı. '$targetObjectName' nesnesi resimde bulunamadı."
            }
        } catch (e: Exception) {
            Log.e(TAG_AUTO_MASK_PREVIEW, "Maskeleme sırasında hata", e)
            errorText = "Maskeleme sırasında bir hata oluştu."
        } finally {
            // 3. Segmenter'ı her durumda (başarılı, hatalı, iptal) finally bloğunda kapat
            Log.d(TAG_AUTO_MASK_PREVIEW, "Segmenter kapatılıyor.")
            segmenter?.close()
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Maske Seçenekleri: ${targetObjectName.replaceFirstChar { it.titlecase() }}") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues).background(MaterialTheme.colorScheme.background),
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
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MaskPreviewCard(
                            title = "Nesne Maskesi",
                            previewBitmap = displayBitmapWithForegroundMask,
                            onClick = {
                                foregroundMaskBitmap?.let { mask ->
                                    navigateToEditScreen(navController, imageUri, mask, targetObjectName, EditTypeValues.FOREGROUND)
                                }
                            }
                        )
                        MaskPreviewCard(
                            title = "Arka Plan Maskesi",
                            previewBitmap = displayBitmapWithBackgroundMask,
                            onClick = {
                                backgroundMaskBitmap?.let { mask ->
                                    navigateToEditScreen(navController, imageUri, mask, "background", EditTypeValues.BACKGROUND)
                                }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            navigateToEditScreen(navController, imageUri, null, "maskesiz", EditTypeValues.NONE)
                        },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Maskesiz Düzenle")
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.MaskPreviewCard(title: String, previewBitmap: Bitmap?, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.weight(1f)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (previewBitmap != null) {
                Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = "$title önizlemesi",
                    contentScale = ContentScale.Fit
                )
            } else {
                Icon(Icons.Default.BrokenImage, "Maske Yok", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onClick, enabled = previewBitmap != null) {
            Text("Bunu Seç")
        }
    }
}

private fun navigateToEditScreen(
    navController: NavController,
    imageUri: Uri,
    maskBitmap: Bitmap?,
    instructionPrefix: String,
    editType: String
) {
    val encodedImageUri = Uri.encode(imageUri.toString())
    val base64Mask = maskBitmap?.let { ImageUtils.convertBitmapToBase64(it) } ?: "null"
    val encodedMask = Uri.encode(base64Mask)
    val initialInstruction = Uri.encode(if (instructionPrefix != "maskesiz") "$instructionPrefix " else "")

    val route = Screen.EditPhoto.route
        .replace("{${NavArgs.IMAGE_URI}}", encodedImageUri)
        .replace("{${NavArgs.INSTRUCTION}}", initialInstruction)
        .replace("{${NavArgs.MASK}}", encodedMask)
        .replace("{${NavArgs.EDIT_TYPE}}", editType)
    navController.navigate(route)
}

private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        }
    } catch (e: Exception) {
        Log.e(TAG_AUTO_MASK_PREVIEW, "Bitmap yüklenirken hata: $uri", e)
        null
    }
}

private fun overlayMaskOnBitmap(original: Bitmap, mask: Bitmap, @ColorInt color: Int): Bitmap {
    val resultBitmap = original.copy(Bitmap.Config.ARGB_8888, true)
    val scaledMask = mask.scale(original.width, original.height, filter = false)
    val canvas = AndroidCanvas(resultBitmap)
    val paint = AndroidPaint().apply {
        colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP)
    }
    canvas.drawBitmap(scaledMask, 0f, 0f, paint)
    scaledMask.recycle()
    return resultBitmap
}
