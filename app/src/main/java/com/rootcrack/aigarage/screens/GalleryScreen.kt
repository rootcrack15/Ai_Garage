// Dosya: app/src/main/java/com/rootcrack/aigarage/screens/GalleryScreen.kt
// Açıklama: Cihaz galerisini gösteren ve AI işlemi için resim seçimi sağlayan ana ekran
// Bağlantılı dosyalar: AutoMaskPreviewScreen.kt, EditPhotoScreenWithPrompt.kt, Navigations.kt
package com.rootcrack.aigarage.screens

import android.net.Uri
import android.os.Environment
import android.util.Log
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip

import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.rootcrack.aigarage.R

// Açıklama: Ana galeri ekranı composable'ı
// Bu ekran cihaz galerisini gösterir ve AI işlemi için resim seçimi sağlar
@Composable
fun GalleryScreen(navController: NavHostController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Açıklama: String resource'ları context'ten al
    val deviceGalleryText = context.getString(R.string.device_gallery)
    val galleryPermissionRequiredText = context.getString(R.string.gallery_permission_required)
    val galleryPermissionDescriptionText = context.getString(R.string.gallery_permission_description)
    val grantPermissionText = context.getString(R.string.grant_permission)
    val noImagesInGalleryText = context.getString(R.string.no_images_in_gallery)
    val noImagesFoundText = context.getString(R.string.no_images_found)
    val selectImageText = context.getString(R.string.select_image)
    val tapImageForPreviewText = context.getString(R.string.tap_image_for_preview)
    val allFoldersText = context.getString(R.string.all_folders)
    val folderSelectText = context.getString(R.string.folder_select)
    val aiButtonText = context.getString(R.string.ai_button)
    val itemsSelectedText = context.getString(R.string.items_selected)
    val navigationErrorText = context.getString(R.string.navigation_error)
    val selectFolderText = context.getString(R.string.select_folder)
    val selectFolderDescriptionText = context.getString(R.string.select_folder_description)
    val cancelText = context.getString(R.string.cancel)
    val selectText = context.getString(R.string.select)
    val aiMaskingModeText = context.getString(R.string.ai_masking_mode)
    val selectMaskingQualityText = context.getString(R.string.select_masking_quality)
    val fastMaskingText = context.getString(R.string.fast_masking)
    val fastMaskingDescriptionText = context.getString(R.string.fast_masking_description)
    val highQualityText = context.getString(R.string.high_quality)
    val highQualityDescriptionText = context.getString(R.string.high_quality_description)
    val continueButtonText = context.getString(R.string.continue_button)
    val selectObjectsToMaskText = context.getString(R.string.select_objects_to_mask)
    val objectsSelectedText = context.getString(R.string.objects_selected)
    val selectAllText = context.getString(R.string.select_all)
    val clearText = context.getString(R.string.clear)
    val maskText = context.getString(R.string.mask)

    // Açıklama: AI maskeleme için kullanılabilir nesne türleri ve API karşılıkları
    // Bu liste AutoMaskPreviewScreen.kt'de de kullanılıyor
    val maskableObjectsGallery = mapOf(
        context.getString(R.string.car) to "car",           // Araç maskeleme için
        context.getString(R.string.motorcycle) to "motorcycle", // İki tekerlekli araç maskeleme
        context.getString(R.string.person) to "person",        // İnsan maskeleme (en çok kullanılan)
        context.getString(R.string.bicycle) to "bicycle",    // Bisiklet maskeleme
        context.getString(R.string.road) to "road",           // Yol maskeleme
        context.getString(R.string.sidewalk) to "sidewalk",  // Kaldırım maskeleme
        context.getString(R.string.building) to "building",      // Bina maskeleme
        context.getString(R.string.wall) to "wall",         // Duvar maskeleme
        context.getString(R.string.terrain) to "terrain",      // Arazi maskeleme
        context.getString(R.string.sky) to "sky",        // Gökyüzü maskeleme
        context.getString(R.string.truck) to "truck",       // Kamyon maskeleme
        context.getString(R.string.bus) to "bus",         // Otobüs maskeleme
        context.getString(R.string.train) to "train"          // Tren maskeleme
    )

    // Açıklama: Kullanıcı arayüzünde gösterilecek nesne sıralaması
    // En çok kullanılan nesneler üstte
    val sortedMaskableObjectsGallery = listOf(
        context.getString(R.string.person), 
        context.getString(R.string.car), 
        context.getString(R.string.motorcycle), 
        context.getString(R.string.bicycle), 
        context.getString(R.string.truck), 
        context.getString(R.string.bus), 
        context.getString(R.string.train),
        context.getString(R.string.road), 
        context.getString(R.string.sidewalk), 
        context.getString(R.string.building), 
        context.getString(R.string.wall), 
        context.getString(R.string.terrain), 
        context.getString(R.string.sky)
    )

    // Açıklama: Cihaz galerisinde gösterilecek klasör seçenekleri
    // Her klasör MediaStore sorgusu için kullanılıyor
    val folderOptions = listOf(
        // "Tüm Resimler" to null,  // Tüm resimleri göster (şu an devre dışı)
        context.getString(R.string.camera_folder) to "Camera",       // Kamera ile çekilen resimler
        context.getString(R.string.screenshots_folder) to "Screenshots", // Ekran görüntüleri
        context.getString(R.string.downloads_folder) to "Download",  // İndirilen resimler
        context.getString(R.string.pictures_folder) to "Pictures",   // Pictures klasörü
        context.getString(R.string.dcim_folder) to "DCIM"           // Dijital kamera resimleri
    )

    // Açıklama: Cihaz galerisi için state yönetimi
    val deviceImages = remember { mutableStateListOf<Uri>() }        // Şu anda gösterilen resimler (lazy loading için)
    val allImages = remember { mutableStateListOf<Uri>() }           // Tüm resimler (tam liste)
    var selectedImageIndex by remember { mutableStateOf(0) }         // Seçili resmin indeksi
    var isLoading by remember { mutableStateOf(false) }              // Yükleme durumu
    var hasPermission by remember { mutableStateOf(false) }          // Galeri izni durumu
    var selectedFolder by remember { mutableStateOf<String?>("Camera") } // Seçili klasör (varsayılan: Camera)
    var showFolderDialog by remember { mutableStateOf(false) }       // Klasör seçim dialog'u gösterimi
    var showPreview by remember { mutableStateOf(false) }            // Resim önizleme gösterimi
    val itemsPerPage = 20 // Açıklama: Lazy loading için sayfa başına resim sayısı

    // Açıklama: Çoklu seçim ve silme işlemleri için state'ler
    var isMultiSelectMode by remember { mutableStateOf(false) }      // Çoklu seçim modu
    val selectedImages = remember { mutableStateListOf<Uri>() }      // Çoklu seçilen resimler
    var showDeleteDialog by remember { mutableStateOf(false) }       // Silme onay dialog'u
    
    // Açıklama: AI maskeleme işlemi için state'ler
    var showModelSelectionDialog by remember { mutableStateOf(false) } // Model seçim dialog'u
    var showMaskSelectionDialog by remember { mutableStateOf(false) }   // Nesne seçim dialog'u
    val selectedMaskObjects = remember { mutableStateListOf<String>() } // Seçilen maskeleme nesneleri
    var selectedModelPath by remember { mutableStateOf<String?>(null) } // Seçilen AI modeli
    var currentSelectedImage by remember { mutableStateOf<Uri?>(null) } // AI işlemi için seçilen resim
    
    // Açıklama: Android izin kontrolü - Android 13+ için READ_MEDIA_IMAGES, eski sürümler için READ_EXTERNAL_STORAGE
    fun checkPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    // Açıklama: İzin isteme launcher'ı - kullanıcı izin verdiğinde hasPermission state'ini günceller
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        // Açıklama: İzin verildiğinde hasPermission state'i değişecek ve LaunchedEffect tetiklenecek
    }
    
    // Açıklama: Cihaz galerisinden resimleri yükleyen ana fonksiyon
    // MediaStore API kullanarak cihazdaki resimleri sorgular
    fun loadDeviceImages() {
        if (!checkPermission()) {
            Log.w("GalleryScreen", "Galeri izni yok, izin isteniyor")
            hasPermission = false
            // Açıklama: İzin isteme işlemini UI'da handle edeceğiz
            return
        }
        
        isLoading = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                Log.d("GalleryScreen", "Cihaz galerisi yükleniyor... Klasör: $selectedFolder")
                
                // Açıklama: MediaStore sorgusu için gerekli alanlar
                val projection = arrayOf(
                    android.provider.MediaStore.Images.Media._ID,        // Resim ID'si
                    android.provider.MediaStore.Images.Media.DISPLAY_NAME, // Dosya adı
                    android.provider.MediaStore.Images.Media.DATE_ADDED,   // Eklenme tarihi
                    android.provider.MediaStore.Images.Media.MIME_TYPE,    // Dosya türü
                    android.provider.MediaStore.Images.Media.DATA          // Dosya yolu (klasör filtresi için)
                )
                
                val sortOrder = "${android.provider.MediaStore.Images.Media.DATE_ADDED} DESC" // En yeni resimler önce
                
                // Açıklama: Klasör filtresi oluştur - seçili klasöre göre resimleri filtrele
                val selection = if (selectedFolder != null) {
                    "${android.provider.MediaStore.Images.Media.DATA} LIKE '%/$selectedFolder/%'"
                } else {
                    "${android.provider.MediaStore.Images.Media.MIME_TYPE} LIKE 'image/%'"
                }
                
                // Açıklama: MediaStore sorgusu - cihazdaki resimleri al
                val uris = mutableListOf<Uri>()
                val mainUri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                Log.d("GalleryScreen", "Ana galeri URI: $mainUri")
                
                val cursor = context.contentResolver.query(
                    mainUri,
                    projection,
                    selection,
                    null,
                    sortOrder
                )
                
                // Açıklama: Sorgu sonuçlarını işle ve URI'lara dönüştür
                cursor?.use { c ->
                    val idColumn = c.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID)
                    val nameColumn = c.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DISPLAY_NAME)
                    val mimeColumn = c.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.MIME_TYPE)
                    val dataColumn = c.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATA)
                    
                    while (c.moveToNext()) { // Açıklama: Tüm resimleri al
                        val id = c.getLong(idColumn)
                        val name = c.getString(nameColumn)
                        val mimeType = c.getString(mimeColumn)
                        val data = c.getString(dataColumn)
                        
                        val contentUri = android.content.ContentUris.withAppendedId(mainUri, id)
                        uris.add(contentUri)
                        
                        Log.d("GalleryScreen", "Resim bulundu: $name ($mimeType) - $data")
                    }
                }
                
                Log.d("GalleryScreen", "Toplam ${uris.size} resim bulundu")
                
                // Açıklama: UI thread'de state'leri güncelle
                withContext(Dispatchers.Main) {
                    allImages.clear()
                    allImages.addAll(uris)
                    deviceImages.clear()
                    deviceImages.addAll(uris.take(itemsPerPage)) // İlk sayfayı yükle (lazy loading)
                    hasPermission = true
                    
                    // Açıklama: Seçili resimleri temizle eğer silinmişse
                    selectedImages.removeAll { !uris.contains(it) }
                    if (selectedImageIndex >= uris.size) {
                        selectedImageIndex = maxOf(0, uris.size - 1)
                    }
                    isLoading = false
                    
                    Log.d("GalleryScreen", "İlk yükleme tamamlandı: ${deviceImages.size}/${allImages.size} resim")
                }
            } catch (e: Exception) {
                Log.e("GalleryScreen", "Cihaz galerisi yükleme hatası", e)
                withContext(Dispatchers.Main) {
                    isLoading = false
                    hasPermission = false
                }
            }
        }
    }
    
    // Açıklama: Lazy loading fonksiyonu - kullanıcı kaydırdıkça yeni resimler yükler
    // Performans için sadece görünür resimleri yükler
    fun loadMoreImages() {
        val currentSize = deviceImages.size
        val totalSize = allImages.size
        
        if (currentSize >= totalSize) {
            Log.d("GalleryScreen", "Tüm resimler zaten yüklendi")
            return
        }
        
        val nextBatch = allImages.drop(currentSize).take(itemsPerPage)
        if (nextBatch.isNotEmpty()) {
            deviceImages.addAll(nextBatch)
            Log.d("GalleryScreen", "Yeni ${nextBatch.size} resim yüklendi. Toplam: ${deviceImages.size}/${totalSize}")
        }
    }
    
    // Açıklama: Resim seçildiğinde önizlemeyi gösteren fonksiyon
    // Kullanıcı resme tıkladığında çağrılır
    fun selectImage(index: Int) {
        selectedImageIndex = index
        showPreview = true
        Log.d("GalleryScreen", "Resim seçildi: $index")
    }

    // Açıklama: Resim seçici launcher (artık kullanılmıyor, cihaz galerisi direkt gösteriliyor)
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        // Açıklama: Bu artık kullanılmıyor, cihaz galerisi direkt gösteriliyor
    }
    
    // Açıklama: Ekran ilk açıldığında resimleri yükle
    LaunchedEffect(Unit) {
        loadDeviceImages()
    }
    
    // Açıklama: İzin verildiğinde resimleri yükle
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            loadDeviceImages()
        }
    }
    
    // Açıklama: Klasör değiştiğinde resimleri yeniden yükle
    LaunchedEffect(selectedFolder) {
        if (hasPermission) {
            loadDeviceImages()
        }
    }
    
    // Açıklama: İzin yoksa izin isteme ekranını göster
    if (!hasPermission) {
        // Açıklama: İzin isteme ekranı - kullanıcı galeri izni vermemişse gösterilir
        Column(modifier = Modifier.fillMaxSize()) {
            // Açıklama: Üst bar - başlık ve ikon
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = deviceGalleryText,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            // Açıklama: İzin isteme içeriği - kullanıcıyı izin vermeye yönlendirir
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = galleryPermissionRequiredText,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = galleryPermissionDescriptionText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Açıklama: İzin verme butonu - Android sürümüne göre farklı izin ister
                    Button(
                        onClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                            } else {
                                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                        }
                    ) {
                        Text(grantPermissionText)
                    }
                }
            }
        }
    } else if (deviceImages.isEmpty() && !isLoading) {
        // Açıklama: Boş galeri durumu - hiç resim yoksa gösterilir
        Column(modifier = Modifier.fillMaxSize()) {
            // Açıklama: Üst bar
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = deviceGalleryText,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            // Açıklama: Boş durum içeriği - kullanıcıya resim olmadığını bildirir
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = noImagesInGalleryText,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = noImagesFoundText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    } else {
        // Açıklama: Ana galeri ekranı - resimler varsa gösterilir
        Column(modifier = Modifier.fillMaxSize()) {
            // Açıklama: Üst bar - başlık, resim sayısı, klasör bilgisi ve butonlar
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Açıklama: Sol taraf - başlık ve bilgiler
                    Column {
                        Text(
                            text = deviceGalleryText,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = context.getString(R.string.images_count, deviceImages.size) + " • " + (selectedFolder ?: allFoldersText),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Açıklama: Sağ taraf butonları - klasör seçimi
                    Row {
                        // Açıklama: Klasör seçim butonu - farklı klasörler arasında geçiş sağlar
                        IconButton(
                            onClick = { showFolderDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Folder,
                                contentDescription = folderSelectText,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            
            // Açıklama: Yükleme göstergesi - resimler yüklenirken gösterilir
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // Açıklama: Ana önizleme ekranı - seçili resmin büyük görünümü
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.5f)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (selectedImageIndex < deviceImages.size && !isMultiSelectMode && showPreview) {
                        // Açıklama: Seçili resmin önizlemesi - blur arkaplan + net resim
                        val selectedImage = deviceImages[selectedImageIndex]
                        
                        // Açıklama: Blur arkaplan - %15 büyütülmüş ve blurlu efekt
                        Image(
                            painter = rememberAsyncImagePainter(
                                model = selectedImage,
                                onSuccess = { Log.d("GalleryScreen", "Background loaded: $selectedImage") }
                            ),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .scale(1.15f) // Açıklama: %15 büyütme
                                .blur(20.dp) // Açıklama: Blur efekti
                        )
                        
                        // Açıklama: Ana resim - üstte, net görünüm
                        Image(
                            painter = rememberAsyncImagePainter(
                                model = selectedImage,
                                onSuccess = { Log.d("GalleryScreen", "Preview loaded: $selectedImage") },
                                onError = { Log.e("GalleryScreen", "Preview failed: $selectedImage") }
                            ),
                            contentDescription = "Seçili resim",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // Açıklama: AI işlemi butonu - sağ alt köşede floating action button
                        if (deviceImages.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                FloatingActionButton(
                                    onClick = {
                                        // Açıklama: AI işlemi başlat - model seçim dialog'unu aç
                                        currentSelectedImage = deviceImages[selectedImageIndex]
                                        showModelSelectionDialog = true
                                    },
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = aiButtonText,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    } else if (isMultiSelectMode) {
                        // Açıklama: Çoklu seçim modunda bilgi göster - seçilen resim sayısını gösterir
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = itemsSelectedText.format(selectedImages.size),
                                    style = MaterialTheme.typography.headlineSmall
                                )
                            }
                        }
                    } else {
                        // Açıklama: Resim seçilmediğinde bilgi göster - kullanıcıyı yönlendirir
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PhotoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(80.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = selectImageText,
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = tapImageForPreviewText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                
                // Açıklama: Thumbnail grid - alt yarı, resimlerin küçük önizlemeleri
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4), // Açıklama: 4 sütunlu grid
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.5f)
                ) {
                    items(deviceImages.size) { index ->
                        // Açıklama: Her resim için grid item'ı
                        val imageUri = deviceImages[index]
                        val isSelected = index == selectedImageIndex && showPreview
                        val isMultiSelected = selectedImages.contains(imageUri)
                        
                        Card(
                            modifier = Modifier
                                .aspectRatio(1f) // Açıklama: Kare şeklinde
                                .clickable { 
                                    if (isMultiSelectMode) {
                                        // Açıklama: Çoklu seçim modunda toggle yap
                                        if (isMultiSelected) {
                                            selectedImages.remove(imageUri)
                                        } else {
                                            selectedImages.add(imageUri)
                                        }
                                        Log.d("GalleryScreen", "Multi-selected: ${selectedImages.size} images")
                                    } else {
                                        // Açıklama: Normal modda tek seçim - önizlemeyi göster
                                        selectImage(index)
                                    }
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    isMultiSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    isSelected && !isMultiSelectMode -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    else -> MaterialTheme.colorScheme.surface
                                }
                            )
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // Açıklama: Resim önizlemesi - Coil ile yüklenir
                                Image(
                                    painter = rememberAsyncImagePainter(
                                        model = imageUri,
                                        onSuccess = { Log.d("GalleryScreen", "Grid loaded: $imageUri") },
                                        onError = { Log.e("GalleryScreen", "Grid failed: $imageUri") }
                                    ),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                
                                // Açıklama: Seçim göstergeleri - hangi resmin seçili olduğunu gösterir
                                if (isMultiSelectMode) {
                                    // Açıklama: Çoklu seçim modu - checkbox göstergesi
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(24.dp)
                                            .background(
                                                Color.Black.copy(alpha = 0.6f),
                                                RoundedCornerShape(12.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isMultiSelected) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                } else if (isSelected) {
                                    // Açıklama: Tek seçim modu - seçili resim göstergesi
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(32.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                                RoundedCornerShape(16.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // Açıklama: Lazy loading - daha fazla resim varsa yükle
                    if (deviceImages.size < allImages.size) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                LaunchedEffect(Unit) {
                                    loadMoreImages() // Açıklama: Yeni resimleri yükle
                                }
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Açıklama: Klasör seçim dialog'u - kullanıcı farklı klasör seçebilir
        if (showFolderDialog) {
            FolderSelectionDialog(
                onDismissRequest = { showFolderDialog = false },
                onFolderSelected = { folder ->
                    selectedFolder = folder
                    showFolderDialog = false
                },
                currentFolder = selectedFolder,
                folderOptions = folderOptions,
                selectFolderText = selectFolderText,
                selectFolderDescriptionText = selectFolderDescriptionText,
                cancelText = cancelText,
                selectText = selectText
            )
        }
        
        // Açıklama: Model seçim dialog'u - AI maskeleme modeli seçimi
        // Bu dialog AutoMaskPreviewScreen.kt'ye yönlendirme için kullanılır
        if (showModelSelectionDialog) {
            ModelSelectionDialog(
                onDismissRequest = { 
                    showModelSelectionDialog = false
                    selectedModelPath = null
                    currentSelectedImage = null
                },
                onModelSelected = { modelPath ->
                    selectedModelPath = modelPath
                    showModelSelectionDialog = false
                    showMaskSelectionDialog = true // Açıklama: Model seçildikten sonra nesne seçimine geç
                    Log.d("GalleryScreen", "Model selected: $modelPath")
                },
                aiMaskingModeText = aiMaskingModeText,
                selectMaskingQualityText = selectMaskingQualityText,
                fastMaskingText = fastMaskingText,
                fastMaskingDescriptionText = fastMaskingDescriptionText,
                highQualityText = highQualityText,
                highQualityDescriptionText = highQualityDescriptionText,
                continueButtonText = continueButtonText,
                cancelText = cancelText
            )
        }
        
        // Açıklama: Maskeleme seçim dialog'u - hangi nesnelerin maskeleneceğini seç
        // Bu dialog AutoMaskPreviewScreen.kt'ye yönlendirme için kullanılır
        if (showMaskSelectionDialog) {
            MaskSelectionDialog(
                onDismissRequest = { 
                    showMaskSelectionDialog = false
                    selectedMaskObjects.clear()
                    selectedModelPath = null
                    currentSelectedImage = null
                },
                onMaskSelected = { selectedObjects ->
                    selectedMaskObjects.clear()
                    selectedMaskObjects.addAll(selectedObjects)
                    showMaskSelectionDialog = false
                    
                    // Açıklama: Maskeleme seçildikten sonra AutoMaskPreview'e yönlendir
                    // Navigations.kt'deki route yapısını kullanır
                    currentSelectedImage?.let { imageUri ->
                        try {
                            // Açıklama: Çoklu nesne seçimi için virgül ile ayırarak gönder
                            val objectsToMask = selectedMaskObjects.joinToString(",")
                            
                            // Açıklama: Navigation route'unu oluştur - AutoMaskPreviewScreen.kt'ye yönlendirir
                            val routeWithArguments = com.rootcrack.aigarage.navigation.Screen.AutoMaskPreview.route
                                .replace("{${com.rootcrack.aigarage.navigation.NavArgs.IMAGE_URI}}", Uri.encode(imageUri.toString()))
                                .replace("{${com.rootcrack.aigarage.navigation.NavArgs.OBJECT_TO_MASK}}", Uri.encode(objectsToMask))
                                .replace("{${com.rootcrack.aigarage.navigation.NavArgs.MODEL_PATH}}", Uri.encode(selectedModelPath ?: "mask.tflite"))
                            
                            Log.d("GalleryScreen", "Navigating to AutoMaskPreview with: $routeWithArguments")
                            navController.navigate(routeWithArguments)
                            
                        } catch (e: Exception) {
                            Log.e("GalleryScreen", "Navigation error", e)
                            Toast.makeText(context, navigationErrorText, Toast.LENGTH_SHORT).show()
                        }
                    }
                    
                    // Açıklama: State'leri temizle
                    selectedMaskObjects.clear()
                    selectedModelPath = null
                    currentSelectedImage = null
                },
                maskableObjects = maskableObjectsGallery,
                sortedDisplayNames = sortedMaskableObjectsGallery,
                selectObjectsToMaskText = selectObjectsToMaskText,
                objectsSelectedText = objectsSelectedText,
                selectAllText = selectAllText,
                clearText = clearText,
                maskText = maskText,
                cancelText = cancelText
            )
        }
    }
}

// Açıklama: Klasör seçim dialog composable'ı
// Kullanıcının cihaz galerisinde hangi klasörden resim görmek istediğini seçmesini sağlar
@Composable
private fun FolderSelectionDialog(
    onDismissRequest: () -> Unit,                    // Dialog'u kapatma fonksiyonu
    onFolderSelected: (folder: String?) -> Unit,     // Klasör seçildiğinde çağrılan fonksiyon
    currentFolder: String?,                          // Şu anda seçili olan klasör
    folderOptions: List<Pair<String, String?>>,       // Seçilebilir klasör listesi
    selectFolderText: String,
    selectFolderDescriptionText: String,
    cancelText: String,
    selectText: String
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .selectableGroup() // Açıklama: Radio button grubu için gerekli
            ) {
                // Açıklama: Dialog başlığı - klasör seçimi hakkında bilgi verir
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = selectFolderText,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = selectFolderDescriptionText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Açıklama: Klasör seçenekleri - her klasör için radio button
                folderOptions.forEach { (displayName, folderPath) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (currentFolder == folderPath),
                                onClick = { onFolderSelected(folderPath) },
                                role = Role.RadioButton
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (currentFolder == folderPath) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Açıklama: Radio button - seçim göstergesi
                            RadioButton(
                                selected = (currentFolder == folderPath),
                                onClick = { onFolderSelected(folderPath) }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (folderPath != null) {
                                    // Açıklama: Klasör yolu gösterimi - kullanıcıya hangi klasör olduğunu belirtir
                                    Text(
                                        text = "/$folderPath",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Açıklama: Dialog butonları - iptal ve seç
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(cancelText)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onFolderSelected(currentFolder)
                            onDismissRequest()
                        }
                    ) {
                        Text(selectText)
                    }
                }
            }
        }
    }
}

// Açıklama: Model seçim dialog composable'ı
// AI maskeleme için hangi modelin kullanılacağını seçmeyi sağlar
// Bu dialog AutoMaskPreviewScreen.kt'ye yönlendirme için kullanılır
@Composable
private fun ModelSelectionDialog(
    onDismissRequest: () -> Unit,                    // Dialog'u kapatma fonksiyonu
    onModelSelected: (modelPath: String) -> Unit,     // Model seçildiğinde çağrılan fonksiyon
    aiMaskingModeText: String,
    selectMaskingQualityText: String,
    fastMaskingText: String,
    fastMaskingDescriptionText: String,
    highQualityText: String,
    highQualityDescriptionText: String,
    continueButtonText: String,
    cancelText: String
) {
    var tempSelectedModel by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .selectableGroup() // Açıklama: Radio button grubu için gerekli
            ) {
                // Açıklama: Dialog başlığı - AI maskeleme hakkında bilgi verir
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = aiMaskingModeText,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = selectMaskingQualityText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Açıklama: Hızlı versiyon seçeneği - mask.tflite modeli
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = (tempSelectedModel == "mask.tflite"),
                            onClick = { tempSelectedModel = "mask.tflite" },
                            role = Role.RadioButton
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (tempSelectedModel == "mask.tflite") 
                            MaterialTheme.colorScheme.primaryContainer 
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Açıklama: Radio button - hızlı model seçimi
                        RadioButton(
                            selected = (tempSelectedModel == "mask.tflite"),
                            onClick = { tempSelectedModel = "mask.tflite" }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = fastMaskingText,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = fastMaskingDescriptionText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Açıklama: Yüksek kalite versiyon seçeneği - deeplabv3-xception65.tflite modeli
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = (tempSelectedModel == "deeplabv3-xception65.tflite"),
                            onClick = { tempSelectedModel = "deeplabv3-xception65.tflite" },
                            role = Role.RadioButton
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (tempSelectedModel == "deeplabv3-xception65.tflite") 
                            MaterialTheme.colorScheme.primaryContainer 
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Açıklama: Radio button - yüksek kalite model seçimi
                        RadioButton(
                            selected = (tempSelectedModel == "deeplabv3-xception65.tflite"),
                            onClick = { tempSelectedModel = "deeplabv3-xception65.tflite" }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = highQualityText,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = highQualityDescriptionText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Açıklama: Dialog butonları - iptal ve devam et
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(cancelText)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            tempSelectedModel?.let { model ->
                                onModelSelected(model)
                            }
                        },
                        enabled = tempSelectedModel != null
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(continueButtonText)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Açıklama: Çoklu maskeleme seçim dialog composable'ı
// Hangi nesnelerin maskeleneceğini seçmeyi sağlar
// Bu dialog AutoMaskPreviewScreen.kt'ye yönlendirme için kullanılır
@Composable
private fun MaskSelectionDialog(
    onDismissRequest: () -> Unit,                    // Dialog'u kapatma fonksiyonu
    onMaskSelected: (selectedObjects: List<String>) -> Unit, // Nesneler seçildiğinde çağrılan fonksiyon
    maskableObjects: Map<String, String>,            // Maskeleme yapılabilir nesneler
    sortedDisplayNames: List<String>,                 // Sıralı nesne adları
    selectObjectsToMaskText: String,
    objectsSelectedText: String,
    selectAllText: String,
    clearText: String,
    maskText: String,
    cancelText: String
) {
    val tempSelectedKeys = remember { mutableStateListOf<String>() }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Açıklama: Dialog başlığı ve seçim sayısı
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = selectObjectsToMaskText,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        if (tempSelectedKeys.isNotEmpty()) {
                            // Açıklama: Seçilen nesne sayısını göster
                            Text(
                                text = objectsSelectedText.format(tempSelectedKeys.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    // Açıklama: Tümünü seç/temizle butonları
                    Row {
                        // Açıklama: Tümünü seç butonu
                        TextButton(
                            onClick = {
                                tempSelectedKeys.clear()
                                tempSelectedKeys.addAll(maskableObjects.values)
                            }
                        ) {
                            Text(selectAllText)
                        }
                        
                        // Açıklama: Temizle butonu
                        TextButton(
                            onClick = {
                                tempSelectedKeys.clear()
                            }
                        ) {
                            Text(clearText)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Açıklama: Nesne listesi - scrollable liste
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    items(sortedDisplayNames.size) { index ->
                        val displayName = sortedDisplayNames[index]
                        val apiKey = maskableObjects[displayName]
                        if (apiKey != null) {
                            val isSelected = tempSelectedKeys.contains(apiKey)
                            
                            // Açıklama: Her nesne için seçim kartı
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clickable {
                                        if (isSelected) {
                                            tempSelectedKeys.remove(apiKey)
                                        } else {
                                            tempSelectedKeys.add(apiKey)
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) 
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    else MaterialTheme.colorScheme.surface
                                ),
                                border = if (isSelected) BorderStroke(
                                    2.dp, 
                                    MaterialTheme.colorScheme.primary
                                ) else null
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Açıklama: Checkbox - seçim göstergesi
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                tempSelectedKeys.add(apiKey)
                                            } else {
                                                tempSelectedKeys.remove(apiKey)
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Açıklama: Dialog butonları - iptal ve maskele
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(cancelText)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (tempSelectedKeys.isNotEmpty()) {
                                onMaskSelected(tempSelectedKeys.toList())
                            }
                        },
                        enabled = tempSelectedKeys.isNotEmpty()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(maskText)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
