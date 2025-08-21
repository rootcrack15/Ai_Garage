package com.rootcrack.aigarage.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.Uri
import android.util.Log
import androidx.annotation.ColorInt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.rootcrack.aigarage.navigation.EditTypeValues
import com.rootcrack.aigarage.navigation.NavArgs
import com.rootcrack.aigarage.navigation.Screen
import com.rootcrack.aigarage.segmentation.DeepLabV3XceptionSegmenter
import com.rootcrack.aigarage.segmentation.SegmentationResult
import com.rootcrack.aigarage.segmentation.MultiObjectSegmentationResult
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
    var showFullScreenDialog by remember { mutableStateOf<Bitmap?>(null) }

    // Açıklama: Çoklu nesne seçimi için tüm nesneleri işle
    val targetObjectNames = remember(objectsToMaskCommaSeparated) {
        objectsToMaskCommaSeparated.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(DEFAULT_TARGET_CLASS_NAME) }
    }
    
    val targetClassIndices = remember(targetObjectNames) {
        targetObjectNames.map { getCityscapesIndexForObject(it) }
    }

    val imageUri = remember(imageUriString) { imageUriString.toUri() }

    // Açıklama: Fade-in animasyonu için state
    var showContent by remember { mutableStateOf(false) }

    // HATA DÜZELTMESİ: Segmenter'ın yaşam döngüsü tamamen LaunchedEffect içine alındı.
    LaunchedEffect(imageUri, targetClassIndices, modelPath) {
        isLoading = true
        errorText = null
        showContent = false
        // Önceki bitmap'leri temizle
        originalBitmap = null; displayedOriginalBitmap = null; foregroundMaskBitmap = null
        backgroundMaskBitmap = null; displayBitmapWithForegroundMask = null; displayBitmapWithBackgroundMask = null

        var segmenter: DeepLabV3XceptionSegmenter? = null
        try {
            Log.d(TAG_AUTO_MASK_PREVIEW, "Yüklenen maskeleme modeli: $modelPath")
            Log.d(TAG_AUTO_MASK_PREVIEW, "Hedef nesneler: $targetObjectNames")
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

            // Açıklama: Çoklu nesne segmentasyonu yap
            val multiSegmentationResult = withContext(Dispatchers.Default) {
                ensureActive()
                segmenter.segmentMultipleObjects(loadedBitmap, targetClassIndices)
            }

            ensureActive()
            if (multiSegmentationResult.combinedMaskBitmap != null) {
                val fgMask = multiSegmentationResult.combinedMaskBitmap
                foregroundMaskBitmap = fgMask
                val bgMask = withContext(Dispatchers.IO) { ImageUtils.invertMaskBitmap(fgMask) }
                backgroundMaskBitmap = bgMask
                displayBitmapWithForegroundMask = withContext(Dispatchers.Default) { overlayMaskOnBitmap(loadedBitmap, fgMask, MASK_OVERLAY_COLOR_PREVIEW.toArgb()) }
                displayBitmapWithBackgroundMask = withContext(Dispatchers.Default) { overlayMaskOnBitmap(loadedBitmap, bgMask, BACKGROUND_MASK_OVERLAY_COLOR_PREVIEW.toArgb()) }
                
                // Açıklama: Başarılı segmentasyonları logla
                val successfulObjects = multiSegmentationResult.individualResults
                    .filter { it.value.maskBitmap != null }
                    .keys.joinToString(", ")
                Log.d(TAG_AUTO_MASK_PREVIEW, "Başarılı segmentasyonlar: $successfulObjects")
            } else {
                val objectNames = targetObjectNames.joinToString(", ")
                errorText = "Maske oluşturulamadı. '$objectNames' nesneleri resimde bulunamadı."
            }
        } catch (e: Exception) {
            Log.e(TAG_AUTO_MASK_PREVIEW, "Maskeleme sırasında hata", e)
            errorText = "Maskeleme sırasında bir hata oluştu."
        } finally {
            // 3. Segmenter'ı her durumda (başarılı, hatalı, iptal) finally bloğunda kapat
            Log.d(TAG_AUTO_MASK_PREVIEW, "Segmenter kapatılıyor.")
            segmenter?.close()
            isLoading = false
            // Açıklama: İçeriği göster ve animasyonu başlat
            showContent = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    val objectNames = targetObjectNames.joinToString(", ") { it.replaceFirstChar { char -> char.titlecase() } }
                    Text(
                        text = "Maske Önizleme",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isLoading) {
                LoadingScreen()
            } else if (errorText != null) {
                ErrorScreen(errorText = errorText!!)
            } else {
                AnimatedVisibility(
                    visible = showContent,
                    enter = fadeIn(animationSpec = tween(500)) + slideInVertically(
                        animationSpec = tween(500),
                        initialOffsetY = { it / 4 }
                    )
                ) {
                    MainContent(
                        originalBitmap = displayedOriginalBitmap,
                        foregroundMaskBitmap = displayBitmapWithForegroundMask,
                        backgroundMaskBitmap = displayBitmapWithBackgroundMask,
                        onForegroundClick = {
                            foregroundMaskBitmap?.let { mask ->
                                val objectNames = targetObjectNames.joinToString(",")
                                navigateToEditScreen(navController, imageUri, mask, objectNames, EditTypeValues.FOREGROUND)
                            }
                        },
                        onBackgroundClick = {
                            backgroundMaskBitmap?.let { mask ->
                                navigateToEditScreen(navController, imageUri, mask, "background", EditTypeValues.BACKGROUND)
                            }
                        },
                        onNoMaskClick = {
                            navigateToEditScreen(navController, imageUri, null, "maskesiz", EditTypeValues.NONE)
                        },
                        onImageClick = { bitmap ->
                            showFullScreenDialog = bitmap
                        }
                    )
                }
            }
        }
    }

    // Açıklama: Tam ekran önizleme dialog'u
    showFullScreenDialog?.let { bitmap ->
        FullScreenImageDialog(
            bitmap = bitmap,
            onDismiss = { showFullScreenDialog = null }
        )
    }
}

