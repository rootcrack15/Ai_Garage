package com.rootcrack.aigarage.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.AndroidPath
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rootcrack.aigarage.navigation.EditTypeValues
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


    // editType'a göre başlık ve talimat placeholder'ı
    val screenTitle = when (editType) {
        EditTypeValues.FOREGROUND -> "Nesneyi Düzenle (${initialInstruction.ifEmpty { "Ön Plan" }})"
        EditTypeValues.BACKGROUND -> "Arka Planı Düzenle (${initialInstruction.ifEmpty { "Replace background with a breathtaking cinematic vista of towering snow-capped mountains glowing under a golden sunset, their peaks piercing through low-hanging clouds, with shimmering light reflecting off a crystal-clear lake below." }})"
        else -> "Düzenle ve Tarif Et" // editType = NONE veya bilinmeyen bir değer
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
        EditTypeValues.FOREGROUND -> "Örn: Kırmızı parlak bir spor arabaya dönüştür"
        EditTypeValues.BACKGROUND -> "Örn: Arka plana karlı dağlar ve gün batımı ekle"
        else -> "Ne yapmak istersin? (Örn: Çizgi film karakterine benzet)"
    }

    val maskInfoText = if (base64Mask != null && base64Mask != "null" && base64Mask.isNotEmpty()) {
        when (editType) {
            EditTypeValues.FOREGROUND -> "Maske: Sadece seçili nesne (ön plan) üzerinde değişiklik yapılacak."
            EditTypeValues.BACKGROUND -> "Maske: Sadece seçili olmayan alan (arka plan) üzerinde değişiklik yapılacak."
            else -> "Maske: Resmin belirli bir bölümü üzerinde değişiklik yapılacak." // Genel durum
        }
    } else {
        null // Maske yoksa bilgi gösterme veya "Maske yok, tüm resim etkilenecek."
    }


    LaunchedEffect(imageUri) {
        isLoading = true // Resim yüklenirken genel yükleme aktif
        errorLoadingImage = null
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
            } catch (e: Exception) {
                Log.e(TAG_EDIT_PHOTO_WITH_PROMPT, "Resim yüklenirken hata oluştu", e)
                errorLoadingImage = "Resim yüklenemedi: ${e.localizedMessage}"
                originalBitmap = null
            }
        }
        // Resim yüklendikten sonra isLoading'i false yapıyoruz, ana işlem için ayrı kontrol edilecek.
        // Eğer `onProcessPhoto` da bir yükleme durumu yönetecekse, bu isLoading'i orada da yönetmek gerekebilir.
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle, maxLines = 1, style = MaterialTheme.typography.titleMedium) }, // Başlık fontunu küçülttüm
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    if (maskInfoText != null) {
                        IconButton(onClick = { showMaskInfoDialog = true }) {
                            Icon(Icons.Filled.Info, contentDescription = "Maske Bilgisi")
                        }
                    }

                    IconButton(
                        onClick = {
                            if (originalBitmap == null) {
                                Toast.makeText(context, "Lütfen resmin yüklenmesini bekleyin.", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            val currentInstruction = instructionText.text.trim()
                            if (currentInstruction.isBlank()) {
                                Toast.makeText(context, "Lütfen bir düzenleme talimatı girin.", Toast.LENGTH_SHORT).show()
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
                        Icon(Icons.Filled.Check, contentDescription = "Onayla ve İşle")
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
        // Çizim araçları için BottomBar (EĞER MANUEL ÇİZİM AKTİFSE)
        // Eğer sadece AutoMaskPreview'dan geliyorsa ve burada çizim yoksa bu BottomBar GEREKSİZDİR.
        // Şimdilik, eğer base64Mask null ise ve editType "none" ise çizim araçlarını gösterelim.
        // Bu, genel düzenleme modu için mantıklı olabilir.
        bottomBar = {
            if (base64Mask.isNullOrEmpty() || base64Mask == "null" && editType == EditTypeValues.NONE) {
                // Bu kısım, manuel çizim için orijinal EditPhotoScreenWithPrompt'taki BottomBar'ınız olmalı.
                // Eğer manuel çizim hiç kullanılmayacaksa, bu bottomBar tamamen kaldırılabilir.
                // Örnek olarak basit bir BottomAppBar bırakıyorum, kendi EditPhotoBottomBar'ınızı entegre edin.
                /*
                EditPhotoBottomBar( // Kendi BottomBar Composable'ınız
                    selectedTool = selectedTool,
                    onToolSelected = { tool ->
                        selectedTool = tool
                        showBrushSizeSlider = (tool == DrawingTool.BRUSH || tool == DrawingTool.ERASER)
                        showColorPicker = (tool == DrawingTool.BRUSH)
                    },
                    onUndo = { if (paths.isNotEmpty()) undonePaths.add(paths.removeLast()) },
                    onRedo = { if (undonePaths.isNotEmpty()) paths.add(undonePaths.removeLast()) },
                    canUndo = paths.isNotEmpty(),
                    canRedo = undonePaths.isNotEmpty(),
                    showSizeSlider = showBrushSizeSlider,
                    brushSize = brushStrokeWidth,
                    onBrushSizeChange = { newSize -> brushStrokeWidth = newSize },
                    showColorButton = selectedTool == DrawingTool.BRUSH,
                    onColorButtonClicked = { showColorPicker = !showColorPicker },
                    isProcessing = isLoading
                )
                */
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // Scaffold'dan gelen padding
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Maske Bilgisi Dialogu
            if (showMaskInfoDialog) {
                AlertDialog(
                    onDismissRequest = { showMaskInfoDialog = false },
                    title = { Text("Maske Kullanım Bilgisi") },
                    text = { Text(maskInfoText ?: "Bir hata oluştu.") },
                    confirmButton = {
                        TextButton(onClick = { showMaskInfoDialog = false }) { Text("Anladım") }
                    }
                )
            }

            // Resim yükleniyor veya hata durumu
            if (isLoading && originalBitmap == null && errorLoadingImage == null) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                    Text("Resim yükleniyor...", modifier = Modifier.padding(top = 70.dp))
                }
            } else if (errorLoadingImage != null) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(errorLoadingImage!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                }
            } else if (originalBitmap != null) {
                // Ana içerik (Resim, Çizim Alanı, Talimat Kutusu)
                Column(
                    modifier = Modifier
                        .weight(1f) // Talimat kutusuna yer bırakmak için
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp) // İç padding
                        .verticalScroll(rememberScrollState()), // Uzun içerik için kaydırma
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Renk Seçici (EĞER MANUEL ÇİZİM AKTİFSE VE GEREKLİYSE)
                    // if (showColorPicker && selectedTool == DrawingTool.BRUSH && (base64Mask.isNullOrEmpty() || base64Mask == "null")) {
                    //     ColorPickerRow(...) // Kendi ColorPickerRow Composable'ınız
                    //     Divider()
                    // }

                    // Resim ve Çizim Alanı
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f) // Resim oranına göre veya sabit
                            .background(Color.DarkGray)
                            .clipToBounds(),
                        contentAlignment = Alignment.Center
                    ) {
                        val boxWidthPx = constraints.maxWidth
                        val boxHeightPx = constraints.maxHeight

                        LaunchedEffect(originalBitmap, boxWidthPx, boxHeightPx) {
                            if (originalBitmap != null && boxWidthPx > 0 && boxHeightPx > 0) {
                                val scaled = scaleBitmapToFit(originalBitmap!!, boxWidthPx, boxHeightPx)
                                scaledBitmapToDisplay = scaled.asImageBitmap()
                                bitmapDisplaySize = IntSize(scaled.width, scaled.height)

                                displayToOriginalScaleX = originalBitmap!!.width.toFloat() / scaled.width
                                displayToOriginalScaleY = originalBitmap!!.height.toFloat() / scaled.height
                                canvasOffsetInBox = Offset(
                                    (boxWidthPx - scaled.width) / 2f,
                                    (boxHeightPx - scaled.height) / 2f
                                )
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
                                contentScale = ContentScale.Fit
                            )

                            // MANUEL ÇİZİM İÇİN CANVAS (EĞER AKTİFSE)
                            // if (base64Mask.isNullOrEmpty() || base64Mask == "null" && editType == EditTypeValues.NONE) {
                            // Canvas(
                            // modifier = Modifier
                            // .size( // Boyutları Image ile aynı olmalı
                            // width = (bitmapDisplaySize.width / density).dp,
                            // height = (bitmapDisplaySize.height / density).dp
                            // )
                            // .pointerInput(Unit) {
                            // detectDragGestures(
                            // onDragStart = { offset ->
                            //                                     // Yeni path başlatma mantığı
                            //                                 },
                            // onDrag = { change, dragAmount ->
                            //                                     // Path'e nokta ekleme mantığı
                            // change.consume()
                            //                                 },
                            // onDragEnd = {
                            //                                     // Path bitirme mantığı
                            //                                 }
                            // )
                            // }
                            // ) {
                            // Draw existing paths
                            // paths.forEach { pathData -> drawPath(pathData.androidPath.asComposePath(), color = pathData.color.copy(alpha=pathData.alpha), style = Stroke(width = pathData.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
                            // }
                            // }
                        }
                    } // BoxWithConstraints Sonu

                    Spacer(modifier = Modifier.height(16.dp))

                    // Maske Bilgisi (Eğer TopAppBar'da dialog yerine burada gösterilecekse)
                    if (maskInfoText != null && !showMaskInfoDialog) { // Dialog açık değilse göster
                        Text(
                            text = maskInfoText,
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp, start = 8.dp, end = 8.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } // İç Column Sonu (Resim ve çizim alanı)
            } else {
                // Resim henüz yüklenmedi veya bir hata var ama isLoading false (beklenmedik durum)
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Resim gösterilemiyor.", modifier = Modifier.padding(16.dp))
                }
            }


            // Talimat Giriş Alanı (Ekranın altına daha yakın)
            OutlinedTextField(
                value = instructionText,
                onValueChange = { instructionText = it },
                label = { Text("Ne yapmak istersin?") },
                placeholder = { Text(defaultPromptPlaceholder) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp), // Alt boşluk
                singleLine = false,
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            // Yükleme göstergesi (işlem sırasında)
            if (isLoading && originalBitmap != null) { // Resim yüklendiyse ve işlem yapılıyorsa
                Spacer(modifier = Modifier.height(8.dp))
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                Text("İşleniyor...", modifier = Modifier.align(Alignment.CenterHorizontally).padding(top=4.dp))
                Spacer(modifier = Modifier.height(16.dp))
            }


        } // Ana Column Sonu (Scaffold içeriği)
    } // Scaffold Sonu
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
