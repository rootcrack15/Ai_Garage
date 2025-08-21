package com.rootcrack.aigarage.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.rootcrack.aigarage.R
import com.rootcrack.aigarage.navigation.Screen
import com.rootcrack.aigarage.segmentation.DeepLabV3XceptionSegmenter
import com.rootcrack.aigarage.segmentation.CITYSCAPES_LABELS_IN_SEGMENTER
import com.rootcrack.aigarage.utils.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    
    var hasPermission by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showObjectSelection by remember { mutableStateOf(false) }
    var selectedObjects by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isProcessing by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(true) }
    var showPreview by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // Açıklama: Kamera izni kontrolü
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (!isGranted) {
            showError = true
            errorMessage = context.getString(R.string.camera_permission_denied)
        }
    }

    // Açıklama: Kamera izni kontrolü
    LaunchedEffect(Unit) {
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) -> {
                hasPermission = true
            }
            else -> {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    // Açıklama: Maskeleme için kullanılabilir nesneler (CameraScreen içinde tanımlanıyor)
    val maskableObjectsCameraScreen = mapOf(
        "person" to context.getString(R.string.person),
        "bicycle" to context.getString(R.string.bicycle),
        "car" to context.getString(R.string.car),
        "motorcycle" to context.getString(R.string.motorcycle),
        "airplane" to context.getString(R.string.airplane),
        "bus" to context.getString(R.string.bus),
        "train" to context.getString(R.string.train),
        "truck" to context.getString(R.string.truck),
        "boat" to context.getString(R.string.boat),
        "traffic light" to context.getString(R.string.traffic_light),
        "fire hydrant" to context.getString(R.string.fire_hydrant),
        "stop sign" to context.getString(R.string.stop_sign),
        "parking meter" to context.getString(R.string.parking_meter),
        "bench" to context.getString(R.string.bench),
        "bird" to context.getString(R.string.bird),
        "cat" to context.getString(R.string.cat),
        "dog" to context.getString(R.string.dog),
        "horse" to context.getString(R.string.horse),
        "sheep" to context.getString(R.string.sheep),
        "cow" to context.getString(R.string.cow),
        "elephant" to context.getString(R.string.elephant),
        "bear" to context.getString(R.string.bear),
        "zebra" to context.getString(R.string.zebra),
        "giraffe" to context.getString(R.string.giraffe),
        "backpack" to context.getString(R.string.backpack),
        "umbrella" to context.getString(R.string.umbrella),
        "handbag" to context.getString(R.string.handbag),
        "tie" to context.getString(R.string.tie),
        "suitcase" to context.getString(R.string.suitcase),
        "frisbee" to context.getString(R.string.frisbee),
        "skis" to context.getString(R.string.skis),
        "snowboard" to context.getString(R.string.snowboard),
        "sports ball" to context.getString(R.string.sports_ball),
        "kite" to context.getString(R.string.kite),
        "baseball bat" to context.getString(R.string.baseball_bat),
        "baseball glove" to context.getString(R.string.baseball_glove),
        "skateboard" to context.getString(R.string.skateboard),
        "surfboard" to context.getString(R.string.surfboard),
        "tennis racket" to context.getString(R.string.tennis_racket),
        "bottle" to context.getString(R.string.bottle),
        "wine glass" to context.getString(R.string.wine_glass),
        "cup" to context.getString(R.string.cup),
        "fork" to context.getString(R.string.fork),
        "knife" to context.getString(R.string.knife),
        "spoon" to context.getString(R.string.spoon),
        "bowl" to context.getString(R.string.bowl),
        "banana" to context.getString(R.string.banana),
        "apple" to context.getString(R.string.apple),
        "sandwich" to context.getString(R.string.sandwich),
        "orange" to context.getString(R.string.orange),
        "broccoli" to context.getString(R.string.broccoli),
        "carrot" to context.getString(R.string.carrot),
        "hot dog" to context.getString(R.string.hot_dog),
        "pizza" to context.getString(R.string.pizza),
        "donut" to context.getString(R.string.donut),
        "cake" to context.getString(R.string.cake),
        "chair" to context.getString(R.string.chair),
        "couch" to context.getString(R.string.couch),
        "potted plant" to context.getString(R.string.potted_plant),
        "bed" to context.getString(R.string.bed),
        "dining table" to context.getString(R.string.dining_table),
        "toilet" to context.getString(R.string.toilet),
        "tv" to context.getString(R.string.tv),
        "laptop" to context.getString(R.string.laptop),
        "mouse" to context.getString(R.string.mouse),
        "remote" to context.getString(R.string.remote),
        "keyboard" to context.getString(R.string.keyboard),
        "cell phone" to context.getString(R.string.cell_phone),
        "microwave" to context.getString(R.string.microwave),
        "oven" to context.getString(R.string.oven),
        "toaster" to context.getString(R.string.toaster),
        "sink" to context.getString(R.string.sink),
        "refrigerator" to context.getString(R.string.refrigerator),
        "book" to context.getString(R.string.book),
        "clock" to context.getString(R.string.clock),
        "vase" to context.getString(R.string.vase),
        "scissors" to context.getString(R.string.scissors),
        "teddy bear" to context.getString(R.string.teddy_bear),
        "hair drier" to context.getString(R.string.hair_drier),
        "toothbrush" to context.getString(R.string.toothbrush)
    )

    // Açıklama: Özel sıralanmış nesne isimleri (CameraScreen içinde tanımlanıyor)
    val customSortedMaskableObjectDisplayNamesCamera = listOf(
        context.getString(R.string.person),
        context.getString(R.string.car),
        context.getString(R.string.dog),
        context.getString(R.string.cat),
        context.getString(R.string.bird),
        context.getString(R.string.phone),
        context.getString(R.string.laptop),
        context.getString(R.string.chair),
        context.getString(R.string.table),
        context.getString(R.string.bottle),
        context.getString(R.string.cup),
        context.getString(R.string.book),
        context.getString(R.string.bag),
        context.getString(R.string.hat),
        context.getString(R.string.shoe),
        context.getString(R.string.plant),
        context.getString(R.string.flower),
        context.getString(R.string.tree),
        context.getString(R.string.building),
        context.getString(R.string.window),
        context.getString(R.string.door),
        context.getString(R.string.car),
        context.getString(R.string.bicycle),
        context.getString(R.string.motorcycle),
        context.getString(R.string.bus),
        context.getString(R.string.truck),
        context.getString(R.string.boat),
        context.getString(R.string.airplane),
        context.getString(R.string.train),
        context.getString(R.string.traffic_light),
        context.getString(R.string.stop_sign),
        context.getString(R.string.fire_hydrant),
        context.getString(R.string.bench),
        context.getString(R.string.parking_meter),
        context.getString(R.string.horse),
        context.getString(R.string.sheep),
        context.getString(R.string.cow),
        context.getString(R.string.elephant),
        context.getString(R.string.bear),
        context.getString(R.string.zebra),
        context.getString(R.string.giraffe),
        context.getString(R.string.backpack),
        context.getString(R.string.umbrella),
        context.getString(R.string.handbag),
        context.getString(R.string.tie),
        context.getString(R.string.suitcase),
        context.getString(R.string.frisbee),
        context.getString(R.string.skis),
        context.getString(R.string.snowboard),
        context.getString(R.string.sports_ball),
        context.getString(R.string.kite),
        context.getString(R.string.baseball_bat),
        context.getString(R.string.baseball_glove),
        context.getString(R.string.skateboard),
        context.getString(R.string.surfboard),
        context.getString(R.string.tennis_racket),
        context.getString(R.string.wine_glass),
        context.getString(R.string.fork),
        context.getString(R.string.knife),
        context.getString(R.string.spoon),
        context.getString(R.string.bowl),
        context.getString(R.string.banana),
        context.getString(R.string.apple),
        context.getString(R.string.sandwich),
        context.getString(R.string.orange),
        context.getString(R.string.broccoli),
        context.getString(R.string.carrot),
        context.getString(R.string.hot_dog),
        context.getString(R.string.pizza),
        context.getString(R.string.donut),
        context.getString(R.string.cake),
        context.getString(R.string.couch),
        context.getString(R.string.potted_plant),
        context.getString(R.string.bed),
        context.getString(R.string.dining_table),
        context.getString(R.string.toilet),
        context.getString(R.string.tv),
        context.getString(R.string.mouse),
        context.getString(R.string.remote),
        context.getString(R.string.keyboard),
        context.getString(R.string.cell_phone),
        context.getString(R.string.microwave),
        context.getString(R.string.oven),
        context.getString(R.string.toaster),
        context.getString(R.string.sink),
        context.getString(R.string.refrigerator),
        context.getString(R.string.clock),
        context.getString(R.string.vase),
        context.getString(R.string.scissors),
        context.getString(R.string.teddy_bear),
        context.getString(R.string.hair_drier),
        context.getString(R.string.toothbrush)
    )

    // Açıklama: Fotoğraf çekme fonksiyonu
    fun takePhoto() {
        val imageCapture = imageCapture ?: return
        
        val photoFile = File(
            context.getExternalFilesDir(FileUtil.APP_MEDIA_SUBDIRECTORY),
            "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                    capturedImageUri = savedUri
                    showCamera = false
                    showPreview = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraScreen", "Fotoğraf çekme hatası", exc)
                    showError = true
                    errorMessage = context.getString(R.string.camera_start_failed, exc.localizedMessage)
                }
            }
        )
    }

    // Açıklama: Nesne seçimi işleme fonksiyonu
    fun processSelectedObjects() {
        if (selectedObjects.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.select_at_least_one_object), Toast.LENGTH_SHORT).show()
            return
        }

        isProcessing = true
        coroutineScope.launch {
            try {
                val segmenter = DeepLabV3XceptionSegmenter(context, "deeplabv3-xception65.tflite")
                val initialized = segmenter.initialize()
                if (!initialized) {
                    showError = true
                    errorMessage = context.getString(R.string.segmentation_failed)
                    return@launch
                }
                
                val targetObjectNames = selectedObjects.toList()
                val targetClassIndices = targetObjectNames.mapNotNull { objectName ->
                    maskableObjectsCameraScreen.entries.find { it.value == objectName }?.key?.let { key ->
                        CITYSCAPES_LABELS_IN_SEGMENTER.indexOf(key)
                    }
                }

                if (targetClassIndices.isNotEmpty() && capturedImageUri != null) {
                    // Açıklama: URI'den bitmap oluştur
                    val bitmap = withContext(Dispatchers.IO) {
                        try {
                            val inputStream = context.contentResolver.openInputStream(capturedImageUri!!)
                            android.graphics.BitmapFactory.decodeStream(inputStream)
                        } catch (e: Exception) {
                            Log.e("CameraScreen", "Bitmap oluşturma hatası", e)
                            null
                        }
                    }
                    
                    if (bitmap != null) {
                        val result = segmenter.segmentMultipleObjects(bitmap, targetClassIndices)
                        if (result.combinedMaskBitmap != null) {
                            // Açıklama: Mask'i Base64'e çevir
                            val maskBase64 = withContext(Dispatchers.IO) {
                                try {
                                    val outputStream = java.io.ByteArrayOutputStream()
                                    result.combinedMaskBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
                                    val byteArray = outputStream.toByteArray()
                                    android.util.Base64.encodeToString(byteArray, android.util.Base64.DEFAULT)
                                } catch (e: Exception) {
                                    Log.e("CameraScreen", "Base64 dönüştürme hatası", e)
                                    null
                                }
                            }
                            
                            if (maskBase64 != null) {
                                navController.navigate(
                                    Screen.AutoMaskPreview.route + "?imageUri=${capturedImageUri}&targetObjectNames=${targetObjectNames.joinToString(",")}&targetClassIndices=${targetClassIndices.joinToString(",")}"
                                )
                            } else {
                                showError = true
                                errorMessage = context.getString(R.string.mask_generation_failed)
                            }
                        } else {
                            showError = true
                            errorMessage = context.getString(R.string.mask_generation_failed)
                        }
                    } else {
                        showError = true
                        errorMessage = context.getString(R.string.image_not_loaded)
                    }
                } else {
                    showError = true
                    errorMessage = context.getString(R.string.no_valid_objects_selected)
                }
                segmenter.close()
            } catch (e: Exception) {
                Log.e("CameraScreen", "Nesne işleme hatası", e)
                showError = true
                errorMessage = context.getString(R.string.processing_error, e.localizedMessage)
            } finally {
                isProcessing = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = stringResource(R.string.camera_screen),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Gallery.route) }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = stringResource(R.string.gallery))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                        )
                    )
                )
        ) {
            when {
                !hasPermission -> {
                    // Açıklama: İzin eksik durumu
                    PermissionRequestScreen(
                        onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                    )
                }
                showError -> {
                    // Açıklama: Hata durumu
                    ErrorScreen(
                        message = errorMessage,
                        onRetry = { 
                            showError = false
                            errorMessage = ""
                        }
                    )
                }
                showPreview && capturedImageUri != null -> {
                    // Açıklama: Önizleme ekranı
                    ImagePreviewScreen(
                        imageUri = capturedImageUri!!,
                        selectedObjects = selectedObjects,
                        onObjectSelection = { showObjectSelection = true },
                        onProcess = { processSelectedObjects() },
                        onRetake = { 
                            showPreview = false
                            showCamera = true
                            capturedImageUri = null
                            selectedObjects = emptySet()
                        },
                        isProcessing = isProcessing
                    )
                }
                showCamera -> {
                    // Açıklama: Kamera ekranı
                    CameraPreviewScreen(
                        onPhotoTaken = { takePhoto() },
                        onImageCaptureReady = { imageCapture = it }
                    )
                }
            }

            // Açıklama: Nesne seçim dialog'u
            if (showObjectSelection) {
                ObjectSelectionDialog(
                    objects = customSortedMaskableObjectDisplayNamesCamera,
                    selectedObjects = selectedObjects,
                    onSelectionChanged = { selectedObjects = it },
                    onConfirm = { 
                        showObjectSelection = false
                        if (selectedObjects.isNotEmpty()) {
                            processSelectedObjects()
                        }
                    },
                    onDismiss = { showObjectSelection = false }
                )
            }
        }
    }
}

