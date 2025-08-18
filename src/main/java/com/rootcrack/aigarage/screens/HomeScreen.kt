package com.rootcrack.aigarage.screens

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.rootcrack.aigarage.navigation.Screen
import com.rootcrack.aigarage.utils.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var imageFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedImageFile by remember { mutableStateOf<File?>(null) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }

    fun loadProcessedImages() {
        isLoading = true
        coroutineScope.launch(Dispatchers.IO) {
            val processedDir = context.getExternalFilesDir(FileUtil.APP_MEDIA_SUBDIRECTORY)
            val files: List<File> = processedDir?.listFiles { file ->
                file.isFile && (file.extension.equals("png", true) || file.extension.equals("jpg", true))
            }?.sortedByDescending { it.lastModified() } ?: emptyList()

            withContext(Dispatchers.Main) {
                imageFiles = files
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit, navController.currentBackStackEntry) {
        loadProcessedImages()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "AI Garage Keşfet") },
                navigationIcon = {
                    Icon(
                        imageVector = Icons.Filled.SportsMotorsports,
                        contentDescription = "Uygulama Logosu",
                        modifier = Modifier.padding(start = 12.dp, end = 15.dp).size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Camera.route) }) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Kamera")
                    }
                    IconButton(onClick = { Log.d("HomeScreen", "Notifications icon clicked") }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Bildirimler")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding).background(MaterialTheme.colorScheme.background)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (imageFiles.isEmpty()) {
                Text(
                    text = "Henüz AI ile işlenmiş bir resim yok.\nKamera ikonuna dokunarak başlayın!",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    contentPadding = PaddingValues(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(imageFiles, key = { it.absolutePath }) { file ->
                        Image(
                            painter = rememberAsyncImagePainter(model = file),
                            contentDescription = "AI Sonucu: ${file.name}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).clickable { selectedImageFile = file }
                        )
                    }
                }
            }

            selectedImageFile?.let { file ->
                ImageDetailDialog(
                    file = file,
                    onDismiss = { selectedImageFile = null },
                    onDeleteRequest = { fileToDelete = it }
                )
            }

            fileToDelete?.let { file ->
                AlertDialog(
                    onDismissRequest = { fileToDelete = null },
                    title = { Text("Resmi Sil") },
                    text = { Text("Bu resmi kalıcı olarak silmek istediğinizden emin misiniz?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    if (file.delete()) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Resim silindi.", Toast.LENGTH_SHORT).show()
                                            loadProcessedImages()
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Resim silinemedi.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                fileToDelete = null
                                selectedImageFile = null // Detay penceresini de kapat
                            }
                        ) { Text("Sil", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { fileToDelete = null }) { Text("İptal") }
                    }
                )
            }
        }
    }
}

@Composable
private fun ImageDetailDialog(
    file: File,
    onDismiss: () -> Unit,
    onDeleteRequest: (File) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    val imageUri = remember(file) {
        try {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (e: Exception) {
            Log.e("HomeScreen", "ImageDetailDialog: FileProvider URI oluşturulamadı.", e)
            null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f)) // Blur efekti için arka plan
                .clickable(onClick = onDismiss), // Dışarı tıklayınca kapat
            contentAlignment = Alignment.Center
        ) {
            // Çapraz "AI Garage" yazısı
            Text(
                text = "AI GARAGE",
                fontSize = 80.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.1f),
                modifier = Modifier.rotate(-45f)
            )

            // Resim ve kontrolleri içeren ana kutu
            Box(
                modifier = Modifier
                    .padding(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                if (imageUri != null) {
                    Image(
                        painter = rememberAsyncImagePainter(model = imageUri),
                        contentDescription = "Detaylı Resim",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    )
                    // Kapatma (X) butonu
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat", tint = MaterialTheme.colorScheme.onSurface)
                    }

                    // Silme butonu
                    IconButton(
                        onClick = { onDeleteRequest(file) },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Sil", tint = Color.White)
                    }

                    // Kaydetme butonu
                    IconButton(
                        onClick = {
                            if (isSaving) return@IconButton
                            isSaving = true
                            coroutineScope.launch {
                                val success = saveImageToGallery(context, imageUri)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, if (success) "Resim galeriye kaydedildi!" else "Resim kaydedilemedi.", Toast.LENGTH_SHORT).show()
                                    isSaving = false
                                }
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .background(Color.White.copy(alpha = 0.7f), CircleShape)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Save, contentDescription = "Galeriye Kaydet", tint = Color.Black)
                        }
                    }
                } else {
                    Text("Resim yüklenemedi.", modifier = Modifier.align(Alignment.Center).padding(24.dp))
                }
            }
        }
    }
}

private suspend fun saveImageToGallery(context: Context, uri: Uri): Boolean {
    return withContext(Dispatchers.IO) {
        val displayName = "AIGarage_Keşfet_${System.currentTimeMillis()}.png"
        val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "AIGarage")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        var newImageUri: Uri? = null
        try {
            newImageUri = context.contentResolver.insert(imageCollection, contentValues)
                ?: return@withContext false

            context.contentResolver.openOutputStream(newImageUri)?.use { outputStream: OutputStream ->
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.copyTo(outputStream)
                } ?: return@withContext false
            } ?: return@withContext false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(newImageUri, contentValues, null, null)
            }
            true
        } catch (e: Exception) {
            Log.e("HomeScreen", "Resim galeriye kaydedilirken hata", e)
            newImageUri?.let { context.contentResolver.delete(it, null, null) }
            false
        }
    }
}
