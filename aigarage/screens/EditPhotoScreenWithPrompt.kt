package com.rootcrack.aigarage.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix // Matrix eklendi
import android.graphics.Paint // Paint eklendi
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect // DashPathEffect için
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.scale // Bitmap ölçekleme için
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

// Grafik nesneleri için takma adlar
typealias AndroidPath = android.graphics.Path
typealias AndroidPaint = android.graphics.Paint
typealias AndroidCanvas = android.graphics.Canvas

// Araç türleri
enum class DrawingTool {
    BRUSH, ERASER
}

// Çizim yolu ve boya bilgilerini tutan data class
data class PathData(
    val androidPath: AndroidPath = AndroidPath(), // android.graphics.Path kullanılacak
    var color: Color = Color.White, // Jetpack Compose Color
    var strokeWidth: Float = 20f,
    val tool: DrawingTool = DrawingTool.BRUSH,
    var alpha: Float = 1.0f, // Fırça opaklığı
)

const val TAG_EDIT_PHOTO_WITH_PROMPT = "EditPhotoScreenWithPrompt"

// Vertex AI için maske renkleri (generateMaskBase64 içinde kullanılacak)
val MASK_DRAW_COLOR_ANDROID = android.graphics.Color.WHITE // android.graphics.Color
val MASK_BACKGROUND_COLOR_ANDROID = android.graphics.Color.BLACK // android.graphics.Color


