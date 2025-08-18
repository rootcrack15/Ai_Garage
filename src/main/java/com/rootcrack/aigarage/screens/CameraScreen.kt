package com.rootcrack.aigarage.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.rootcrack.aigarage.navigation.NavArgs
import com.rootcrack.aigarage.navigation.Screen
import com.rootcrack.aigarage.utils.FileUtil
import com.rootcrack.aigarage.viewmodels.CameraViewModel

// Maskelenebilir nesne tanımlamaları (Constants dosyasından alınabilir)
val MASKABLE_OBJECTS_CAMERA_SCREEN = mapOf(
    "Araba" to "car",
    "Motosiklet" to "motorcycle",
    "İnsan" to "person",
    "Bisiklet" to "bicycle",
    "Yol" to "road",
    "Kaldırım" to "sidewalk",
    "Bina" to "building",
    "Duvar" to "wall",
    "Arazi" to "terrain",
    "Gökyüzü" to "sky",
    "Kamyon" to "truck",
    "Otobüs" to "bus",
    "Tren" to "train"
)
val CUSTOM_SORTED_MASKABLE_OBJECT_DISPLAY_NAMES_CAMERA = listOf(
    "İnsan", "Araba", "Motosiklet", "Bisiklet", "Kamyon", "Otobüs", "Tren",
    "Yol", "Kaldırım", "Bina", "Duvar", "Arazi", "Gökyüzü"
)

