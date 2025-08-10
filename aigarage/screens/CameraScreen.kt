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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.rootcrack.aigarage.components.PhotoConfirmationDialog
import com.rootcrack.aigarage.utils.FileUtil
import com.rootcrack.aigarage.viewmodels.CameraViewModel

// CameraViewModel ve CameraScreenStateHolder'ı kullanıyorsanız import edin
// import com.rootcrack.aigarage.viewmodels.CameraViewModel
// import com.rootcrack.aigarage.screens.rememberCameraScreenStateHolder

const val TAG_CAMERA_SCREEN = "CameraScreen"

@Composable
fun CameraScreen(
    navController: NavController,
     cameraViewModel: CameraViewModel, // Kullanıyorsanız
    // stateHolder: CameraScreenStateHolder = rememberCameraScreenStateHolder(), // Kullanıyorsanız
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    // İzinler: API 29+ için sadece CAMERA, API < 29 için depolama da gerekli olabilir (MediaStore kullanımı ve Scoped Storage'a bağlı)
    // Sadece MediaStore'a özel dizine yazıyorsanız (AIGarage gibi), WRITE_EXTERNAL_STORAGE API < 29'da bile gerekmeyebilir.
    // Ancak MediaStore'a doğrudan yazmak için (IS_PENDING olmadan veya eski yöntemlerle) gerekebilir.
    // Şimdilik, eski sürümler için depolama iznini tutalım, FileUtil'inize göre optimize edebilirsiniz.
    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(Manifest.permission.CAMERA)
        } else {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE // MediaStore'a güvenli yazma için gerekli olabilir
                // Manifest.permission.READ_EXTERNAL_STORAGE // Sadece galeriden okuma varsa
            )
        }
    }
    var hasPermissions by remember { mutableStateOf(checkPermissions(context, permissions)) }
    var showPermissionRationaleDialog by remember { mutableStateOf(false) }
    var showSettingsRedirectDialog by remember { mutableStateOf(false) }

    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showPhotoConfirmationDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        hasPermissions = permissionsMap.values.all { it }
        Log.d(TAG_CAMERA_SCREEN, "İzin sonucu: $permissionsMap, Has Permissions: $hasPermissions")
        if (!hasPermissions) {
            val permanentlyDenied = permissions.any { permission ->
                ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED &&
                        activity?.shouldShowRequestPermissionRationale(permission) == false
            }
            if (permanentlyDenied) {
                Log.d(TAG_CAMERA_SCREEN, "İzinler kalıcı olarak reddedildi, ayarlar dialogu gösteriliyor.")
                showSettingsRedirectDialog = true
            } else {
                Log.d(TAG_CAMERA_SCREEN, "İzinler reddedildi, açıklama dialogu gösteriliyor.")
                showPermissionRationaleDialog = true
            }
        } else {
            showPermissionRationaleDialog = false
            showSettingsRedirectDialog = false
        }
    }

    // Kamera ile ilgili state'ler
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var previewView: PreviewView? by remember { mutableStateOf(null) }
    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var cameraReady by remember { mutableStateOf(false) }

    // Reklam state'leri
    var interstitialAd: InterstitialAd? by remember { mutableStateOf(null) }
    var isAdLoading by remember { mutableStateOf(false) }

    fun loadInterstitialAd(onAdLoadedAction: (() -> Unit)? = null, onAdFailedAction: (() -> Unit)? = null) {
        if (activity == null || isAdLoading || interstitialAd != null) {
            if (interstitialAd != null) onAdLoadedAction?.invoke() else onAdFailedAction?.invoke()
            return
        }
        isAdLoading = true
        Log.d(TAG_CAMERA_SCREEN, "Interstitial Ad Yükleniyor...")
        InterstitialAd.load(
            context,
            "ca-app-pub-3940256099942544/1033173712", // Test Ad ID
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loadedAd: InterstitialAd) {
                    interstitialAd = loadedAd
                    isAdLoading = false
                    Log.i(TAG_CAMERA_SCREEN, "Interstitial Ad Yüklendi.")
                    onAdLoadedAction?.invoke()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e(TAG_CAMERA_SCREEN, "Ad yüklenemedi: ${loadAdError.message}")
                    interstitialAd = null
                    isAdLoading = false
                    onAdFailedAction?.invoke()
                }
            }
        )
    }

    fun showInterstitialAd(onAdDismissedOrFailed: () -> Unit) {
        if (activity == null) {
            onAdDismissedOrFailed()
            return
        }
        interstitialAd?.let { ad ->
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG_CAMERA_SCREEN, "Ad kapatıldı.")
                    interstitialAd = null // Reklamı bir kere gösterdikten sonra null yap
                    loadInterstitialAd() // Yenisini yükle
                    onAdDismissedOrFailed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e(TAG_CAMERA_SCREEN, "Ad gösterilemedi: ${adError.message}")
                    interstitialAd = null
                    loadInterstitialAd()
                    onAdDismissedOrFailed()
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG_CAMERA_SCREEN, "Ad gösterildi.")
                }
            }
            ad.show(activity)
        } ?: run {
            Log.d(TAG_CAMERA_SCREEN, "Ad henüz yüklenmemiş, direkt devam ediliyor.")
            loadInterstitialAd(onAdLoadedAction = { showInterstitialAd(onAdDismissedOrFailed) }, onAdFailedAction = onAdDismissedOrFailed)
        }
    }

    // İlk açılışta ve izinler değiştiğinde izinleri kontrol et/iste
    LaunchedEffect(Unit, activity) { // activity'i de key olarak ekleyebiliriz, null olma durumunu yönetmek için
        if (activity != null && !hasPermissions) {
            permissionLauncher.launch(permissions)
        }
        loadInterstitialAd() // Reklamı yüklemeye başla
    }

    // Kamera bağlama/güncelleme efekti
    LaunchedEffect(hasPermissions, cameraProviderFuture, previewView, lensFacing, lifecycleOwner) {
        if (!hasPermissions || previewView == null) {
            cameraReady = false
            return@LaunchedEffect
        }
        Log.d(TAG_CAMERA_SCREEN, "Kamera başlatma etkisi tetiklendi. HasPermissions: $hasPermissions, LensFacing: $lensFacing")
        cameraReady = false
        try {
            val CamProvider = cameraProviderFuture.get() // Blocking call, IO thread'de olmalıydı ama LaunchedEffect coroutine'de
            cameraProvider = CamProvider
            val preview = Preview.Builder()
                .setTargetRotation(previewView!!.display.rotation) // Null olamaz çünkü yukarıda kontrol edildi
                .build()
                .also { it.setSurfaceProvider(previewView!!.surfaceProvider) }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetRotation(previewView!!.display.rotation)
                .build()

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            CamProvider.unbindAll() // Önceki bağlantıları kaldır
            CamProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
            cameraReady = true
            Log.i(TAG_CAMERA_SCREEN, "Kamera başarıyla bağlandı. Lens: $lensFacing")
        } catch (e: Exception) {
            Log.e(TAG_CAMERA_SCREEN, "Kamera başlatılamadı: ${e.message}", e)
            Toast.makeText(context, "Kamera açılamadı: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            cameraReady = false
        }
    }


    fun takePhoto() {
        if (!hasPermissions) {
            permissionLauncher.launch(permissions)
            return
        }
        val localImageCapture = imageCapture
        if (localImageCapture == null || !cameraReady) {
            Toast.makeText(context, "Kamera hazır değil, lütfen bekleyin.", Toast.LENGTH_SHORT).show()
            Log.e(TAG_CAMERA_SCREEN, "imageCapture null veya kamera hazır değil, fotoğraf çekilemedi.")
            return
        }

        val outputOptions = FileUtil.getPhotoOutputFileOptions(context)
        if (outputOptions == null) {
            Toast.makeText(context, "Dosya oluşturulamadı, depolama hatası.", Toast.LENGTH_SHORT).show()
            Log.e(TAG_CAMERA_SCREEN, "outputOptions null, fotoğraf çekilemedi.")
            return
        }
        Log.d(TAG_CAMERA_SCREEN, "Fotoğraf çekiliyor...")

        localImageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = outputFileResults.savedUri
                    if (savedUri != null) {
                        Log.d(TAG_CAMERA_SCREEN, "Fotoğraf MediaStore'a kaydedildi: $savedUri")
                        // Reklamı göster, sonra onay dialoğuna git
                        showInterstitialAd {
                            capturedImageUri = savedUri
                            showPhotoConfirmationDialog = true
                        }
                    } else {
                        Log.e(TAG_CAMERA_SCREEN, "Fotoğraf kaydedildi ama MediaStore URI null.")
                        Toast.makeText(context, "Fotoğraf kaydedilemedi: URI null.", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG_CAMERA_SCREEN, "Fotoğraf çekme hatası: ${exception.message}", exception)
                    Toast.makeText(context, "Fotoğraf çekilemedi: ${exception.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            Log.d(TAG_CAMERA_SCREEN, "CameraScreen dispose ediliyor, kamera bağlantıları kaldırılıyor.")
            cameraProvider?.unbindAll() // Kamera kaynaklarını serbest bırak
        }
    }


    Scaffold(
        modifier = Modifier.fillMaxSize(),
        // TopBar veya BottomBar eklenebilir
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (hasPermissions) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            this.scaleType = PreviewView.ScaleType.FILL_CENTER
                            previewView = this // previewView state'ini güncelle
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (!cameraReady && hasPermissions) { // Kamera yüklenirken gösterge
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                // Kamera Kontrol Butonları
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Boşluk veya başka bir buton eklenebilir
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
                                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                    CameraSelector.LENS_FACING_FRONT
                                } else {
                                    CameraSelector.LENS_FACING_BACK
                                }
                                Log.d(TAG_CAMERA_SCREEN, "Kamera değiştiriliyor: Yeni lensFacing = $lensFacing")
                            }
                        },
                        modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                        enabled = cameraReady && (hasFrontAndBackCamera(cameraProvider)) // Sadece ön ve arka kamera varsa etkinleştir
                    ) {
                        Icon(
                            Icons.Filled.Cameraswitch,
                            contentDescription = "Kamera Değiştir",
                            tint = if (cameraReady && hasFrontAndBackCamera(cameraProvider)) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                }

            } else { // İzinler verilmediyse gösterilecek ekran
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Uygulamanın çalışması için kamera ve depolama (eski sürümler için) izinlerine ihtiyaç var.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { permissionLauncher.launch(permissions) }) {
                        Text("İzinleri İste")
                    }
                }
            }

            // İzin Açıklama Dialogu
            if (showPermissionRationaleDialog) {
                PermissionRationaleDialog(
                    onConfirm = {
                        showPermissionRationaleDialog = false
                        permissionLauncher.launch(permissions)
                    },
                    onDismiss = { showPermissionRationaleDialog = false }
                )
            }

            // Ayarlara Yönlendirme Dialogu
            if (showSettingsRedirectDialog) {
                SettingsRedirectDialog(
                    onConfirm = {
                        showSettingsRedirectDialog = false
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        val uri = Uri.fromParts("package", context.packageName, null)
                        intent.data = uri
                        context.startActivity(intent)
                    },
                    onDismiss = { showSettingsRedirectDialog = false }
                )
            }

            // Fotoğraf Onay Dialogu
            if (showPhotoConfirmationDialog && capturedImageUri != null) {
                PhotoConfirmationDialog(
                    imageUri = capturedImageUri!!,
                    navController = navController,
                    onDismissRequest = {
                        showPhotoConfirmationDialog = false
                        capturedImageUri = null // URI'yi temizle
                    },
                    onRetakePhoto = {
                        showPhotoConfirmationDialog = false
                        capturedImageUri = null
                        // Kamera ekranında kal, yeni fotoğraf çekmeye hazır
                    },
                    onSaveAndGoToGallery = {
                        showPhotoConfirmationDialog = false
                        // Burada FileUtil veya MediaStore ile kalıcı kaydetme ve galeri yenileme işlemi yapılabilir.
                        // Şimdilik sadece galeriyi açtığımızı varsayalım veya bir mesaj gösterelim.
                        Toast.makeText(context, "Fotoğraf galeride (simüle edildi).", Toast.LENGTH_SHORT).show()
                        capturedImageUri?.let { uri ->
                            // MediaStore'a IS_PENDING = 0 ile güncelleme (FileUtil'de yapıldıysa tekrar gerekmez)
                            FileUtil.makeFileVisibleInGallery(context, uri)
                        }
                        navController.popBackStack() // Kamera ekranından çık veya galeriye git
                        // navController.navigate(Screen.Gallery.route) // Galeriniz varsa
                        capturedImageUri = null
                    }
                )
            }
        }
    }
}

// Yardımcı Fonksiyonlar ve Dialoglar

fun checkPermissions(context: Context, permissions: Array<String>): Boolean {
    return permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

fun hasFrontAndBackCamera(cameraProvider: ProcessCameraProvider?): Boolean {
    return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) == true &&
            cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) == true
}


@Composable
fun PermissionRationaleDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("İzin Gerekli") },
        text = { Text("Bu özellik için kamera (ve eski sürümlerde depolama) izinlerine ihtiyacımız var. Lütfen izinleri onaylayın.") },
        confirmButton = { Button(onClick = onConfirm) { Text("İzin Ver") } },
        dismissButton = { Button(onClick = onDismiss) { Text("İptal") } }
    )
}

@Composable
fun SettingsRedirectDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("İzin Gerekli") },
        text = { Text("Kamera (ve eski sürümlerde depolama) izinleri daha önce reddedildi. Özelliği kullanmak için lütfen uygulama ayarlarından izinleri manuel olarak etkinleştirin.") },
        confirmButton = { Button(onClick = onConfirm) { Text("Ayarlara Git") } },
        dismissButton = { Button(onClick = onDismiss) { Text("İptal") } }
    )
}