@SuppressLint("UnusedBoxWithConstraintsScope", "NewApi")
@RequiresApi(Build.VERSION_CODES.O) // Base64.getEncoder() için O gerekli, decodeStream için de bazı geliştirmeler var
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPhotoScreenWithPrompt(
    navController: NavController,
    imageUri: String, // String olarak alıp Uri.parse() ile kullanacağız
    initialInstruction: String,
    onProcessPhoto: (originalImageUri: String, maskBase64: String?, instruction: String) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current.density

    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var scaledBitmapToDisplay by remember { mutableStateOf<ImageBitmap?>(null) }
    var bitmapDisplaySize by remember { mutableStateOf(IntSize.Zero) } // Görüntülenen bitmap'in UI üzerindeki boyutu

    val paths = remember { mutableStateListOf<PathData>() }
    val undonePaths = remember { mutableStateListOf<PathData>() } // Geri alınan yolları tutmak için

    var currentBrushColor by remember { mutableStateOf(Color.Yellow) } // Seçilen fırça rengi
    var currentAlpha by remember { mutableStateOf(0.5f) } // %50 opaklık varsayılan
    var selectedTool by remember { mutableStateOf(DrawingTool.BRUSH) }
    var brushStrokeWidth by rememberSaveable { mutableStateOf(30f) }
    var showBrushSizeSlider by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }


    var canvasWidthPx by remember { mutableStateOf(0) }
    var canvasHeightPx by remember { mutableStateOf(0) }

    var displayToOriginalScaleX by remember { mutableStateOf(1f) }
    var displayToOriginalScaleY by remember { mutableStateOf(1f) }
    var canvasOffsetInBox by remember { mutableStateOf(Offset.Zero) } // Canvas'ın BoxWithConstraints içindeki ofseti

    var isLoading by remember { mutableStateOf(true) }
    var errorLoadingImage by remember { mutableStateOf<String?>(null) }

    var instructionText by rememberSaveable(initialInstruction, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialInstruction))
    }

    LaunchedEffect(imageUri) {
        isLoading = true
        errorLoadingImage = null
        Log.d(TAG_EDIT_PHOTO_WITH_PROMPT, "Resim yükleniyor: $imageUri")
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(imageUri) // String'i Uri'ye çevir
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val tempBitmap = BitmapFactory.decodeStream(inputStream)
                    if (tempBitmap == null) {
                        throw Exception("BitmapFactory.decodeStream null döndü.")
                    }
                    originalBitmap = tempBitmap
                    Log.d(TAG_EDIT_PHOTO_WITH_PROMPT, "Orijinal resim yüklendi: ${originalBitmap?.width}x${originalBitmap?.height}")
                }
            } catch (e: Exception) {
                Log.e(TAG_EDIT_PHOTO_WITH_PROMPT, "Resim yüklenirken hata oluştu", e)
                errorLoadingImage = "Resim yüklenemedi: ${e.localizedMessage}"
                originalBitmap = null
            }
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Düzenle ve Tarif Et") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    // İşlemi onayla butonu (Sağ üst köşe)
                    IconButton(
                        onClick = {
                            if (originalBitmap == null) {
                                Toast.makeText(context, "Lütfen önce bir resmin yüklenmesini bekleyin.", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            val currentInstruction = instructionText.text
                            if (paths.isEmpty() && currentInstruction.isBlank()) {
                                Toast.makeText(context, "Lütfen bir düzenleme yapın veya bir talimat girin.", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }

                            coroutineScope.launch {
                                isLoading = true // Yükleme göstergesini başlat
                                val maskBase64 = if (paths.isNotEmpty()) {
                                    generateMaskBase64(
                                        originalBitmapWidth = originalBitmap!!.width,
                                        originalBitmapHeight = originalBitmap!!.height,
                                        paths = paths.toList(),
                                        scaleXToOriginal = displayToOriginalScaleX,
                                        scaleYToOriginal = displayToOriginalScaleY,
                                        canvasOffsetXPx = canvasOffsetInBox.x,
                                        canvasOffsetYPx = canvasOffsetInBox.y
                                    )
                                } else {
                                    null
                                }
                                Log.d(TAG_EDIT_PHOTO_WITH_PROMPT, "İşlenecek talimat: $currentInstruction, Maske var mı: ${maskBase64 != null}")
                                onProcessPhoto(imageUri, maskBase64, currentInstruction)
                                // isLoading'i burada false yapmıyoruz, navigasyondan sonra bu ekran görünmeyecekse.
                            }
                        },
                        enabled = originalBitmap != null && (paths.isNotEmpty() || instructionText.text.isNotBlank()) && !isLoading
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Onayla")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            EditPhotoBottomBar(
                selectedTool = selectedTool,
                onToolSelected = { tool ->
                    selectedTool = tool
                    showBrushSizeSlider = (tool == DrawingTool.BRUSH || tool == DrawingTool.ERASER)
                    if (tool == DrawingTool.BRUSH) showColorPicker = true else showColorPicker = false
                },
                onUndo = {
                    if (paths.isNotEmpty()) {
                        undonePaths.add(paths.removeLast())
                    }
                },
                onRedo = {
                    if (undonePaths.isNotEmpty()) {
                        paths.add(undonePaths.removeLast())
                    }
                },
                canUndo = paths.isNotEmpty(),
                canRedo = undonePaths.isNotEmpty(),
                showSizeSlider = showBrushSizeSlider,
                brushSize = brushStrokeWidth,
                onBrushSizeChange = { newSize -> brushStrokeWidth = newSize },
                showColorButton = selectedTool == DrawingTool.BRUSH, // Sadece fırça seçiliyken renk butonunu göster
                onColorButtonClicked = { showColorPicker = !showColorPicker },
                isProcessing = isLoading // Yükleme durumunu bottom bar'a iletiyoruz
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surfaceVariant) // Arka plan rengi
        ) {
            if (isLoading && originalBitmap == null && errorLoadingImage == null) {
                Box(modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(), contentAlignment = Alignment.Center) { // weight(1f) ile ortala
                    CircularProgressIndicator()
                    Text("Resim yükleniyor...", modifier = Modifier.padding(top = 70.dp))
                }
            } else if (errorLoadingImage != null) {
                Box(modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(), contentAlignment = Alignment.Center) { // weight(1f)
                    Text(errorLoadingImage!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                }
            } else if (originalBitmap != null) {
                // Renk Seçici (gizlenebilir/gösterilebilir)
                if (showColorPicker && selectedTool == DrawingTool.BRUSH) {
                    ColorPickerRow(
                        selectedColor = currentBrushColor,
                        onColorSelected = { color -> currentBrushColor = color },
                        selectedAlpha = currentAlpha,
                        onAlphaChanged = { alpha -> currentAlpha = alpha }
                    )
                    Divider()
                }

                // Resim ve Çizim Alanı
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f) // Kalan alanı kapla
                        .fillMaxWidth()
                        .background(Color.DarkGray) // Canvas arka planı
                        .clipToBounds(), // Dışarı taşan çizimleri engelle
                    contentAlignment = Alignment.Center
                ) {
                    val boxWidthPx = constraints.maxWidth
                    val boxHeightPx = constraints.maxHeight

                    // Orijinal bitmap'i BoxWithConstraints'e sığacak şekilde ölçekle
                    // ve ölçeklenmiş ImageBitmap'i ve boyutlarını hesapla
                    LaunchedEffect(originalBitmap, boxWidthPx, boxHeightPx) {
                        if (originalBitmap != null && boxWidthPx > 0 && boxHeightPx > 0) {
                            val scaled = scaleBitmapToFit(originalBitmap!!, boxWidthPx, boxHeightPx)
                            scaledBitmapToDisplay = scaled.asImageBitmap()
                            bitmapDisplaySize = IntSize(scaled.width, scaled.height) // Piksel cinsinden

                            // Ölçek faktörlerini hesapla
                            displayToOriginalScaleX = originalBitmap!!.width.toFloat() / scaled.width
                            displayToOriginalScaleY = originalBitmap!!.height.toFloat() / scaled.height

                            // Canvas'ın Box içindeki ofsetini hesapla
                            canvasOffsetInBox = Offset(
                                (boxWidthPx - scaled.width) / 2f,
                                (boxHeightPx - scaled.height) / 2f
                            )

                            // Canvas boyutlarını güncelle (piksel cinsinden)
                            canvasWidthPx = scaled.width
                            canvasHeightPx = scaled.height
                        }
                    }

                    scaledBitmapToDisplay?.let { bmp ->
                        Image(
                            bitmap = bmp,
                            contentDescription = "Düzenlenecek Resim",
                            modifier = Modifier.size(
                                width = (bitmapDisplaySize.width / density).dp,
                                height = (bitmapDisplaySize.height / density).dp
                            ),
                            contentScale = ContentScale.Fit // Fit olmalı ki canvas ile eşleşsin
                        )

                        Canvas(
                            modifier = Modifier
                                .size(
                                    width = (bitmapDisplaySize.width / density).dp,
                                    height = (bitmapDisplaySize.height / density).dp
                                )
                                .pointerInput(Unit) {
                                    var currentPath: PathData? = null
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            undonePaths.clear() // Yeni çizim başladığında ileri alma listesini temizle
                                            val newAndroidPath = AndroidPath()
                                            newAndroidPath.moveTo(offset.x, offset.y)
                                            currentPath = PathData(
                                                androidPath = newAndroidPath,
                                                color = if (selectedTool == DrawingTool.BRUSH) currentBrushColor else Color.Transparent, // Silgi için transparan gibi davranabiliriz
                                                strokeWidth = brushStrokeWidth,
                                                tool = selectedTool,
                                                alpha = if (selectedTool == DrawingTool.BRUSH) currentAlpha else 1.0f // Silgi için tam opaklık
                                            )
                                        },
                                        onDrag = { change, _ ->
                                            currentPath?.androidPath?.lineTo(
                                                change.position.x,
                                                change.position.y
                                            )
                                            // Canlı çizim için anlık güncellemeyi tetiklemek amacıyla
                                            // geçici bir path listesi kullanılabilir veya recomposition tetiklenebilir.
                                            // Şimdilik sadece onDragEnd'de ekliyoruz.
                                            // Daha akıcı bir deneyim için burada da path'i güncelleyip recompose tetiklenebilir.
                                            // Ancak bu, performans sorunlarına yol açabilir.
                                            // En basit çözüm, paths listesini bir state gibi kullanmaktır.
                                            // Şimdilik bu kısmı optimize etmiyoruz, drag bitince path ekleniyor.
                                            // Bunun yerine drawContext.canvas.drawPath ile direkt çizilebilir.
                                        },
                                        onDragEnd = {
                                            currentPath?.let {
                                                paths.add(it)
                                            }
                                            currentPath = null
                                        }
                                    )
                                }
                        ) {
                            // Mevcut tüm yolları çiz
                            paths.forEach { pathData ->
                                drawPath(
                                    path = pathData.androidPath.asComposePath(), // Android Path'i Compose Path'e çevir
                                    color = pathData.color.copy(alpha = if (pathData.tool == DrawingTool.BRUSH) pathData.alpha else 1.0f), // Fırça ise alpha uygula
                                    style = Stroke(
                                        width = pathData.strokeWidth,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    ),
                                    alpha = if (pathData.tool == DrawingTool.BRUSH) pathData.alpha else 1.0f
                                    // Silgi için özel bir blend mode gerekebilir, ancak şimdilik alpha ile yetiniyoruz.
                                    // Gerçek bir silgi için PorterDuff.Mode.CLEAR kullanılmalı, bu da nativeCanvas gerektirir.
                                )
                            }
                            // Halihazırda çizilmekte olan yolu da (eğer varsa) çiz (opsiyonel, daha iyi UX için)
                            // Bu kısım detectDragGestures içindeki onDrag ile daha iyi entegre edilebilir.
                        }
                    }
                }

                // Prompt için TextField
                OutlinedTextField(
                    value = instructionText,
                    onValueChange = { instructionText = it },
                    label = { Text("Ne yapmak istiyorsunuz? (Örn: Arabayı kırmızı yap)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    ),
                    maxLines = 3
                )
            }
            // Eğer yükleme devam ediyorsa, ekranın alt kısmında bir yükleme göstergesi
            if (isLoading && originalBitmap != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("İşleniyor...")
                }
            }
        }
    }
}