const val TAG_CAMERA_SCREEN = "CameraScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    navController: NavController,
    cameraViewModel: CameraViewModel? = null, // Kullanıyorsanız ve null olabilir
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    val permissionsArray = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
    var hasPermissions by remember { mutableStateOf(checkPermissions(context, permissionsArray)) }
    var showPermissionRationaleDialog by remember { mutableStateOf(false) }
    var showSettingsRedirectDialog by remember { mutableStateOf(false) }

    var selectedObjectToMaskKey by remember { mutableStateOf<String?>(null) }
    var selectedObjectToMaskDisplay by remember { mutableStateOf<String?>(null) }
    var showObjectSelectionDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        hasPermissions = permissionsMap.values.all { it }
        if (!hasPermissions) {
            val permanentlyDenied = permissionsArray.any { permission ->
                ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED &&
                        activity?.shouldShowRequestPermissionRationale(permission) == false
            }
            if (permanentlyDenied) showSettingsRedirectDialog = true else showPermissionRationaleDialog = true
        } else {
            showPermissionRationaleDialog = false
            showSettingsRedirectDialog = false
            if (selectedObjectToMaskKey == null) {
                showObjectSelectionDialog = true
            }
        }
    }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var previewView: PreviewView? by remember { mutableStateOf(null) } // Tip belirtildi
    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) } // Tip belirtildi
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var cameraReady by remember { mutableStateOf(false) }

    var interstitialAd: InterstitialAd? by remember { mutableStateOf(null) }
    var isAdLoading by remember { mutableStateOf(false) }

    fun loadInterstitialAd(onAdLoadedAction: (() -> Unit)? = null, onAdFailedAction: (() -> Unit)? = null) {
        if (activity == null || isAdLoading || interstitialAd != null) {
            if (interstitialAd != null) onAdLoadedAction?.invoke() else onAdFailedAction?.invoke()
            return
        }
        isAdLoading = true
        InterstitialAd.load(
            context, "ca-app-pub-3940256099942544/1033173712", // Test Ad ID
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loadedAd: InterstitialAd) {
                    interstitialAd = loadedAd
                    isAdLoading = false; onAdLoadedAction?.invoke()
                }
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null; isAdLoading = false; onAdFailedAction?.invoke()
                }
            }
        )
    }

    fun showInterstitialAd(onAdDismissedOrFailed: () -> Unit) {
        if (activity == null) { onAdDismissedOrFailed(); return }
        interstitialAd?.let { ad ->
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null; loadInterstitialAd(); onAdDismissedOrFailed()
                }
                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null; loadInterstitialAd(); onAdDismissedOrFailed()
                }
            }
            ad.show(activity)
        } ?: run {
            loadInterstitialAd(onAdLoadedAction = { showInterstitialAd(onAdDismissedOrFailed) }, onAdFailedAction = onAdDismissedOrFailed)
        }
    }

    LaunchedEffect(Unit, activity) {
        if (activity != null) {
            if (!hasPermissions) {
                permissionLauncher.launch(permissionsArray)
            } else if (selectedObjectToMaskKey == null) {
                showObjectSelectionDialog = true
            }
        }
        loadInterstitialAd()
    }

    LaunchedEffect(hasPermissions, cameraProviderFuture, previewView, lensFacing, lifecycleOwner, selectedObjectToMaskKey) {
        if (!hasPermissions || previewView == null || selectedObjectToMaskKey == null) {
            cameraReady = false
            return@LaunchedEffect
        }
        cameraReady = false
        try {
            val camProviderInstance = cameraProviderFuture.get()
            cameraProvider = camProviderInstance
            val preview = Preview.Builder()
                .setTargetRotation(previewView!!.display.rotation)
                .build().also { it.setSurfaceProvider(previewView!!.surfaceProvider) }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetRotation(previewView!!.display.rotation).build()

            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            camProviderInstance.unbindAll()
            camProviderInstance.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
            cameraReady = true
        } catch (e: Exception) {
            Log.e(TAG_CAMERA_SCREEN, "Kamera başlatılamadı: ${e.message}", e)
            Toast.makeText(context, "Kamera açılamadı: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            cameraReady = false
        }
    }

    fun takePhoto() {
        if (!hasPermissions) {
            permissionLauncher.launch(permissionsArray)
            return
        }
        if (selectedObjectToMaskKey == null) {
            Toast.makeText(context, "Lütfen önce maskelenecek bir nesne seçin.", Toast.LENGTH_SHORT).show()
            showObjectSelectionDialog = true
            return
        }
        val localImageCapture = imageCapture
        if (localImageCapture == null || !cameraReady) {
            Toast.makeText(context, "Kamera hazır değil veya nesne seçilmedi.", Toast.LENGTH_SHORT).show()
            return
        }

        val outputOptions = FileUtil.getPhotoOutputFileOptions(context)
        if (outputOptions == null) {
            Toast.makeText(context, "Dosya oluşturulamadı.", Toast.LENGTH_SHORT).show(); return
        }

        localImageCapture.takePicture(
            outputOptions, ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = outputFileResults.savedUri
                    if (savedUri != null) {
                        showInterstitialAd {
                            val encodedImageUri = Uri.encode(savedUri.toString())
                            val encodedObjectToMask = Uri.encode(selectedObjectToMaskKey!!)

                            val route = Screen.AutoMaskPreview.route
                                .replace("{${NavArgs.IMAGE_URI}}", encodedImageUri)
                                .replace("{${NavArgs.OBJECT_TO_MASK}}", encodedObjectToMask)

                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    } else {
                        Toast.makeText(context, "Fotoğraf kaydedilemedi.", Toast.LENGTH_LONG).show()
                    }
                }
                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(context, "Fotoğraf çekilemedi: ${exception.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    DisposableEffect(lifecycleOwner) {
        onDispose { cameraProvider?.unbindAll() }
    }

    Scaffold(
        topBar = {
            if (hasPermissions) {
                TopAppBar(
                    title = { Text(selectedObjectToMaskDisplay ?: "Nesne Seçin") },
                    actions = {
                        IconButton(onClick = { showObjectSelectionDialog = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Maskelenecek Nesneyi Değiştir")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (hasPermissions) {
                if (selectedObjectToMaskKey != null) {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                this.scaleType = PreviewView.ScaleType.FILL_CENTER
                                previewView = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (!cameraReady) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { if (cameraReady) takePhoto() },
                            modifier = Modifier.size(72.dp),
                            enabled = cameraReady
                        ) {
                            Icon(
                                Icons.Filled.PhotoCamera,
                                contentDescription = "Fotoğraf Çek",
                                tint = if (cameraReady) MaterialTheme.colorScheme.primary else Color.Gray,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        IconButton(
                            onClick = {
                                if (cameraReady) {
                                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .align(Alignment.CenterVertically),
                            enabled = cameraReady && hasFrontAndBackCamera(cameraProvider)
                        ) {
                            Icon(
                                Icons.Filled.Cameraswitch,
                                contentDescription = "Kamera Değiştir",
                                tint = if (cameraReady && hasFrontAndBackCamera(cameraProvider)) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                    }
                } else if (!showObjectSelectionDialog) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Maskelemek için bir nesne seçin.", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { showObjectSelectionDialog = true }) {
                            Text("Nesne Seç")
                        }
                    }
                }
            } else {
                PermissionRequestUI( // Bu Composable'ı tanımlamanız veya import etmeniz gerekecek
                    onGrantPermissionClick = {
                        if (showSettingsRedirectDialog) {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            intent.data = Uri.fromParts("package", context.packageName, null)
                            context.startActivity(intent)
                        } else {
                            permissionLauncher.launch(permissionsArray)
                        }
                    },
                    isPermanentlyDenied = showSettingsRedirectDialog
                )
            }

            if (showObjectSelectionDialog && hasPermissions) {
                ObjectSelectionDialog( // Bu Composable'ı tanımlamanız veya import etmeniz gerekecek
                    onDismissRequest = {
                        showObjectSelectionDialog = false
                        if(selectedObjectToMaskKey == null) {
                            Toast.makeText(context, "Maskeleme için nesne seçimi iptal edildi.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onObjectSelected = { displayValue, apiKey ->
                        selectedObjectToMaskDisplay = displayValue
                        selectedObjectToMaskKey = apiKey
                        showObjectSelectionDialog = false
                    },
                    // Bu parametreler ObjectSelectionDialog'unuzun beklediği şekilde olmalı
                    maskableObjects = MASKABLE_OBJECTS_CAMERA_SCREEN,
                    sortedDisplayNames = CUSTOM_SORTED_MASKABLE_OBJECT_DISPLAY_NAMES_CAMERA
                )
            }

            // İzin Gerekçesi Dialogu
            if (showPermissionRationaleDialog) {
                AlertDialog(
                    onDismissRequest = { showPermissionRationaleDialog = false },
                    title = { Text("İzin Gerekli") },
                    text = { Text("Bu uygulamanın düzgün çalışması için kamera ve gerekirse depolama iznine ihtiyacı var. Lütfen izinleri verin.") },
                    confirmButton = {
                        Button(onClick = {
                            permissionLauncher.launch(permissionsArray)
                            showPermissionRationaleDialog = false
                        }) { Text("İzin Ver") }
                    },
                    dismissButton = {
                        Button(onClick = { showPermissionRationaleDialog = false }) { Text("Reddet") }
                    }
                )
            }

            // Ayarlara Yönlendirme Dialogu
            if (showSettingsRedirectDialog) {
                AlertDialog(
                    onDismissRequest = { showSettingsRedirectDialog = false },
                    title = { Text("İzin Reddedildi") },
                    text = { Text("Kamera iznini kalıcı olarak reddettiniz. Uygulamayı kullanmak için lütfen uygulama ayarlarından izinleri etkinleştirin.") },
                    confirmButton = {
                        Button(onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            val uri = Uri.fromParts("package", context.packageName, null)
                            intent.data = uri
                            context.startActivity(intent)
                            showSettingsRedirectDialog = false
                        }) { Text("Ayarları Aç") }
                    },
                    dismissButton = {
                        Button(onClick = { showSettingsRedirectDialog = false }) { Text("İptal") }
                    }
                )
            }
        }
    }
}

// Yardımcı Fonksiyonlar ve Composable'lar (Dosyanın sonunda veya ayrı bir dosyada olabilir)

fun checkPermissions(context: Context, permissions: Array<String>): Boolean {
    return permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

fun hasFrontAndBackCamera(cameraProvider: ProcessCameraProvider?): Boolean {
    if (cameraProvider == null) return false
    var hasBack = false
    var hasFront = false
    try {
        hasBack = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
    } catch (e: Exception) { /* Hata yönetimi */ }
    try {
        hasFront = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
    } catch (e: Exception) { /* Hata yönetimi */ }
    return hasBack && hasFront
}

@Composable
fun PermissionRequestUI(
    onGrantPermissionClick: () -> Unit,
    isPermanentlyDenied: Boolean,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (isPermanentlyDenied) "İzinler kalıcı olarak reddedildi."
            else "Kamera ve depolama izni gereklidir.",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (isPermanentlyDenied) "Lütfen uygulama ayarlarından izinleri manuel olarak etkinleştirin."
            else "Lütfen uygulamayı kullanabilmek için izinleri verin."
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onGrantPermissionClick) {
            Text(if (isPermanentlyDenied) "Ayarları Aç" else "İzinleri Ver")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObjectSelectionDialog(
    onDismissRequest: () -> Unit,
    onObjectSelected: (displayName: String, apiKey: String) -> Unit,
    maskableObjects: Map<String, String>, // Parametre eklendi
    sortedDisplayNames: List<String>,      // Parametre eklendi
) {
    var tempSelectedKey by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .selectableGroup()
            ) {
                Text("Maskelenecek Nesneyi Seçin", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))

                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) { // Yüksekliği sınırla
                    items(sortedDisplayNames) { displayName ->
                        val apiKey = maskableObjects[displayName]
                        if (apiKey != null) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = (apiKey == tempSelectedKey),
                                        onClick = { tempSelectedKey = apiKey },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (apiKey == tempSelectedKey),
                                    onClick = null // Row'un selectable'ı tarafından yönetiliyor
                                )
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text("İptal")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            tempSelectedKey?.let { key ->
                                val displayName = maskableObjects.entries.find { it.value == key }?.key
                                if (displayName != null) {
                                    onObjectSelected(displayName, key)
                                }
                            }
                            // onDismissRequest() // Seçim yapıldıktan sonra dialog kapatılır.
                        },
                        enabled = tempSelectedKey != null
                    ) {
                        Text("Seç")
                    }
                }
            }
        }
    }
}
