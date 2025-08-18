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
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

// Model dosyalarının adları
const val TFLITE_MODEL_PRECISE = "deeplabv3-xception65.tflite"
const val TFLITE_MODEL_FAST = "mask.tflite"

// ... (MASKABLE_OBJECTS_GALLERY ve diğer sabitler aynı kalıyor)
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
        File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "AIGarage").apply { mkdirs() }
    }

    val imageFiles = remember { mutableStateListOf<File>() }
    var currentSortOrder by remember { mutableStateOf(GallerySortOrder.DATE_DESC) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }
    val selectedFilesForBatchOperation = remember { mutableStateListOf<File>() }
    var isSelectionMode by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    var showMoreMenuForFile by remember { mutableStateOf<File?>(null) }
    var fileForAutoMaskSelection by remember { mutableStateOf<File?>(null) }
    var showModelSelectionDialog by remember { mutableStateOf(false) } // Yeni state
    var showAutoMaskObjectSelectionDialog by remember { mutableStateOf(false) }
    var selectedModelPath by remember { mutableStateOf(TFLITE_MODEL_PRECISE) } // Varsayılan hassas model

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
                            val originalFileName = "imported_${System.currentTimeMillis()}.jpg"
                            val file = File(galleryDir, originalFileName)
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

    val pickImageForAutoMaskLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            fileForAutoMaskSelection = null // Önceki seçimi temizle
            showModelSelectionDialog = true // Önce model seçimi dialogunu göster
        }
    }

    fun deleteFiles(filesToDelete: List<File>) {
        if (filesToDelete.isEmpty()) return
        selectedFilesForBatchOperation.clear()
        selectedFilesForBatchOperation.addAll(filesToDelete)
        showDeleteConfirmationDialog = true
    }

    fun confirmDeleteSelectedFiles() {
        showDeleteConfirmationDialog = false
        isLoading = true
        coroutineScope.launch(Dispatchers.IO) {
            var deletedCount = 0
            selectedFilesForBatchOperation.forEach { file ->
                if (file.exists() && file.delete()) {
                    deletedCount++
                }
            }
            withContext(Dispatchers.Main) {
                isLoading = false
                if (deletedCount > 0) {
                    Toast.makeText(context, "$deletedCount fotoğraf silindi", Toast.LENGTH_SHORT).show()
                    loadAndSortFiles()
                }
                selectedFilesForBatchOperation.clear()
                isSelectionMode = false
            }
        }
    }

    fun shareFiles(filesToShare: List<File>) {
        if (filesToShare.isEmpty()) return
        val uris = filesToShare.mapNotNull { file ->
            try {
                FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            } catch (e: Exception) {
                null
            }
        }
        if (uris.isEmpty()) return

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

    fun navigateToAutoMaskPreviewScreen(fileToMask: File, objectKey: String, modelPath: String) {
        try {
            val fileUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", fileToMask)
            val encodedUri = Uri.encode(fileUri.toString())
            val encodedObjectKey = Uri.encode(objectKey)
            val encodedModelPath = Uri.encode(modelPath) // Model yolunu da encode et
            val route = Screen.AutoMaskPreview.route
                .replace("{${NavArgs.IMAGE_URI}}", encodedUri)
                .replace("{${NavArgs.OBJECT_TO_MASK}}", encodedObjectKey)
                .replace("{${NavArgs.MODEL_PATH}}", encodedModelPath) // Yeni argümanı ekle
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
                            Icon(Icons.Filled.AutoAwesome, "AI ile Otomatik Maskele")
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
                    Icon(Icons.Filled.Edit, "Galeriden Yeni Fotoğraf Düzenle")
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
                modifier = Modifier.fillMaxWidth(),
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
                                    val fileUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                    val encodedUri = Uri.encode(fileUri.toString())
                                    val route = Screen.PhotoPreview.route.replace("{${NavArgs.IMAGE_URI}}", encodedUri)
                                    navController.navigate(route)
                                }
                            },
                            onItemLongClick = {
                                if (!isSelectionMode) {
                                    isSelectionMode = true
                                    selectedFilesForBatchOperation.add(file)
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
                                showModelSelectionDialog = true // Önce model seçimi
                            },
                            onEditAIClick = { fileToEdit ->
                                showMoreMenuForFile = null
                                val fileUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", fileToEdit)
                                val encodedUri = Uri.encode(fileUri.toString())
                                val route = Screen.EditPhoto.route
                                    .replace("{${NavArgs.IMAGE_URI}}", encodedUri)
                                    .replace("{${NavArgs.INSTRUCTION}}", "")
                                    .replace("{${NavArgs.MASK}}", "null")
                                    .replace("{${NavArgs.EDIT_TYPE}}", EditTypeValues.NONE)
                                navController.navigate(route)
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
                onDismissRequest = { showDeleteConfirmationDialog = false },
                title = { Text("Fotoğrafları Sil") },
                text = { Text("${selectedFilesForBatchOperation.size} fotoğraf kalıcı olarak silinsin mi?") },
                confirmButton = {
                    TextButton(onClick = { confirmDeleteSelectedFiles() }) { Text("Sil") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmationDialog = false }) { Text("İptal") }
                }
            )
        }

        if (showModelSelectionDialog) {
            ModelSelectionDialog(
                onDismiss = {
                    showModelSelectionDialog = false
                    fileForAutoMaskSelection = null
                },
                onModelSelected = { modelPath ->
                    selectedModelPath = modelPath
                    showModelSelectionDialog = false
                    showAutoMaskObjectSelectionDialog = true // Şimdi nesne seçimini göster
                }
            )
        }

        if (showAutoMaskObjectSelectionDialog && fileForAutoMaskSelection != null) {
            ObjectSelectionDialog(
                maskableObjects = MASKABLE_OBJECTS_GALLERY,
                onDismiss = {
                    showAutoMaskObjectSelectionDialog = false
                    fileForAutoMaskSelection = null
                },
                onObjectSelected = { selectedObjectKey ->
                    showAutoMaskObjectSelectionDialog = false
                    fileForAutoMaskSelection?.let {
                        navigateToAutoMaskPreviewScreen(it, selectedObjectKey, selectedModelPath)
                    }
                }
            )
        }
    }
}
//... (GalleryItem, SortOrderDialog, ObjectSelectionDialog Composable'ları burada yer alıyor.
// Değişiklik olmadığı için tekrar eklenmemiştir.)
@Composable
fun ModelSelectionDialog(
    onDismiss: () -> Unit,
    onModelSelected: (modelPath: String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Maskeleme Yöntemini Seçin") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onModelSelected(TFLITE_MODEL_PRECISE) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text("Hassas (Daha Yavaş)")
                }
                Button(
                    onClick = { onModelSelected(TFLITE_MODEL_FAST) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Hızlı (Daha Az Detaylı)")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
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
