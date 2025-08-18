
package com.rootcrack.aigarage.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.rootcrack.aigarage.navigation.EditTypeValues
import com.rootcrack.aigarage.navigation.NavArgs
import com.rootcrack.aigarage.navigation.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

const val TAG_GALLERY_SCREEN = "GalleryScreen"

// Modelinizin tanıdığı nesneler için map
val MASKABLE_OBJECTS_GALLERY = mapOf(
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
val CUSTOM_SORTED_MASKABLE_OBJECT_DISPLAY_NAMES = listOf(
    "İnsan", "Araba", "Motosiklet", "Bisiklet", "Kamyon", "Otobüs", "Tren",
    "Yol", "Kaldırım", "Bina", "Duvar", "Arazi", "Gökyüzü"
)

enum class GallerySortOrder {
    DATE_DESC, DATE_ASC, NAME_ASC, NAME_DESC
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(navController: NavHostController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val galleryDir = remember {
        File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "AIGarage").apply {
            if (!exists()) mkdirs()
        }
    }

    val imageFiles = remember { mutableStateListOf<File>() }
    var currentSortOrder by remember { mutableStateOf(GallerySortOrder.DATE_DESC) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }
    val selectedFilesForBatchOperation = remember { mutableStateListOf<File>() }
    var isSelectionMode by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    // Hangi dosya için "daha fazla" menüsünün veya nesne seçim dialogunun açık olduğunu tutar
    var showMoreMenuForFile by remember { mutableStateOf<File?>(null) }
    var fileForAutoMaskSelection by remember { mutableStateOf<File?>(null) }
    var showAutoMaskObjectSelectionDialog by remember { mutableStateOf(false) }


    fun loadAndSortFiles() {
        isLoading = true
        coroutineScope.launch(Dispatchers.IO) {
            val files = galleryDir.listFiles()
                ?.filter { it.isFile && it.extension.lowercase() in listOf("jpg", "png", "jpeg", "webp") }
                ?: emptyList()
            val sortedFiles = when (currentSortOrder) {
                GallerySortOrder.DATE_DESC -> files.sortedByDescending { it.lastModified() }
                GallerySortOrder.DATE_ASC -> files.sortedBy { it.lastModified() }
                GallerySortOrder.NAME_ASC -> files.sortedBy { it.name.lowercase() }
                GallerySortOrder.NAME_DESC -> files.sortedByDescending { it.name.lowercase() }
            }
            withContext(Dispatchers.Main) {
                imageFiles.clear()
                imageFiles.addAll(sortedFiles)
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit, navController.currentBackStackEntry) {
        MobileAds.initialize(context) {}
        loadAndSortFiles()
        isSelectionMode = false
        selectedFilesForBatchOperation.clear()
        showMoreMenuForFile = null
        fileForAutoMaskSelection = null
        showAutoMaskObjectSelectionDialog = false
    }

    val pickImageForAILauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            val encodedUri = Uri.encode(selectedUri.toString())
            val route = Screen.EditPhoto.route
                .replace("{${NavArgs.IMAGE_URI}}", encodedUri)
                .replace("{${NavArgs.INSTRUCTION}}", Uri.encode(""))
                .replace("{${NavArgs.MASK}}", Uri.encode("null"))
                .replace("{${NavArgs.EDIT_TYPE}}", EditTypeValues.NONE)
            navController.navigate(route)
        }
    }

    val importImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            isLoading = true
            coroutineScope.launch(Dispatchers.IO) {
                var importedCount = 0
                uris.forEach { uri ->
                    try {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            val originalFileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                    if (nameIndex != -1) cursor.getString(nameIndex) else null
                                } else null
                            } ?: "imported_${System.currentTimeMillis()}.${
                                uri.lastPathSegment?.substringAfterLast('.', "jpg") ?: "jpg"
                            }"

                            var counter = 0
                            var newFileName = originalFileName
                            var file = File(galleryDir, newFileName)
                            while (file.exists()) {
                                counter++
                                val nameWithoutExtension = originalFileName.substringBeforeLast('.')
                                val extension = originalFileName.substringAfterLast('.', "")
                                newFileName = "${nameWithoutExtension}_$counter${if (extension.isNotEmpty()) ".$extension" else ""}"
                                file = File(galleryDir, newFileName)
                            }
                            FileOutputStream(file).use { outputStream -> inputStream.copyTo(outputStream) }
                            importedCount++
                        }
                    } catch (e: Exception) {
                        Log.e(TAG_GALLERY_SCREEN, "Resim içe aktarılırken hata: $uri", e)
                    }
                }
                withContext(Dispatchers.Main) {
                    isLoading = false
                    if (importedCount > 0) {
                        Toast.makeText(context, "$importedCount fotoğraf eklendi", Toast.LENGTH_SHORT).show()
                        loadAndSortFiles()
                    } else {
                        Toast.makeText(context, "Fotoğraf eklenemedi", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Cihazdan OTOMATİK MASKELEME için resim seçme launcher'ı
    val pickImageForAutoMaskLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            isLoading = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    // Bu dosyayı geçici olarak AIGarage klasörüne kopyala
                    val tempFile = File(galleryDir, "automask_temp_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(selectedUri)?.use { inputStream ->
                        FileOutputStream(tempFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        fileForAutoMaskSelection = tempFile // Seçilen ve kopyalanan dosyayı state'e ata
                        showAutoMaskObjectSelectionDialog = true // Nesne seçim dialogunu göster
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        Log.e(TAG_GALLERY_SCREEN, "Otomatik maskeleme için resim kopyalanamadı", e)
                        Toast.makeText(context, "Resim işlenemedi.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }


    fun deleteFiles(filesToDelete: List<File>) {
        if (filesToDelete.isEmpty()) return
        selectedFilesForBatchOperation.clear()
        selectedFilesForBatchOperation.addAll(filesToDelete)
        showDeleteConfirmationDialog = true
        // Menü zaten GalleryItem'da kapatılacak, burada tekrar kapatmaya gerek yok.
    }

    fun confirmDeleteSelectedFiles() {
        showDeleteConfirmationDialog = false
        isLoading = true
        coroutineScope.launch(Dispatchers.IO) {
            var deletedCount = 0
            selectedFilesForBatchOperation.forEach { file ->
                if (file.exists() && file.delete()) {
                    deletedCount++
                } else {
                    Log.w(TAG_GALLERY_SCREEN, "Dosya silinemedi: ${file.absolutePath}")
                }
            }
            withContext(Dispatchers.Main) {
                isLoading = false
                if (deletedCount > 0) {
                    Toast.makeText(context, "$deletedCount fotoğraf silindi", Toast.LENGTH_SHORT).show()
                    loadAndSortFiles()
                } else {
                    Toast.makeText(context, "Fotoğraflar silinemedi", Toast.LENGTH_SHORT).show()
                }
                selectedFilesForBatchOperation.clear()
                isSelectionMode = false // Seçim modunu kapat
            }
        }
    }

    fun shareFiles(filesToShare: List<File>) {
        if (filesToShare.isEmpty()) return
        // Menü zaten GalleryItem'da kapatılacak
        val uris = filesToShare.mapNotNull { file ->
            try {
                FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            } catch (e: Exception) {
                Log.e(TAG_GALLERY_SCREEN, "Paylaşım için URI oluşturulamadı: ${file.name}", e)
                null
            }
        }
        if (uris.isEmpty()) {
            Toast.makeText(context, "Paylaşılacak dosya bulunamadı.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent().apply {
            action = if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
            if (uris.size == 1) {
                putExtra(Intent.EXTRA_STREAM, uris.first())
            } else {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Fotoğrafları paylaş"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Paylaşım uygulaması bulunamadı.", Toast.LENGTH_SHORT).show()
        }
    }

    fun navigateToAutoMaskPreviewScreen(fileToMask: File, selectedObjectKey: String) {
        // Menü zaten GalleryItem'da veya ObjectSelectionDialog'da kapatılacak
        try {
            val fileUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", fileToMask)
            val encodedUri = Uri.encode(fileUri.toString())
            val encodedObjectKey = Uri.encode(selectedObjectKey)
            val route = Screen.AutoMaskPreview.route
                .replace("{${NavArgs.IMAGE_URI}}", encodedUri)
                .replace("{${NavArgs.OBJECT_TO_MASK}}", encodedObjectKey)
            navController.navigate(route)
        } catch (e: Exception) {
            Log.e(TAG_GALLERY_SCREEN, "Otomatik maskeleme önizlemesi için URI oluşturma hatası", e)
            Toast.makeText(context, "Otomatik maskeleme başlatılamadı.", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isSelectionMode) "${selectedFilesForBatchOperation.size} seçildi" else "Galeri") },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            // Tümünü seç / Tüm seçimi kaldır mantığı
                            if (selectedFilesForBatchOperation.size == imageFiles.size) {
                                selectedFilesForBatchOperation.clear()
                            } else {
                                selectedFilesForBatchOperation.clear()
                                selectedFilesForBatchOperation.addAll(imageFiles)
                            }
                        }) {
                            Icon(
                                if (selectedFilesForBatchOperation.size == imageFiles.size) Icons.Filled.Deselect else Icons.Filled.SelectAll,
                                contentDescription = if (selectedFilesForBatchOperation.size == imageFiles.size) "Tüm Seçimi Kaldır" else "Tümünü Seç"
                            )
                        }
                        IconButton(onClick = { if (selectedFilesForBatchOperation.isNotEmpty()) showDeleteConfirmationDialog = true }) {
                            Icon(Icons.Filled.Delete, "Seçilenleri Sil")
                        }
                        IconButton(onClick = { if (selectedFilesForBatchOperation.isNotEmpty()) shareFiles(selectedFilesForBatchOperation.toList()) }) {
                            Icon(Icons.Filled.Share, "Seçilenleri Paylaş")
                        }
                    } else {
                        IconButton(onClick = { pickImageForAutoMaskLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                            Icon(Icons.Filled.AutoAwesome, "AI ile Otomatik Maskele (Yeni Resim)") // AutoAwesomeSearch yerine AutoAwesome
                        }
                        IconButton(onClick = { importImagesLauncher.launch("image/*") }) {
                            Icon(Icons.Filled.AddPhotoAlternate, "Cihazdan Fotoğraf Ekle")
                        }
                        IconButton(onClick = { showSortDialog = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, "Sırala")
                        }
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedFilesForBatchOperation.clear()
                        }) {
                            Icon(Icons.Filled.Close, "Seçim Modunu Kapat")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(onClick = {
                    pickImageForAILauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }) {
                    Icon(Icons.Filled.Edit, "Galeriden Yeni Fotoğraf Düzenle (AI)")
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                factory = { context ->
                    AdView(context).apply {
                        setAdSize(AdSize.BANNER)
                        adUnitId = "ca-app-pub-3940256099942544/6300978111" // Test ID
                        loadAd(AdRequest.Builder().build())
                    }
                }
            )

            if (isLoading && imageFiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (imageFiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Galerinizde hiç fotoğraf bulunmuyor.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    contentPadding = PaddingValues(all = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(imageFiles, key = { it.absolutePath }) { file ->
                        val isSelected = selectedFilesForBatchOperation.contains(file)
                        GalleryItem(
                            file = file,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            onItemClick = {
                                if (isSelectionMode) {
                                    if (isSelected) selectedFilesForBatchOperation.remove(file)
                                    else selectedFilesForBatchOperation.add(file)
                                } else {
                                    try {
                                        val fileUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                        val encodedUri = Uri.encode(fileUri.toString())
                                        // PhotoDetail yerine direkt PhotoPreview'a yönlendiriyoruz
                                        val route = Screen.PhotoPreview.route.replace("{${NavArgs.IMAGE_URI}}", encodedUri)
                                        navController.navigate(route)
                                    } catch (e: Exception) {
                                        Log.e(TAG_GALLERY_SCREEN, "Detay/Önizleme URI hatası", e)
                                        Toast.makeText(context, "Fotoğraf açılamadı.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onItemLongClick = {
                                if (!isSelectionMode) {
                                    isSelectionMode = true
                                    selectedFilesForBatchOperation.add(file) // Uzun basılanı direkt seç
                                } else {
                                    // Zaten seçim modundaysa uzun basış seçimi tersine çevirsin
                                    if (isSelected) selectedFilesForBatchOperation.remove(file)
                                    else selectedFilesForBatchOperation.add(file)
                                }
                            },
                            currentOpenFileForMenu = showMoreMenuForFile,
                            onMoreClick = { clickedFile ->
                                showMoreMenuForFile = if (showMoreMenuForFile == clickedFile) null else clickedFile
                            },
                            onDismissMoreMenu = { showMoreMenuForFile = null },
                            onAutoMaskClick = { fileToMask ->
                                showMoreMenuForFile = null
                                fileForAutoMaskSelection = fileToMask
                                showAutoMaskObjectSelectionDialog = true
                            },
                            onEditAIClick = { fileToEdit ->
                                showMoreMenuForFile = null
                                try {
                                    val fileUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", fileToEdit)
                                    val encodedUri = Uri.encode(fileUri.toString())
                                    val route = Screen.EditPhoto.route
                                        .replace("{${NavArgs.IMAGE_URI}}", encodedUri)
                                        .replace("{${NavArgs.INSTRUCTION}}", Uri.encode(""))
                                        .replace("{${NavArgs.MASK}}", Uri.encode("null"))
                                        .replace("{${NavArgs.EDIT_TYPE}}", EditTypeValues.NONE)
                                    navController.navigate(route)
                                } catch (e: Exception) {
                                    Log.e(TAG_GALLERY_SCREEN, "Düzenleme için URI oluşturma hatası", e)
                                    Toast.makeText(context, "Fotoğraf düzenlenemedi.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onShareClick = { fileToShare ->
                                showMoreMenuForFile = null
                                shareFiles(listOf(fileToShare))
                            },
                            onDeleteClick = { fileToDelete ->
                                showMoreMenuForFile = null
                                deleteFiles(listOf(fileToDelete))
                            }
                        )
                    }
                }
            }
        }

        if (showSortDialog) {
            SortOrderDialog(
                currentOrder = currentSortOrder,
                onDismiss = { showSortDialog = false },
                onSortSelected = { newOrder ->
                    currentSortOrder = newOrder
                    showSortDialog = false
                    loadAndSortFiles()
                }
            )
        }

        if (showDeleteConfirmationDialog) {
            AlertDialog(
                onDismissRequest = {
                    showDeleteConfirmationDialog = false
                    // İptal edilirse seçimi temizleme, kullanıcı devam etmek isteyebilir.
                },
                title = { Text("Fotoğrafları Sil") },
                text = { Text("${selectedFilesForBatchOperation.size} fotoğraf kalıcı olarak silinsin mi?") },
                confirmButton = {
                    TextButton(onClick = { confirmDeleteSelectedFiles() }) {
                        Text("Sil", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmationDialog = false }) { Text("İptal") }
                }
            )
        }
// --- DOSYANIN 1. PARÇASININ SONU ---
// --- DOSYANIN 2. PARÇASININ BAŞLANGICI ---

        if (showAutoMaskObjectSelectionDialog) {
            ObjectSelectionDialog(
                maskableObjects = MASKABLE_OBJECTS_GALLERY,
                onDismiss = {
                    showAutoMaskObjectSelectionDialog = false
                    // Eğer resim pickImageForAutoMaskLauncher ile seçildiyse ve iptal edilirse, geçici dosyayı sil
                    if (fileForAutoMaskSelection?.path?.contains("automask_temp_") == true) {
                        fileForAutoMaskSelection?.delete()
                        Log.d(TAG_GALLERY_SCREEN, "Geçici automask dosyası silindi: ${fileForAutoMaskSelection?.name}")
                    }
                    fileForAutoMaskSelection = null // State'i sıfırla
                },
                onObjectSelected = { selectedObjectKey ->
                    showAutoMaskObjectSelectionDialog = false
                    fileForAutoMaskSelection?.let { fileToMask ->
                        navigateToAutoMaskPreviewScreen(fileToMask, selectedObjectKey)
                        // fileForAutoMaskSelection state'ini burada null yapmıyoruz.
                        // AutoMaskPreviewScreen'e geçici dosyanın sorumluluğu devrediliyor.
                        // Eğer galeriden bir dosya değilse (yani automask_temp ise),
                        // AutoMaskPreviewScreen tamamlandığında veya iptal edildiğinde bu dosyayı silmeli.
                        // Şimdilik, GalleryScreen'de bu state'i açık bırakıyoruz,
                        // böylece AutoMaskPreview'dan geri dönüldüğünde gerekirse tekrar kullanılabilir
                        // ya da AutoMaskPreview ekranı silme işlemini üstlenir.
                        // Ancak, karışıklığı önlemek için, eğer bir sonraki adımda kullanılmayacaksa null yapmak daha güvenli olabilir.
                        // Şimdilik, sadece galeriden olmayan geçici dosyalar için null yapalım,
                        // çünkü AutoMaskPreviewScreen kendi kopyasını oluşturabilir veya direkt kullanabilir.
                        // Eğer pickImageForAutoMaskLauncher ile gelen geçici dosya ise, AutoMaskPreviewScreen silebilir.
                        // fileForAutoMaskSelection = null; // Bu satırı şimdilik yoruma alıyoruz.
                    } ?: run {
                        Toast.makeText(context, "Maskelenecek dosya bulunamadı.", Toast.LENGTH_SHORT).show()
                        Log.e(TAG_GALLERY_SCREEN, "ObjectSelectionDialog: fileForAutoMaskSelection is null onObjectSelected")
                    }
                }
            )
        }
    } // Scaffold sonu
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryItem(
    file: File,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
    currentOpenFileForMenu: File?,
    onMoreClick: (File) -> Unit,
    onDismissMoreMenu: () -> Unit,
    onAutoMaskClick: (File) -> Unit,
    onEditAIClick: (File) -> Unit,
    onShareClick: (File) -> Unit,
    onDeleteClick: (File) -> Unit
) {
    val context = LocalContext.current
    val imageUri = remember(file) {
        try {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (e: Exception) {
            Log.e(TAG_GALLERY_SCREEN, "GalleryItem URI hatası: ${file.name}", e)
            null
        }
    }
    val isMenuExpanded = currentOpenFileForMenu == file

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f) // Kare görünüm
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onItemLongClick
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant) // Arka plan rengi
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (imageUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(model = imageUri),
                    contentDescription = file.name,
                    contentScale = ContentScale.Crop, // Resmi kareye yay
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // URI null ise (hata oluştuysa) bir placeholder göster
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.LightGray),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.BrokenImage, "Hata", tint = Color.DarkGray)
                }
            }

            // Seçim modunda değilse ve bu item'ın menüsü için üç nokta göster
            if (!isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(0.dp) // Padding'i IconButton'a taşıdık
                ) {
                    IconButton(
                        onClick = { onMoreClick(file) },
                        modifier = Modifier
                            .size(36.dp) // Buton boyutunu ayarladık
                            .padding(4.dp) // Buton çevresine biraz boşluk
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape) // Hafifçe opak arka plan
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Daha fazla seçenek (${file.name})",
                            tint = Color.White // İkon rengi
                        )
                    }

                    // DropdownMenu'yu IconButton'un içine değil, Box'ın sonuna ekliyoruz
                    // Böylece IconButton'un üzerinde açılır.
                    DropdownMenu(
                        expanded = isMenuExpanded,
                        onDismissRequest = onDismissMoreMenu,
                        // offset DpOffset ile ayarlanabilir, örneğin sağ üstten açılması için
                        offset = DpOffset(x = (-8).dp, y = (0).dp) // Örnek offset, ayarlamanız gerekebilir
                    ) {
                        DropdownMenuItem(
                            text = { Text("Otomatik Maskele") },
                            onClick = {
                                onAutoMaskClick(file)
                                // onDismissMoreMenu() // Bu artık onAutoMaskClick içinde dolaylı olarak yönetiliyor
                            },
                            leadingIcon = { Icon(Icons.Filled.AutoFixHigh, null, tint = MaterialTheme.colorScheme.primary) }
                        )
                        DropdownMenuItem(
                            text = { Text("Düzenle (AI)") },
                            onClick = {
                                onEditAIClick(file)
                                // onDismissMoreMenu()
                            },
                            leadingIcon = { Icon(Icons.Filled.Edit, null, tint = MaterialTheme.colorScheme.primary) }
                        )
                        DropdownMenuItem(
                            text = { Text("Paylaş") },
                            onClick = {
                                onShareClick(file)
                                // onDismissMoreMenu()
                            },
                            leadingIcon = { Icon(Icons.Filled.Share, null, tint = MaterialTheme.colorScheme.primary) }
                        )
                        DropdownMenuItem(
                            text = { Text("Sil") },
                            onClick = {
                                onDeleteClick(file)
                                // onDismissMoreMenu()
                            },
                            leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }


            // Seçim Modu Göstergesi
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) // Seçiliyse vurgula
                            else Color.Transparent // Seçili değilse transparan
                        )
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Seçildi",
                            tint = MaterialTheme.colorScheme.onPrimary, // Seçim ikonunun rengi
                            modifier = Modifier
                                .align(Alignment.Center) // Ortada göster
                                .size(48.dp) // Boyutunu ayarla
                        )
                    }
                }
            }
        }
    }
}


