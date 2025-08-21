// Dosya: EditPhotoScreenWithPrompt.kt
package com.rootcrack.aigarage.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.AndroidPath
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.rootcrack.aigarage.R
import com.rootcrack.aigarage.navigation.EditTypeValues
import com.rootcrack.aigarage.navigation.NavArgs
import com.rootcrack.aigarage.navigation.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Orijinal dosyanızdaki PathData, DrawingTool vs. burada da tanımlı olmalı,
// eğer bu ekran HEM OTOMATİK MASKELİ HEM MANUEL ÇİZİMLİ olacaksa.
// Şimdiki senaryoda sadece otomatik maske ve talimatla geldiği için
// manuel çizim kısımları basitleştirildi/kaldırıldı.

// typealias AndroidPath = android.graphics.Path
// typealias AndroidPaint = android.graphics.Paint
// typealias AndroidCanvas = android.graphics.Canvas

enum class DrawingTool { BRUSH, ERASER }
data class PathData(
    val androidPath: AndroidPath = AndroidPath(),
    var color: Color = Color.White,
    var strokeWidth: Float = 20f,
    val tool: DrawingTool = DrawingTool.BRUSH,
    var alpha: Float = 1.0f,
)

private const val TAG_EDIT_PHOTO_WITH_PROMPT = "EditPhotoScreenWithPrompt"

// Vertex AI için maske renkleri (generateMaskBase64 içinde kullanılacak)
// val MASK_DRAW_COLOR_ANDROID = android.graphics.Color.WHITE
// val MASK_BACKGROUND_COLOR_ANDROID = android.graphics.Color.BLACK