/**
 * Bitmap'i verilen maksimum genişlik ve yüksekliğe sığacak şekilde, en boy oranını koruyarak ölçekler.
 */
fun scaleBitmapToFit(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
    if (maxWidth <= 0 || maxHeight <= 0) return bitmap // Geçersiz boyutlar

    val width = bitmap.width
    val height = bitmap.height

    val ratioBitmap = width.toFloat() / height.toFloat()
    val ratioMax = maxWidth.toFloat() / maxHeight.toFloat()

    var finalWidth = maxWidth
    var finalHeight = maxHeight

    if (ratioMax > ratioBitmap) {
        finalWidth = (maxHeight.toFloat() * ratioBitmap).toInt()
    } else {
        finalHeight = (maxWidth.toFloat() / ratioBitmap).toInt()
    }
    return Bitmap.createScaledBitmap(bitmap, finalWidth, finalHeight, true)
}


@Composable
fun ColorPickerRow(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    selectedAlpha: Float,
    onAlphaChanged: (Float) -> Unit,
) {
    val colors = listOf(
        Color.Red, Color.Yellow, Color.Green, Color.Blue, Color.Magenta, Color.Cyan, Color.Black, Color.White, Color.Gray
    )
    Column(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)) {
        Text("Fırça Rengi ve Opaklığı:", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            colors.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color)
                        .clickable { onColorSelected(color) }
                        .then(
                            if (color == selectedColor) Modifier.border(
                                2.dp,
                                MaterialTheme.colorScheme.outline,
                                CircleShape
                            ) else Modifier
                        )
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text("Opaklık: ${(selectedAlpha * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = selectedAlpha,
            onValueChange = onAlphaChanged,
            valueRange = 0.1f..1.0f, // %10 ile %100 arası opaklık
            steps = 8 // (1.0 - 0.1) / 0.1 - 1 = 9 - 1 = 8 adım (9 farklı değer)
        )
    }
}