// Sıralama ayarlarını içeren dialog Composable'ı
@Composable
fun SortOrderDialog(
    currentOrder: GallerySortOrder,
    onDismiss: () -> Unit,
    onSortSelected: (GallerySortOrder) -> Unit,
) {
    val radioOptions = mapOf(
        GallerySortOrder.DATE_DESC to "Tarihe Göre (En Yeni)",
        GallerySortOrder.DATE_ASC to "Tarihe Göre (En Eski)",
        GallerySortOrder.NAME_ASC to "Ada Göre (A-Z)",
        GallerySortOrder.NAME_DESC to "Ada Göre (Z-A)"
    )
    // Sırayı korumak için LinkedHashMap veya List<Pair> kullanılabilir, ancak mapOf genellikle iterasyon sırasını korur.
    // Garanti altına almak için:
    val orderedRadioOptions = remember {
        listOf(
            GallerySortOrder.DATE_DESC to "Tarihe Göre (En Yeni)",
            GallerySortOrder.DATE_ASC to "Tarihe Göre (En Eski)",
            GallerySortOrder.NAME_ASC to "Ada Göre (A-Z)",
            GallerySortOrder.NAME_DESC to "Ada Göre (Z-A)"
        )
    }

    var selectedOption by remember { mutableStateOf(currentOrder) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sıralama Ölçütü") },
        text = {
            Column(Modifier.selectableGroup()) { // Radio butonları için grup
                orderedRadioOptions.forEach { (order, text) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .selectable( // Satırın tıklanabilir olmasını sağlar
                                selected = (order == selectedOption),
                                onClick = { selectedOption = order }, // Seçimi güncelle
                                role = Role.RadioButton // Erişilebilirlik için
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (order == selectedOption),
                            onClick = null // Tıklama zaten Row'da yönetiliyor
                        )
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSortSelected(selectedOption)
                // onDismiss() // onSortSelected zaten dismiss'i tetikliyor olabilir, GalleryScreen'deki mantığa bağlı.
                // Genellikle confirm'den sonra dialog kapanır.
            }) {
                Text("Uygula")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { // Dialogu kapat
                Text("İptal")
            }
        }
    )
}

// Nesne seçimi için dialog Composable'ı
@Composable
fun ObjectSelectionDialog(
    maskableObjects: Map<String, String>, // Görünen Ad -> Anahtar Değer (örn: "Araba" -> "car")
    onDismiss: () -> Unit,
    onObjectSelected: (String) -> Unit, // Seçilen nesnenin anahtarını döndürür (örn: "car")
) {
    // CUSTOM_SORTED_MASKABLE_OBJECT_DISPLAY_NAMES listesini kullanarak sıralı ve geçerli nesneleri al
    val displayableObjectEntries = remember(maskableObjects) {
        CUSTOM_SORTED_MASKABLE_OBJECT_DISPLAY_NAMES.mapNotNull { displayName ->
            maskableObjects[displayName]?.let { key -> displayName to key } // (Görünen Ad, Anahtar) çifti
        }
    }

    // Başlangıçta seçili olacak anahtarı belirle, eğer liste boş değilse ilkini al
    var selectedObjectKey by remember {
        mutableStateOf(displayableObjectEntries.firstOrNull()?.second ?: "")
    }


    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Maskelenecek Nesneyi Seçin") },
        text = {
            if (displayableObjectEntries.isEmpty()) {
                Text("Maskelenebilecek nesne bulunamadı.")
            } else {
                LazyColumn(modifier = Modifier.padding(top = 8.dp)) { // Uzun listeler için kaydırılabilir alan
                    items(displayableObjectEntries, key = { it.second /* Anahtar benzersiz olmalı */ }) { (displayName, objectKey) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable( // Satırı tıklanabilir yap
                                    selected = (objectKey == selectedObjectKey),
                                    onClick = { selectedObjectKey = objectKey }, // Seçimi güncelle
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 12.dp, horizontal = 16.dp), // Yatay padding ekledik
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (objectKey == selectedObjectKey),
                                onClick = null // Tıklama Row'da yönetiliyor
                            )
                            Text(
                                text = displayName, // Görünen adı göster
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selectedObjectKey.isNotEmpty()) {
                        onObjectSelected(selectedObjectKey) // Seçimi bir üst bileşene bildir
                    }
                    // onDismiss() // Seçim yapıldıktan sonra dialog kapanır, onObjectSelected sonrası çağrılabilir.
                    // GalleryScreen'de yönetiliyor.
                },
                enabled = selectedObjectKey.isNotEmpty() && displayableObjectEntries.isNotEmpty() // Seçim yapıldığında ve liste boş olmadığında aktif
            ) {
                Text("Seç")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { // Dialogu kapat
                Text("İptal")
            }
        }
    )
}
// --- DOSYANIN 2. PARÇASININ SONU ---