@SuppressLint("UnusedBoxWithConstraintsScope", "NewApi")
@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPhotoScreenWithPrompt(
    navController: NavController,
    imageUri: String, // String olarak alıp Uri.parse() ile kullanacağız
    initialInstruction: String,
    base64Mask: String?, // Otomatik maske (veya manuel çizimden gelen)
    editType: String,    // YENİ: "foreground", "background", veya "none"
    onProcessPhoto: (originalImageUri: String, maskBase64: String?, instruction: String) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current.density

    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var scaledBitmapToDisplay by remember { mutableStateOf<ImageBitmap?>(null) }
    var bitmapDisplaySize by remember { mutableStateOf(IntSize.Zero) }
    var maskBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // --- MANUEL ÇİZİM İÇİN STATE'LER (EĞER KULLANILACAKSA) ---
    // Bu kısım, eğer bu ekranda AYRICA manuel çizim yapılacaksa gereklidir.
    // AutoMaskPreview'dan geliyorsa ve burada ek çizim YOKSA, bu state'ler gereksiz olabilir
    // veya basitleştirilebilir. Şimdilik orijinaldeki gibi bırakıyorum,
    // ancak `paths`'ın nasıl ele alınacağı önemli.
    val paths = remember { mutableStateListOf<com.rootcrack.aigarage.screens.PathData>() } // Kendi PathData'nızı kullanın
    val undonePaths = remember { mutableStateListOf<com.rootcrack.aigarage.screens.PathData>() }

    var currentBrushColor by remember { mutableStateOf(Color.Yellow) }
    var currentAlpha by remember { mutableStateOf(0.5f) }
    var selectedTool by remember { mutableStateOf(com.rootcrack.aigarage.screens.DrawingTool.BRUSH) } // Kendi DrawingTool'unuz
    var brushStrokeWidth by rememberSaveable { mutableStateOf(30f) }
    var showBrushSizeSlider by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }

    var canvasWidthPx by remember { mutableStateOf(0) }
    var canvasHeightPx by remember { mutableStateOf(0) }

    var displayToOriginalScaleX by remember { mutableStateOf(1f) }
    var displayToOriginalScaleY by remember { mutableStateOf(1f) }
    var canvasOffsetInBox by remember { mutableStateOf(Offset.Zero) }
    // --- MANUEL ÇİZİM STATE'LERİ SONU ---

    var isLoading by remember { mutableStateOf(true) } // Genel yükleme + resim yükleme
    var errorLoadingImage by remember { mutableStateOf<String?>(null) }
    var showMaskInfoDialog by remember { mutableStateOf(false) }
    var showFullScreenDialog by remember { mutableStateOf<Bitmap?>(null) }
    var showContent by remember { mutableStateOf(false) }

    // editType'a göre başlık ve talimat placeholder'ı
    val screenTitle = when (editType) {
        EditTypeValues.FOREGROUND -> stringResource(R.string.edit_foreground_title, initialInstruction.ifEmpty { stringResource(R.string.foreground_default) })
        EditTypeValues.BACKGROUND -> stringResource(R.string.edit_background_title, initialInstruction.ifEmpty { stringResource(R.string.background_default) })
        else -> stringResource(R.string.edit_general_title) // editType = NONE veya bilinmeyen bir değer
    }

    // `initialInstruction` doluysa onu kullan, yoksa editType'a göre varsayılanı kullan
    val actualInitialInstruction = if (initialInstruction.isNotBlank() && initialInstruction != "null") {
        initialInstruction
    } else {
        when (editType) {
            EditTypeValues.FOREGROUND -> "" // Veya "Seçili nesneyi..."
            EditTypeValues.BACKGROUND -> "Replace background with a breathtaking cinematic vista of towering snow-capped mountains glowing under a golden sunset, their peaks piercing through low-hanging clouds, with shimmering light reflecting off a crystal-clear lake below." // Veya "Arka planı..."
            else -> ""
        }
    }

    var instructionText by rememberSaveable(actualInitialInstruction, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(actualInitialInstruction))
    }

    val defaultPromptPlaceholder = when (editType) {
        EditTypeValues.FOREGROUND -> stringResource(R.string.prompt_placeholder_foreground)
        EditTypeValues.BACKGROUND -> stringResource(R.string.prompt_placeholder_background)
        else -> stringResource(R.string.prompt_placeholder_general)
    }

    val maskInfoText = if (base64Mask != null && base64Mask != "null" && base64Mask.isNotEmpty()) {
        when (editType) {
            EditTypeValues.FOREGROUND -> stringResource(R.string.mask_info_foreground)
            EditTypeValues.BACKGROUND -> stringResource(R.string.mask_info_background)
            else -> stringResource(R.string.mask_info_general) // Genel durum
        }
    } else {
        null // Maske yoksa bilgi gösterme veya "Maske yok, tüm resim etkilenecek."
    }

    LaunchedEffect(imageUri) {
        isLoading = true // Resim yüklenirken genel yükleme aktif
        errorLoadingImage = null
        showContent = false
        Log.d(TAG_EDIT_PHOTO_WITH_PROMPT, "Resim yükleniyor: $imageUri, EditType: $editType, Maske var mı: ${base64Mask != null && base64Mask != "null"}")
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(imageUri)
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val tempBitmap = BitmapFactory.decodeStream(inputStream)
                    if (tempBitmap == null) {
                        throw Exception("BitmapFactory.decodeStream null döndü.")
                    }
                    originalBitmap = tempBitmap
                    Log.d(TAG_EDIT_PHOTO_WITH_PROMPT, "Orijinal resim yüklendi: ${originalBitmap?.width}x${originalBitmap?.height}")
                } ?: throw Exception("Content resolver null input stream döndü.")

                // Açıklama: Maske bitmap'ini yükle
                if (base64Mask != null && base64Mask != "null" && base64Mask.isNotEmpty()) {
                    try {
                        val maskBytes = android.util.Base64.decode(base64Mask, android.util.Base64.DEFAULT)
                        maskBitmap = BitmapFactory.decodeByteArray(maskBytes, 0, maskBytes.size)
                        Log.d(TAG_EDIT_PHOTO_WITH_PROMPT, "Maske bitmap yüklendi: ${maskBitmap?.width}x${maskBitmap?.height}")
                    } catch (e: Exception) {
                        Log.w(TAG_EDIT_PHOTO_WITH_PROMPT, "Maske bitmap yüklenirken hata: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG_EDIT_PHOTO_WITH_PROMPT, "Resim yüklenirken hata oluştu", e)
                errorLoadingImage = "Resim yüklenemedi: ${e.localizedMessage}"
                originalBitmap = null
            }
        }
        // Resim yüklendikten sonra isLoading'i false yapıyoruz, ana işlem için ayrı kontrol edilecek.
        // Eğer `onProcessPhoto` da bir yükleme durumu yönetecekse, bu isLoading'i orada da yönetmek gerekebilir.
        isLoading = false
        showContent = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        screenTitle, 
                        maxLines = 1, 
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                },
                actions = {
                    if (maskInfoText != null) {
                        IconButton(onClick = { showMaskInfoDialog = true }) {
                            Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.mask_info_title))
                        }
                    }

                    IconButton(
                        onClick = {
                            if (originalBitmap == null) {
                                Toast.makeText(context, context.getString(R.string.image_not_loaded), Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            val currentInstruction = instructionText.text.trim()
                            if (currentInstruction.isBlank()) {
                                Toast.makeText(context, context.getString(R.string.enter_instruction), Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }

                            // Eğer manuel çizim aktifse ve base64Mask null ise, çizimden maske üret.
                            // Şimdilik bu mantık devredışı, AutoMaskPreview'dan gelen maske öncelikli.
                            // Eğer bu ekranda da çizim yapılıp maske oluşturulacaksa, aşağıdaki generateMaskBase64
                            // ve paths kontrolü yeniden etkinleştirilmeli.

                            // val finalMaskBase64: String?
                            // if (paths.isNotEmpty()) { // Kullanıcı bu ekranda YENİ bir çizim yaptıysa
                            //     Log.d(TAG_EDIT_PHOTO_WITH_PROMPT, "Yeni çizim maskesi oluşturuluyor.")
                            //     finalMaskBase64 = generateMaskBase64(...) // Kendi generateMaskBase64 fonksiyonunuz
                            // } else {
                            //     finalMaskBase64 = if (base64Mask == "null") null else base64Mask // Navigasyondan gelen maske
                            // }
                            val finalMaskBase64 = if (base64Mask == "null" || base64Mask.isNullOrEmpty()) null else base64Mask

                            Log.d(TAG_EDIT_PHOTO_WITH_PROMPT, "İşlenecek talimat: $currentInstruction, Maske var mı: ${finalMaskBase64 != null}, EditType: $editType")
                            isLoading = true // İşlem başlarken yükleme göstergesini aktif et
                            onProcessPhoto(imageUri, finalMaskBase64, currentInstruction)
                            // Navigasyon onProcessPhoto içinden yönetildiği için isLoading'i orada false yapmalısınız
                            // veya bir callback ile bu ekrana bildirmelisiniz. Şimdilik burada bırakıyorum.
                        },
                        enabled = originalBitmap != null && !isLoading && instructionText.text.isNotBlank()
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.start_processing))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
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
            if (isLoading && originalBitmap == null && errorLoadingImage == null) {
                LoadingScreen()
            } else if (errorLoadingImage != null) {
                ErrorScreen(errorText = errorLoadingImage!!)
            } else if (originalBitmap != null) {
                AnimatedVisibility(
                    visible = showContent,
                    enter = fadeIn(animationSpec = tween(500)) + slideInVertically(
                        animationSpec = tween(500),
                        initialOffsetY = { it / 4 }
                    )
                ) {
                    MainContent(
                        originalBitmap = originalBitmap,
                        maskBitmap = maskBitmap,
                        processedBitmap = processedBitmap,
                        instructionText = instructionText,
                        onInstructionChange = { instructionText = it },
                        defaultPromptPlaceholder = defaultPromptPlaceholder,
                        onProcessClick = {
                            if (originalBitmap == null) {
                                Toast.makeText(context, context.getString(R.string.image_not_loaded), Toast.LENGTH_SHORT).show()
                                return@MainContent
                            }
                            val currentInstruction = instructionText.text.trim()
                            if (currentInstruction.isBlank()) {
                                Toast.makeText(context, context.getString(R.string.enter_instruction), Toast.LENGTH_SHORT).show()
                                return@MainContent
                            }
                            val finalMaskBase64 = if (base64Mask == "null" || base64Mask.isNullOrEmpty()) null else base64Mask
                            isLoading = true
                            onProcessPhoto(imageUri, finalMaskBase64, currentInstruction)
                        },
                        onImageClick = { bitmap ->
                            showFullScreenDialog = bitmap
                        },
                        onProcessedImageClick = {
                            // Açıklama: İşlenmiş resme tıklandığında PhotoPreviewScreen'e yönlendir
                            val encodedImageUri = Uri.encode(imageUri)
                            val route = Screen.PhotoPreview.route
                                .replace("{${NavArgs.IMAGE_URI}}", encodedImageUri)
                            navController.navigate(route)
                        },
                        isLoading = isLoading,
                        isProcessEnabled = originalBitmap != null && !isLoading && instructionText.text.isNotBlank()
                    )
                }
            } else {
                // Resim henüz yüklenmedi veya bir hata var ama isLoading false (beklenmedik durum)
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.image_cannot_be_displayed), modifier = Modifier.padding(16.dp))
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

        // Maske Bilgisi Dialogu
        if (showMaskInfoDialog) {
            AlertDialog(
                onDismissRequest = { showMaskInfoDialog = false },
                title = { Text(stringResource(R.string.mask_info_title)) },
                text = { Text(maskInfoText ?: stringResource(R.string.error)) },
                confirmButton = {
                    TextButton(onClick = { showMaskInfoDialog = false }) { 
                        Text(stringResource(R.string.understood)) 
                    }
                }
            )
        }
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
            text = stringResource(R.string.image_loading),
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
            imageVector = Icons.Default.Close,
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
    maskBitmap: Bitmap?,
    processedBitmap: Bitmap?,
    instructionText: TextFieldValue,
    onInstructionChange: (TextFieldValue) -> Unit,
    defaultPromptPlaceholder: String,
    onProcessClick: () -> Unit,
    onImageClick: (Bitmap) -> Unit,
    onProcessedImageClick: () -> Unit,
    isLoading: Boolean,
    isProcessEnabled: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Açıklama: Üst kısım - Orijinal resim ve maske yan yana
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Açıklama: Sol taraf - Orijinal resim
            ImageCard(
                title = stringResource(R.string.original_image),
                bitmap = originalBitmap,
                onClick = { originalBitmap?.let { onImageClick(it) } },
                modifier = Modifier.weight(1f)
            )

            // Açıklama: Sağ taraf - Maske
            ImageCard(
                title = stringResource(R.string.selected_mask),
                bitmap = maskBitmap,
                onClick = { maskBitmap?.let { onImageClick(it) } },
                modifier = Modifier.weight(1f)
            )
        }

        // Açıklama: Ayırıcı çizgi
        Divider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
            thickness = 1.dp
        )

        // Açıklama: İşlenmiş resim alanı
        ProcessedImageSection(
            processedBitmap = processedBitmap,
            onImageClick = onProcessedImageClick
        )

        // Açıklama: Talimat giriş alanı
        InstructionInputSection(
            instructionText = instructionText,
            onInstructionChange = onInstructionChange,
            defaultPromptPlaceholder = defaultPromptPlaceholder,
            onProcessClick = onProcessClick,
            isLoading = isLoading,
            isProcessEnabled = isProcessEnabled
        )
    }
}