@Composable
fun EditPhotoBottomBar(
    selectedTool: DrawingTool,
    onToolSelected: (DrawingTool) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    showSizeSlider: Boolean,
    brushSize: Float,
    onBrushSizeChange: (Float) -> Unit,
    showColorButton: Boolean,
    onColorButtonClicked: () -> Unit,
    isProcessing: Boolean, // Yükleme durumunu almak için eklendi
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp) // Yüzey rengi
    ) {
        Column {
            if (showSizeSlider) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Boyut:", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = brushSize,
                        onValueChange = onBrushSizeChange,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        valueRange = 5f..100f, // Fırça/Silgi boyutu aralığı
                        steps = 18 // (100-5)/5 - 1 = 19 - 1 = 18 adım
                    )
                    Text("${brushSize.toInt()}px", style = MaterialTheme.typography.bodyMedium)
                }
                Divider()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Geri Al Butonu
                IconButton(onClick = onUndo, enabled = canUndo && !isProcessing) {
                    Icon(
                        Icons.Filled.Undo,
                        contentDescription = "Geri Al",
                        //tint = if (canUndo && !isProcessing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = androidx.wear.compose.material.ContentAlpha.disabled)
                        tint = if (canUndo && !isProcessing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

                    )
                }

                // İleri Al Butonu
                IconButton(onClick = onRedo, enabled = canRedo && !isProcessing) {
                    Icon(
                        Icons.AutoMirrored.Filled.Redo, // Yeniden yap için Redo ikonu
                        contentDescription = "İleri Al",
                        //tint = if (canRedo && !isProcessing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = androidx.wear.compose.material.ContentAlpha.disabled)
                        tint = if (canRedo && !isProcessing) MaterialTheme.colorScheme.primary else  MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }

                // Fırça Butonu
                IconButton(
                    onClick = { onToolSelected(DrawingTool.BRUSH) },
                    enabled = !isProcessing,
                    modifier = if (selectedTool == DrawingTool.BRUSH) Modifier.background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        CircleShape
                    ) else Modifier
                ) {
                    Icon(
                        Icons.Filled.Brush,
                        contentDescription = "Fırça",
                        tint = if (selectedTool == DrawingTool.BRUSH && !isProcessing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                // Renk Seçici Butonu (Sadece fırça seçiliyken ve aktifken)
                if (showColorButton) {
                    IconButton(onClick = onColorButtonClicked, enabled = !isProcessing) {
                        Icon(
                            Icons.Filled.ColorLens, // Renk paleti ikonu
                            contentDescription = "Renk Seç",
                            //tint = if (!isProcessing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = androidx.wear.compose.material.ContentAlpha.disabled)
                            tint = if (!isProcessing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

                        )
                    }
                }


                // Silgi Butonu (Kaldırıldı, maske doğrudan AI tarafından yorumlanacak)
                // Eğer silgi aracı yine de isteniyorsa:
                /*
                IconButton(
                    onClick = { onToolSelected(DrawingTool.ERASER) },
                    enabled = !isProcessing,
                    modifier = if (selectedTool == DrawingTool.ERASER) Modifier.background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        CircleShape
                    ) else Modifier
                ) {
                    Icon(
                        painterResource(id = R.drawable.ic_eraser), // Özel bir silgi ikonu kullanabilirsiniz
                        contentDescription = "Silgi",
                        tint = if (selectedTool == DrawingTool.ERASER && !isProcessing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                */
            }
        }
    }
}

/**
 * Verilen yolları kullanarak orijinal resim boyutlarında bir maske oluşturur ve Base64 string olarak döndürür.
 * Bu fonksiyon, UI üzerindeki çizim koordinatlarını orijinal resim koordinatlarına ölçekler.
 */
@RequiresApi(Build.VERSION_CODES.O) // Base64.getEncoder() için
fun generateMaskBase64(
    originalBitmapWidth: Int,
    originalBitmapHeight: Int,
    paths: List<PathData>,
    scaleXToOriginal: Float,
    scaleYToOriginal: Float,
    canvasOffsetXPx: Float, // Görüntülenen canvas'ın Box içindeki X ofseti (piksel)
    canvasOffsetYPx: Float,  // Görüntülenen canvas'ın Box içindeki Y ofseti (piksel)
): String? {
    if (paths.isEmpty()) return null
    if (originalBitmapWidth <= 0 || originalBitmapHeight <= 0) {
        Log.e(TAG_EDIT_PHOTO_WITH_PROMPT, "generateMaskBase64: Geçersiz orijinal bitmap boyutları.")
        return null
    }

    // Maske bitmap'ini orijinal resim boyutlarında oluştur
    val maskBitmap = Bitmap.createBitmap(originalBitmapWidth, originalBitmapHeight, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(maskBitmap)

    // Maske arka planını ayarla (örneğin siyah)
    canvas.drawColor(MASK_BACKGROUND_COLOR_ANDROID)

    val paint = AndroidPaint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    paths.forEach { pathData ->
        // Path'i orijinal resim koordinatlarına ölçekle
        val scaledPath = AndroidPath()
        val matrix = Matrix()

        // 1. Canvas ofsetini çıkar (çizimler canvas'ın sol üst köşesine göre (0,0))
        // 2. Orijinal resim boyutlarına ölçekle
        // Bu sıralama önemli. Çizimler canvas'ın kendi (0,0) koordinat sistemine göre yapıldığı için
        // önce canvas ofsetini düşünmemize gerek yok. Doğrudan path koordinatlarını ölçekleyebiliriz.
        // Ancak, pathData içindeki koordinatlar, scaledBitmapToDisplay üzerindeki koordinatlardır.
        // Bu koordinatlar, BoxWithConstraints içindeki scaledBitmapToDisplay'in sol üst köşesine göredir.
        // Bu nedenle, canvasOffsetXPx ve canvasOffsetYPx'i burada dikkate almamalıyız,
        // çünkü PathData'daki koordinatlar zaten canvas'ın (0,0)'ına göredir.

        matrix.setScale(scaleXToOriginal, scaleYToOriginal)
        pathData.androidPath.transform(matrix, scaledPath)


        paint.color = if (pathData.tool == DrawingTool.BRUSH) {
            MASK_DRAW_COLOR_ANDROID // Fırça ile çizilen alanlar maskede beyaz olacak
        } else {
            // Silgi aracı şu an doğrudan desteklenmiyor.
            // Eğer silgi olsaydı, PorterDuffXfermode ile CLEAR kullanılabilirdi.
            // Şimdilik, Vertex AI'ın sadece "maskelenmiş alanları değiştir" mantığına güveniyoruz.
            // Bu durumda, silgi ile çizilen alanların maskede siyah (arka plan rengi) olması gerekir.
            // Ancak PathData'da silgi için ayrı bir renk tutmadık, bu yüzden bu mantık burada eksik.
            // Eğer silgi BRUSH ile aynı şekilde (ama farklı renkle) path ekliyorsa, burada ona göre davranmalıyız.
            // Şu anki yapıda, "silgi" path'leri de beyaz olarak işaretlenecek, bu istenmeyebilir.
            // Vertex AI'ın "inpainting" (maskelenen alanı doldurma) ve "outpainting" (maskelenen alan dışını değiştirme)
            // gibi farklı modları olabilir. Maskenin nasıl yorumlanacağı API'ye bağlıdır.
            // Genelde, maskenin beyaz kısımları "etkilenecek alanlar" anlamına gelir.
            // Eğer silgi "etkilenmeyecek alan" oluşturacaksa, o path'ler ya maskeye çizilmemeli
            // ya da maske üzerinde arka plan rengiyle (siyah) çizilmeli.
            // Şimdilik, tüm pathData'ların "etkilenecek alan" olduğunu varsayıyoruz.
            MASK_DRAW_COLOR_ANDROID
        }
        paint.strokeWidth = pathData.strokeWidth * scaleXToOriginal // Kalınlığı da ölçekle (genellikle X ölçeği yeterli olur)
        paint.alpha = (pathData.alpha * 255).toInt() // Compose alpha (0-1) to Android Paint alpha (0-255)

        // PorterDuff modunu ayarla (Silgi için önemli olabilir)
        if (pathData.tool == DrawingTool.ERASER) {
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) // Silgi için
            paint.color = Color.Transparent.toArgb() // Silgi rengi transparan olmalı CLEAR modu için
        } else {
            paint.xfermode = null // Fırça için normal çizim
            paint.color = MASK_DRAW_COLOR_ANDROID // Fırça için maske rengi
        }


        canvas.drawPath(scaledPath, paint)
    }

    // Bitmap'i Base64 string'e dönüştür
    val outputStream = ByteArrayOutputStream()
    maskBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
    val byteArray = outputStream.toByteArray()
    maskBitmap.recycle() // Bitmap'i serbest bırak

    return java.util.Base64.getEncoder().encodeToString(byteArray)
}