@Composable
private fun PermissionRequestScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.camera_permission_required),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.camera_permission_explanation),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequestPermission) {
            Icon(Icons.Default.CameraAlt, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.grant_permission))
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.error_occurred),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.retry))
        }
    }
}

@Composable
private fun CameraPreviewScreen(
    onPhotoTaken: () -> Unit,
    onImageCaptureReady: (ImageCapture) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build()
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()

                onImageCaptureReady(imageCapture)

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                    )
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                } catch (e: Exception) {
                    Log.e("CameraScreen", "Kamera bağlama hatası", e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    )

    // Açıklama: Fotoğraf çekme butonu
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        FloatingActionButton(
            onClick = onPhotoTaken,
            modifier = Modifier
                .padding(bottom = 32.dp)
                .size(72.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                Icons.Default.Camera,
                contentDescription = stringResource(R.string.take_photo),
                modifier = Modifier.size(32.dp),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun ImagePreviewScreen(
    imageUri: Uri,
    selectedObjects: Set<String>,
    onObjectSelection: () -> Unit,
    onProcess: () -> Unit,
    onRetake: () -> Unit,
    isProcessing: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Açıklama: Önizleme resmi
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Image(
                painter = rememberAsyncImagePainter(model = imageUri),
                contentDescription = stringResource(R.string.captured_image),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )
        }

        // Açıklama: Seçili nesneler
        if (selectedObjects.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.selected_objects),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        selectedObjects.forEach { objectName ->
                            Card(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(16.dp)
                                    ),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = objectName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Açıklama: Aksiyon butonları
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onRetake,
                modifier = Modifier.weight(1f),
                enabled = !isProcessing
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.retake))
            }

            Button(
                onClick = onObjectSelection,
                modifier = Modifier.weight(1f),
                enabled = !isProcessing
            ) {
                Icon(Icons.Default.TouchApp, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.select_objects))
            }
        }

        if (selectedObjects.isNotEmpty()) {
            Button(
                onClick = onProcess,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isProcessing
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.processing))
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.process_image))
                }
            }
        }
    }
}

@Composable
private fun ObjectSelectionDialog(
    objects: List<String>,
    selectedObjects: Set<String>,
    onSelectionChanged: (Set<String>) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.select_objects_to_mask),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                objects.forEach { objectName ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val newSelection = if (selectedObjects.contains(objectName)) {
                                    selectedObjects - objectName
                                } else {
                                    selectedObjects + objectName
                                }
                                onSelectionChanged(newSelection)
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedObjects.contains(objectName),
                            onCheckedChange = { checked ->
                                val newSelection = if (checked) {
                                    selectedObjects + objectName
                                } else {
                                    selectedObjects - objectName
                                }
                                onSelectionChanged(newSelection)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = objectName,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = selectedObjects.isNotEmpty()
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    // Açıklama: Basit flow row implementasyonu
    LazyRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                content()
            }
        }
    }
}