@Composable
private fun ImageCard(
    title: String,
    bitmap: Bitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
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
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Açıklama: Başlık
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Açıklama: Resim
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
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
                            .padding(4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(6.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = stringResource(R.string.fullscreen_view),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.no_image),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProcessedImageSection(
    processedBitmap: Bitmap?,
    onImageClick: () -> Unit
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
                text = stringResource(R.string.processed_image),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Açıklama: İşlenmiş resim
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onImageClick),
                contentAlignment = Alignment.Center
            ) {
                if (processedBitmap != null) {
                    Image(
                        bitmap = processedBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.processed_image),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // Açıklama: Tam ekran butonu
                    IconButton(
                        onClick = onImageClick,
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
                            contentDescription = stringResource(R.string.fullscreen_view),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.not_processed_yet),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.not_processed_yet),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstructionInputSection(
    instructionText: TextFieldValue,
    onInstructionChange: (TextFieldValue) -> Unit,
    defaultPromptPlaceholder: String,
    onProcessClick: () -> Unit,
    isLoading: Boolean,
    isProcessEnabled: Boolean
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Açıklama: Başlık
            Text(
                text = stringResource(R.string.edit_instruction_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Açıklama: Talimat giriş alanı
            OutlinedTextField(
                value = instructionText,
                onValueChange = onInstructionChange,
                label = { Text(stringResource(R.string.what_do_you_want)) },
                placeholder = { Text(defaultPromptPlaceholder) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            // Açıklama: İşlem butonu
            Button(
                onClick = onProcessClick,
                enabled = isProcessEnabled,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.processing_with_ai))
                } else {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.start_processing))
                }
            }
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
                    contentDescription = stringResource(R.string.close),
                    tint = Color.White
                )
            }

            // Açıklama: Resim
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.fullscreen_view),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
            )
        }
    }
}