@Composable
private fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Maskeler oluşturuluyor...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun ErrorScreen(errorText: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.BrokenImage,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = errorText,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun MainContent(
    originalBitmap: Bitmap?,
    foregroundMaskBitmap: Bitmap?,
    backgroundMaskBitmap: Bitmap?,
    onForegroundClick: () -> Unit,
    onBackgroundClick: () -> Unit,
    onNoMaskClick: () -> Unit,
    onImageClick: (Bitmap) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Açıklama: Orijinal resim
        ImageSection(
            title = "Orijinal Resim",
            bitmap = originalBitmap,
            onClick = { originalBitmap?.let { onImageClick(it) } }
        )

        // Açıklama: Nesne maskesi
        ImageSection(
            title = "Nesne Maskesi",
            bitmap = foregroundMaskBitmap,
            onClick = { foregroundMaskBitmap?.let { onImageClick(it) } },
            actionButton = {
                Button(
                    onClick = onForegroundClick,
                    enabled = foregroundMaskBitmap != null,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Bu Maskeyi Kullan")
                }
            }
        )

        // Açıklama: Arka plan maskesi
        ImageSection(
            title = "Arka Plan Maskesi",
            bitmap = backgroundMaskBitmap,
            onClick = { backgroundMaskBitmap?.let { onImageClick(it) } },
            actionButton = {
                Button(
                    onClick = onBackgroundClick,
                    enabled = backgroundMaskBitmap != null,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Bu Maskeyi Kullan")
                }
            }
        )

        // Açıklama: Maskesiz düzenleme butonu
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(16.dp),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Button(
                onClick = onNoMaskClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Maskesiz Düzenle")
            }
        }
    }
}

@Composable
private fun ImageSection(
    title: String,
    bitmap: Bitmap?,
    onClick: () -> Unit,
    actionButton: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Açıklama: Başlık
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Açıklama: Resim
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "$title önizlemesi",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // Açıklama: Tam ekran butonu
                    IconButton(
                        onClick = onClick,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(8.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Tam ekran görüntüle",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    Icon(
                        Icons.Default.BrokenImage,
                        contentDescription = "Resim yok",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            // Açıklama: Aksiyon butonu
            actionButton?.invoke()
        }
    }
}

@Composable
private fun FullScreenImageDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f))
                .clickable { onDismiss() }
        ) {
            // Açıklama: Kapatma butonu
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Kapat",
                    tint = Color.White
                )
            }

            // Açıklama: Resim
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Tam ekran önizleme",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
            )
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
