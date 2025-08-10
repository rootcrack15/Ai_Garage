package com.rootcrack.aigarage.screens

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome // Otomatik maskeleme ikonu
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.rootcrack.aigarage.navigation.NavArgs
import com.rootcrack.aigarage.navigation.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

const val TAG_GALLERY_SCREEN = "GalleryScreen"
private const val DEFAULT_OBJECT_TO_MASK_FROM_GALLERY = "person" // Otomatik maskeleme için varsayılan nesne

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
    var showMoreMenuForFile by remember { mutableStateOf<File?>(null) } // Hangi dosya için menü açık
    fun loadAndSortFiles() {
        val files = galleryDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in listOf("jpg", "png", "jpeg", "webp") }
            ?: emptyList()

        val sortedFiles = when (currentSortOrder) {
            GallerySortOrder.DATE_DESC -> files.sortedByDescending { it.lastModified() }
            GallerySortOrder.DATE_ASC -> files.sortedBy { it.lastModified() }
            GallerySortOrder.NAME_ASC -> files.sortedBy { it.name.lowercase() }
            GallerySortOrder.NAME_DESC -> files.sortedByDescending { it.name.lowercase() }
        }
        imageFiles.clear()
        imageFiles.addAll(sortedFiles)
        Log.d(TAG_GALLERY_SCREEN, "Galeri yüklendi ve sıralandı: ${imageFiles.size} dosya, Sıralama: $currentSortOrder")
    }

    LaunchedEffect(Unit, navController.currentBackStackEntry) {
        MobileAds.initialize(context) {}
        loadAndSortFiles() // Galeriye her dönüldüğünde veya ilk açılışta dosyaları yükle/yenile
    }

    val pickImageForAILauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            val encodedUri = Uri.encode(selectedUri.toString())
            // Screen.EditPhoto rotasını kullanarak yönlendirme (maskesiz ve talimatsız)
            val route = Screen.EditPhoto.route
                .replace("{${NavArgs.IMAGE_URI}}", encodedUri)
                .replace("{${NavArgs.INSTRUCTION}}", Uri.encode("")) // Başlangıçta talimat yok
                .replace("{${NavArgs.MASK}}", Uri.encode(""))      // Başlangıçta maske yok

            Log.d(TAG_GALLERY_SCREEN, "Navigating to Edit Screen from Gallery with URI: $selectedUri, Route: $route")
            navController.navigate(route)
        }
    }

    val importImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
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

                            val file = File(galleryDir, originalFileName)
                            FileOutputStream(file).use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                            importedCount++
                        }
                    } catch (e: Exception) {
                        Log.e(TAG_GALLERY_SCREEN, "Resim içe aktarılırken hata: $uri", e)
                    }
                }
                withContext(Dispatchers.Main) {
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
    fun deleteSelectedFiles() {
        if (selectedFilesForBatchOperation.isEmpty()) return
        showDeleteConfirmationDialog = true
    }

    fun confirmDeleteSelectedFiles() {
        showDeleteConfirmationDialog = false
        coroutineScope.launch(Dispatchers.IO) {
            var deletedCount = 0
            val filesToDelete = ArrayList(selectedFilesForBatchOperation)
            filesToDelete.forEach { file ->
                if (file.exists() && file.delete()) {
                    deletedCount++
                } else {
                    Log.w(TAG_GALLERY_SCREEN, "Dosya silinemedi: ${file.absolutePath}")
                }
            }
            withContext(Dispatchers.Main) {
                if (deletedCount > 0) {
                    Toast.makeText(context, "$deletedCount fotoğraf silindi", Toast.LENGTH_SHORT).show()
                    imageFiles.removeAll(filesToDelete.toSet())
                    selectedFilesForBatchOperation.removeAll(filesToDelete.toSet())
                    if (selectedFilesForBatchOperation.isEmpty()) {
                        isSelectionMode = false
                    }
                } else {
                    Toast.makeText(context, "Fotoğraflar silinemedi", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun shareSelectedFiles() {
        if (selectedFilesForBatchOperation.isEmpty()) return
        val urisToShare = ArrayList(selectedFilesForBatchOperation.mapNotNull { file ->
            try {
                FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            } catch (e: Exception) {
                Log.e(TAG_GALLERY_SCREEN, "Paylaşım için URI alınırken hata: ${file.name}", e)
                null
            }
        })

        if (urisToShare.isEmpty()) {
            Toast.makeText(context, "Paylaşılacak dosya bulunamadı veya URI hatası.", Toast.LENGTH_SHORT).show()
            return
        }

        val shareIntent = Intent().apply {
            action = if (urisToShare.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, urisToShare)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(shareIntent, "Fotoğrafları paylaş"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Paylaşım yapılabilecek uygulama bulunamadı.", Toast.LENGTH_SHORT).show()
        }
    }

    fun navigateToAutoMask(file: File) {
        val imageUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val encodedUri = Uri.encode(imageUri.toString())
        val encodedObject = Uri.encode(DEFAULT_OBJECT_TO_MASK_FROM_GALLERY) // Varsayılan nesneyi kullan

        val route = Screen.AutoMaskPreview.route
            .replace("{${NavArgs.IMAGE_URI}}", encodedUri)
            .replace("{${NavArgs.OBJECT_TO_MASK}}", encodedObject)

        Log.d(TAG_GALLERY_SCREEN, "Navigating to AutoMaskPreviewScreen: $route")
        navController.navigate(route)
        isSelectionMode = false // İşlem sonrası seçim modundan çık
        selectedFilesForBatchOperation.clear()
        showMoreMenuForFile = null // Menüyü kapat
    }
    fun navigateToEdit(file: File) {
        val imageUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val encodedUri = Uri.encode(imageUri.toString())
        val route = Screen.EditPhoto.route
            .replace("{${NavArgs.IMAGE_URI}}", encodedUri)
            .replace("{${NavArgs.INSTRUCTION}}", Uri.encode("")) // Başlangıçta talimat yok
            .replace("{${NavArgs.MASK}}", Uri.encode(""))      // Başlangıçta maske yok

        Log.d(TAG_GALLERY_SCREEN, "Navigating to Edit Screen from Gallery for file: $route")
        navController.navigate(route)
        isSelectionMode = false // İşlem sonrası seçim modundan çık
        selectedFilesForBatchOperation.clear()
        showMoreMenuForFile = null // Menüyü kapat
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (isSelectionMode) "${selectedFilesForBatchOperation.size} seçildi" else "Galeri")
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
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
                        IconButton(onClick = { shareSelectedFiles() }) {
                            Icon(Icons.Filled.Share, contentDescription = "Seçilenleri Paylaş")
                        }
                        IconButton(onClick = { deleteSelectedFiles() }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Seçilenleri Sil")
                        }
                        // Sadece TEK dosya seçiliyse Otomatik Maskeleme ve Düzenleme butonlarını göster
                        if (selectedFilesForBatchOperation.size == 1) {
                            IconButton(onClick = {
                                selectedFilesForBatchOperation.firstOrNull()?.let { navigateToAutoMask(it) }
                            }) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = "Otomatik Maskele")
                            }
                            IconButton(onClick = {
                                selectedFilesForBatchOperation.firstOrNull()?.let { navigateToEdit(it) }
                            }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Düzenle")
                            }
                        }
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedFilesForBatchOperation.clear()
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Seçim Modunu Kapat")
                        }
                    } else {
                        IconButton(onClick = { showSortDialog = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sırala")
                        }
                        IconButton(onClick = { importImagesLauncher.launch("image/*") }) {
                            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Galeriden Ekle")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(onClick = {
                    pickImageForAILauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }) {
                    Icon(Icons.Filled.AutoAwesome, "AI Düzenleme için Resim Seç")
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        content = { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues)) {
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
                if (imageFiles.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Galerinizde henüz fotoğraf bulunmuyor.", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        contentPadding = PaddingValues(4.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(imageFiles, key = { it.absolutePath }) { file ->
                            val isSelected = selectedFilesForBatchOperation.contains(file)
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .aspectRatio(1f)
                                    .clip(MaterialTheme.shapes.medium)
                                    .combinedClickable(
                                        onClick = {
                                            if (isSelectionMode) {
                                                if (isSelected) {
                                                    selectedFilesForBatchOperation.remove(file)
                                                    if (selectedFilesForBatchOperation.isEmpty()) {
                                                        isSelectionMode = false
                                                    }
                                                } else {
                                                    selectedFilesForBatchOperation.add(file)
                                                }
                                            } else {
                                                // Normal tıklama: Detay ekranına git
                                                val encodedUri = Uri.encode(FileProvider.getUriForFile(context, "${context.packageName}.provider", file).toString())
                                                val route = Screen.PhotoDetail.route.replace("{${NavArgs.IMAGE_URI}}", encodedUri)
                                                navController.navigate(route)
                                            }
                                        },
                                        onLongClick = {
                                            if (!isSelectionMode) {
                                                isSelectionMode = true
                                                selectedFilesForBatchOperation.add(file)
                                            }
                                        }
                                    )
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent)
                            ) {
                                Image(
                                    painter = rememberAsyncImagePainter(model = file),
                                    contentDescription = file.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (isSelectionMode) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                        contentDescription = "Seçim Durumu",
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .background(
                                                if (isSelected) Color.White.copy(alpha = 0.5f) else Color.Black.copy(
                                                    alpha = 0.3f
                                                ),
                                                CircleShape
                                            )
                                            .padding(4.dp)
                                    )
                                } else {
                                    // Üç nokta menüsü için IconButton (sadece normal modda ve fare üzerinde değilken)
                                    IconButton(
                                        onClick = { showMoreMenuForFile = file },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .background(
                                                Color.Black.copy(alpha = 0.3f),
                                                CircleShape
                                            )
                                    ) {
                                        Icon(Icons.Filled.MoreVert, "Daha Fazla", tint = Color.White)
                                    }

                                    DropdownMenu(
                                        expanded = showMoreMenuForFile == file,
                                        onDismissRequest = { showMoreMenuForFile = null }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Düzenle") },
                                            onClick = { navigateToEdit(file) },
                                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = "Düzenle") }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Otomatik Maskele") },
                                            onClick = { navigateToAutoMask(file) },
                                            leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = "Otomatik Maskele") }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Paylaş") },
                                            onClick = {
                                                selectedFilesForBatchOperation.clear() // Önce temizle
                                                selectedFilesForBatchOperation.add(file) // Sadece bu dosyayı ekle
                                                shareSelectedFiles()
                                                selectedFilesForBatchOperation.clear() // İşlem sonrası temizle
                                                showMoreMenuForFile = null // Menüyü kapat
                                            },
                                            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = "Paylaş") }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Sil") },
                                            onClick = {
                                                selectedFilesForBatchOperation.clear() // Önce temizle
                                                selectedFilesForBatchOperation.add(file) // Sadece bu dosyayı ekle
                                                deleteSelectedFiles() // Bu zaten confirmation dialog gösterecek
                                                // selectedFilesForBatchOperation.clear() // confirmDeleteSelectedFiles içinde yapılıyor
                                                showMoreMenuForFile = null // Menüyü kapat
                                            },
                                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = "Sil") }
                                        )
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                            DropdownMenuItem(
                                                text = { Text("Duvar Kağıdı Yap") },
                                                onClick = {
                                                    try {
                                                        val imageUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                                        val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
                                                            addCategory(Intent.CATEGORY_DEFAULT)
                                                            setDataAndType(imageUri, "image/*")
                                                            putExtra("mimeType", "image/*")
                                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        }
                                                        context.startActivity(Intent.createChooser(intent, "Duvar kağıdı olarak ayarla"))
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Duvar kağıdı ayarlanamadı.", Toast.LENGTH_SHORT).show()
                                                        Log.e(TAG_GALLERY_SCREEN, "Duvar kağıdı ayarlama hatası", e)
                                                    }
                                                    showMoreMenuForFile = null // Menüyü kapat
                                                },
                                                leadingIcon = { Icon(Icons.Filled.Wallpaper, contentDescription = "Duvar Kağıdı Yap") }
                                            )
                                        }
                                    }
                                }

                                // Resmin altında dosya adını göster (opsiyonel)
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                                            )
                                        )
                                        .padding(vertical = 4.dp, horizontal = 6.dp)
                                ) {
                                    Text(
                                        text = file.name,
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (showSortDialog) {
                AlertDialog(
                    onDismissRequest = { showSortDialog = false },
                    title = { Text("Görselleri Sırala") },
                    text = {
                        Column {
                            GallerySortOrder.values().forEach { order ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (currentSortOrder != order) {
                                                currentSortOrder = order
                                                loadAndSortFiles()
                                            }
                                            showSortDialog = false
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = currentSortOrder == order,
                                        onClick = null // Tıklama Row tarafından yönetiliyor
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(when (order) {
                                        GallerySortOrder.DATE_DESC -> "Tarihe Göre (En Yeni)"
                                        GallerySortOrder.DATE_ASC -> "Tarihe Göre (En Eski)"
                                        GallerySortOrder.NAME_ASC -> "Ada Göre (A-Z)"
                                        GallerySortOrder.NAME_DESC -> "Ada Göre (Z-A)"
                                    })
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSortDialog = false }) {
                            Text("Kapat")
                        }
                    }
                )
            }

            if (showDeleteConfirmationDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmationDialog = false },
                    title = { Text("Silmeyi Onayla") },
                    text = { Text("${selectedFilesForBatchOperation.size} fotoğraf kalıcı olarak silinsin mi?") },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmDeleteSelectedFiles()
                            // showDeleteConfirmationDialog = false // confirmDeleteSelectedFiles içinde zaten yapılıyor
                        }) {
                            Text("Sil", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirmationDialog = false }) {
                            Text("İptal")
                        }
                    }
                )
            }
        }
    )
}

// GalleryScreen.kt Dosya Sonu