// Orijinal dosyanızdaki scaleBitmapToFit fonksiyonu
fun scaleBitmapToFit(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
    if (maxWidth <= 0 || maxHeight <= 0) return bitmap // Geçersiz boyutlar

    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return bitmap // Geçersiz bitmap boyutları

    val ratioBitmap = width.toFloat() / height.toFloat()
    val ratioMax = maxWidth.toFloat() / maxHeight.toFloat()

    var finalWidth = maxWidth
    var finalHeight = maxHeight

    if (ratioMax > ratioBitmap) {
        finalWidth = (maxHeight.toFloat() * ratioBitmap).toInt()
    } else {
        finalHeight = (maxWidth.toFloat() / ratioBitmap).toInt()
    }
    // Güvenlik için, finalWidth/Height 0'dan büyük olmalı
    if (finalWidth <= 0) finalWidth = 1
    if (finalHeight <= 0) finalHeight = 1

    return Bitmap.createScaledBitmap(bitmap, finalWidth, finalHeight, true)
}


// generateMaskBase64 fonksiyonu da burada olmalı EĞER bu ekranda manuel çizimden maske üretilecekse.
// Örnek:
// @RequiresApi(Build.VERSION_CODES.O)
// private fun generateMaskBase64(
// originalBitmapWidth: Int,
// originalBitmapHeight: Int,
// paths: List<PathData>, // Kendi PathData'nız
// scaleXToOriginal: Float,
// scaleYToOriginal: Float,
// canvasOffsetXPx: Float,
// canvasOffsetYPx: Float
// ): String? {
// if (paths.isEmpty()) return null
//
//    // Create a new bitmap for the mask with original dimensions
//    val maskBitmap = Bitmap.createBitmap(originalBitmapWidth, originalBitmapHeight, Bitmap.Config.ALPHA_8)
//    val canvas = android.graphics.Canvas(maskBitmap)
//    canvas.drawColor(android.graphics.Color.BLACK) // Mask background is black (transparent for Vertex AI)
//
//    val paint = android.graphics.Paint().apply {
// style = android.graphics.Paint.Style.STROKE
// isAntiAlias = true
// strokeJoin = android.graphics.Paint.Join.ROUND
// strokeCap = android.graphics.Paint.Cap.ROUND
// color = android.graphics.Color.WHITE // Mask drawings are white (opaque for Vertex AI)
//    }
//
//    val matrix = Matrix()
// matrix.postTranslate(-canvasOffsetXPx, -canvasOffsetYPx) // Translate paths back relative to the scaled image
// matrix.postScale(scaleXToOriginal, scaleYToOriginal) // Scale paths to original image dimensions
//
// paths.forEach { pathData ->
//        paint.strokeWidth = pathData.strokeWidth * scaleXToOriginal // Scale stroke width too
//        // pathData.tool'e göre paint ayarları (örn. Xfermode for eraser)
//
//        val transformedPath = android.graphics.Path()
// pathData.androidPath.transform(matrix, transformedPath)
// canvas.drawPath(transformedPath, paint)
//    }
//
//    val outputStream = ByteArrayOutputStream()
//    maskBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
//    val byteArray = outputStream.toByteArray()
// maskBitmap.recycle() // Bitmap'i serbest bırak
//    return Base64.getEncoder().encodeToString(byteArray)
// }


// Renk seçici ve fırça boyutu slider'ı için composable'larınız (EditPhotoBottomBar vb.)
// orijinal dosyanızdaki gibi burada veya ayrı bir dosyada tanımlı olmalı.
// Örnek:
// @Composable
// fun ColorPickerRow(...) { ... }
// @Composable
// fun EditPhotoBottomBar(...) { ... }
