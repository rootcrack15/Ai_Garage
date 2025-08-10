package com.rootcrack.aigarage.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val TAG_PHOTO_PREVIEW = "PhotoPreviewScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoPreviewScreen(
    bitmap: Bitmap? = null, // Navigasyondan direkt gelen Bitmap (öncelikli)
    filePath: String? = null, // Veya dosya yolu (URI string'i olarak gelebilir)
    navController: NavController
) {
    val context = LocalContext.current
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var fileDate by remember { mutableStateOf<String?>(null) } // Dosya tarihi veya oluşturulma zamanı
    var actualFileFromPath by remember { mutableStateOf<File?>(null) } // Paylaşım ve silme için File nesnesi
    val coroutineScope = rememberCoroutineScope()
    var isSavingToGallery by remember { mutableStateOf(false) }

    // --- Helper function to save bitmap to MediaStore ---
    suspend fun saveBitmapToMediaStoreHelper(
        context: Context,
        bitmapToSave: Bitmap,
        displayNamePrefix: String = "AIGarageImage"
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val timestamp = System.currentTimeMillis()
            val displayName = "${displayNamePrefix}_$timestamp.jpg"
            val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.DATE_ADDED, timestamp / 1000)
                put(MediaStore.Images.Media.DATE_MODIFIED, timestamp / 1000)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "AIGarageSaved")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                } else {
                    val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val targetDir = File(picturesDir, "AIGarageSaved")
                    if (!targetDir.exists()) {
                        if (!targetDir.mkdirs()) {
                            Log.e(TAG_PHOTO_PREVIEW, "AIGarageSaved klasörü oluşturulamadı.")
                            return@withContext false
                        }
                    }
                    val file = File(targetDir, displayName)
                    put(MediaStore.Images.Media.DATA, file.absolutePath)
                }
            }

            var imageUri: Uri? = null
            try {
                imageUri = context.contentResolver.insert(imageCollection, contentValues)
                if (imageUri == null) {
                    Log.e(TAG_PHOTO_PREVIEW, "MediaStore'a yeni resim girişi oluşturulamadı.")
                    return@withContext false
                }

                context.contentResolver.openOutputStream(imageUri)?.use { outputStream: OutputStream ->
                    if (!bitmapToSave.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)) {
                        Log.e(TAG_PHOTO_PREVIEW, "Bitmap MediaStore'a sıkıştırılamadı.")
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            imageUri?.let { context.contentResolver.delete(it, null, null) }
                        }
                        return@withContext false
                    }
                } ?: run {
                    Log.e(TAG_PHOTO_PREVIEW, "MediaStore için OutputStream açılamadı: $imageUri")
                    // IS_PENDING kullanılmıyorsa ve stream açılamadıysa kaydı sil
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        imageUri?.let { context.contentResolver.delete(it, null, null) }
                    }
                    return@withContext false
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(imageUri, contentValues, null, null)
                }

                Log.d(TAG_PHOTO_PREVIEW, "Resim başarıyla MediaStore'a kaydedildi: $imageUri")
                return@withContext true

            } catch (e: Exception) {
                Log.e(TAG_PHOTO_PREVIEW, "Resim MediaStore'a kaydedilirken hata", e)
                imageUri?.let {
                    try {
                        context.contentResolver.delete(it, null, null)
                    } catch (deleteEx: Exception) {
                        Log.e(TAG_PHOTO_PREVIEW, "Hata sonrası MediaStore kaydı silinirken hata", deleteEx)
                    }
                }
                return@withContext false
            }
        }
    }
    // --- End of helper function ---

    LaunchedEffect(filePath, bitmap) {
        Log.d(TAG_PHOTO_PREVIEW, "LaunchedEffect: filePath=$filePath, bitmap provided=${bitmap != null}")
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) // Daha okunaklı tarih formatı

        if (bitmap != null) {
            imageBitmap = bitmap
            if (filePath != null) {
                try {
                    val fileUri = Uri.parse(filePath)
                    if (fileUri.scheme == "file") {
                        val file = File(fileUri.path!!) // filePath null olamaz, yukarıda kontrol edildi
                        if (file.exists()) {
                            actualFileFromPath = file
                            fileDate = sdf.format(Date(file.lastModified()))
                            Log.d(TAG_PHOTO_PREVIEW, "Tarih dosyadan alındı ($filePath): $fileDate")
                        } else {
                            fileDate = sdf.format(Date()) // Dosya yoksa şimdiki zaman
                            Log.w(TAG_PHOTO_PREVIEW, "Dosya yolu ($filePath) mevcut değil, şimdiki zaman kullanılıyor.")
                        }
                    } else { // content URI veya başka bir şema olabilir
                        fileDate = sdf.format(Date()) // content URI için dosya tarihi almak zor, şimdiki zamanı kullan
                        Log.d(TAG_PHOTO_PREVIEW, "File URI değil ($filePath), tarih şimdiki zaman olarak ayarlandı.")
                    }
                } catch (e: Exception) {
                    fileDate = sdf.format(Date())
                    Log.e(TAG_PHOTO_PREVIEW, "Dosya yolu ($filePath) işlenirken hata, şimdiki zaman kullanılıyor: ${e.message}")
                }
            } else {
                fileDate = sdf.format(Date()) // Bitmap var ama dosya yolu yoksa şimdiki zaman
                Log.d(TAG_PHOTO_PREVIEW, "Bitmap var ama dosya yolu yok, tarih şimdiki zaman olarak ayarlandı.")
            }
        } else if (filePath != null) {
            try {
                val fileUri = Uri.parse(filePath)
                Log.d(TAG_PHOTO_PREVIEW, "filePath URI şeması: ${fileUri.scheme}")
                var loadedBitmap: Bitmap? = null

                if (fileUri.scheme == "file") {
                    val file = File(fileUri.path!!)
                    if (file.exists()) {
                        actualFileFromPath = file
                        loadedBitmap = BitmapFactory.decodeFile(file.absolutePath)
                        fileDate = sdf.format(Date(file.lastModified()))
                        Log.d(TAG_PHOTO_PREVIEW, "Resim ve tarih dosyadan yüklendi ($filePath): $fileDate")
                    } else {
                        loadedBitmap = null
                        Log.e(TAG_PHOTO_PREVIEW, "Dosya bulunamadı: $filePath")
                    }
                } else if (fileUri.scheme == "content") {
                    try {
                        context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                            loadedBitmap = BitmapFactory.decodeStream(inputStream)
                            Log.d(TAG_PHOTO_PREVIEW, "Resim content URI'den yüklendi: $filePath")
                            // Content URI için dosya tarihini almak MediaStore sorgusu gerektirebilir.
                            // Şimdilik yükleme zamanını veya yaklaşık bir zamanı kullanıyoruz.
                            fileDate = sdf.format(Date())
                        } ?: run {
                            loadedBitmap = null
                            Log.e(TAG_PHOTO_PREVIEW, "Content URI'den stream açılamadı: $filePath")
                        }
                    } catch (e: Exception) {
                        loadedBitmap = null
                        Log.e(TAG_PHOTO_PREVIEW, "Content URI ($filePath) işlenirken hata: ${e.message}")
                    }
                    actualFileFromPath = null // Content URI ise direkt File nesnesi olmayabilir.
                } else {
                    loadedBitmap = null
                    Log.e(TAG_PHOTO_PREVIEW, "Desteklenmeyen URI şeması veya dosya yolu: $filePath")
                }
                imageBitmap = loadedBitmap
                if (imageBitmap == null) { // Eğer resim yüklenemediyse, tarih de "Bilinmiyor" olabilir
                    fileDate = "Bilinmiyor"
                }
            } catch (e: Exception) {
                Log.e(TAG_PHOTO_PREVIEW, "Resim yüklenirken hata ($filePath): ${e.message}")
                imageBitmap = null
                fileDate = "Yüklenemedi"
            }
        } else {
            // Ne bitmap ne de filePath var
            fileDate = "Veri Yok"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        fileDate ?: "Önizleme", // fileDate null ise "Önizleme" göster
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    // Paylaş Butonu
                    if (imageBitmap != null) { // Paylaşmak için bir resim olmalı
                        IconButton(onClick = {
                            var uriToShareAttempt: Uri? = null
                            if (actualFileFromPath != null) {
                                try {
                                    uriToShareAttempt = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        actualFileFromPath!!
                                    )
                                } catch (e: IllegalArgumentException) {
                                    Log.e(TAG_PHOTO_PREVIEW, "FileProvider için URI alınamadı (actualFileFromPath): ${e.message}")
                                }
                            }

                            if (uriToShareAttempt == null && filePath != null) {
                                // Eğer actualFileFromPath'den URI alınamadıysa veya null ise
                                // ve filePath varsa, onu parse etmeyi dene.
                                // Bu, content:// URI'leri veya file:// URI'leri (FileProvider olmadan) için bir geri dönüş olabilir.
                                try {
                                    uriToShareAttempt = Uri.parse(filePath)
                                } catch (e: Exception){
                                    Log.e(TAG_PHOTO_PREVIEW, "filePath parse edilirken hata: $filePath ", e)
                                }
                            }

                            if (uriToShareAttempt != null) {
                                shareImageUri(context, uriToShareAttempt)
                            } else if (actualFileFromPath == null && filePath == null && imageBitmap != null) {
                                // Eğer ne actualFileFromPath ne de filePath varsa, ama bitmap varsa,
                                // bitmap'i geçici dosyaya kaydet ve paylaş.
                                Log.d(TAG_PHOTO_PREVIEW, "Paylaşım için dosya yolu yok, bitmap'ten geçici dosya oluşturuluyor.")
                                coroutineScope.launch {
                                    val tempFileUri = saveBitmapToTempFileForSharing(context, imageBitmap!!)
                                    if (tempFileUri != null) {
                                        shareImageUri(context, tempFileUri)
                                    } else {
                                        Toast.makeText(context, "Paylaşım için geçici dosya oluşturulamadı.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                // Hiçbir URI kaynağı bulunamadıysa.
                                Toast.makeText(context, "Paylaşılacak bir resim kaynağı bulunamadı.", Toast.LENGTH_SHORT).show()
                                Log.w(TAG_PHOTO_PREVIEW, "Paylaşım için URI bulunamadı. actualFileFromPath: $actualFileFromPath, filePath: $filePath")
                            }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Paylaş")
                        }
                    }

                    // Galeriye Kaydet Butonu
                    imageBitmap?.let { bmp ->
                        if (isSavingToGallery) {
                            CircularProgressIndicator(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        } else {
                            IconButton(onClick = {
                                isSavingToGallery = true
                                coroutineScope.launch {
                                    val success = saveBitmapToMediaStoreHelper(context, bmp, "AIGarage")
                                    withContext(Dispatchers.Main) {
                                        if (success) {
                                            Toast.makeText(context, "Fotoğraf galeriye (Pictures/AIGarageSaved) kaydedildi!", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Fotoğraf galeriye kaydedilemedi.", Toast.LENGTH_SHORT).show()
                                        }
                                        isSavingToGallery = false
                                    }
                                }
                            }) {
                                Icon(Icons.Filled.Save, contentDescription = "Galeriye Kaydet")
                            }
                        }
                    }

                    // Silme Butonu (Sadece uygulama özel dizinindeki dosyalar için)
                    actualFileFromPath?.let { file ->
                        val appSpecificDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.resolve("AIGarage")
                        // Silme butonu sadece dosya bizim AIGarage klasörümüzdeyse ve gerçekten bir dosya ise gösterilsin.
                        if (appSpecificDir != null && file.isFile && file.absolutePath.startsWith(appSpecificDir.absolutePath)) {
                            if (file.exists()) {
                                IconButton(onClick = {
                                    try {
                                        val deleted = file.delete()
                                        if (deleted) {
                                            Log.d(TAG_PHOTO_PREVIEW, "Dosya silindi: ${file.absolutePath}")
                                            Toast.makeText(context, "Fotoğraf silindi", Toast.LENGTH_SHORT).show()
                                            // Ekrandaki resmi ve referansları temizle
                                            imageBitmap = null
                                            actualFileFromPath = null
                                            fileDate = "Silindi"
                                            // İsteğe bağlı olarak bir önceki ekrana dönülebilir.
                                            // navController.popBackStack()
                                        } else {
                                            Log.e(TAG_PHOTO_PREVIEW, "Dosya silinemedi: ${file.absolutePath}")
                                            Toast.makeText(context, "Fotoğraf silinemedi", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: SecurityException) {
                                        Log.e(TAG_PHOTO_PREVIEW, "Dosya silinirken güvenlik hatası: ${e.message}")
                                        Toast.makeText(context, "Silme izni yok: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Sil")
                                }
                            }
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
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            imageBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Tam Ekran Fotoğraf",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } ?: Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isSavingToGallery) { // Eğer galeriye kaydetme işlemi devam ediyorsa burada da bir gösterge olabilir
                    CircularProgressIndicator()
                    Text("Kaydediliyor...", modifier = Modifier.padding(top = 8.dp))
                } else {
                    Text(fileDate ?: "Görüntülenecek fotoğraf yok veya yüklenemedi.")
                }
            }
        }
    }
}

// Bu fonksiyon URI alacak şekilde güncellendi
private fun shareImageUri(context: Context, uri: Uri) {
    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_STREAM, uri)
        type = context.contentResolver.getType(uri) ?: "image/*" // MIME türünü dinamik al veya varsayılan kullan
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(shareIntent, "Fotoğrafı Paylaş"))
        Log.d(TAG_PHOTO_PREVIEW, "Paylaşım niyeti başlatıldı: $uri")
    } catch (e: Exception) {
        Log.e(TAG_PHOTO_PREVIEW, "Paylaşım niyeti başlatılırken hata: ${e.message}")
        android.widget.Toast.makeText(context, "Paylaşım başlatılamadı: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
    }
}

// Paylaşım için bitmap'i geçici bir dosyaya kaydetme fonksiyonu
private suspend fun saveBitmapToTempFileForSharing(context: Context, bitmap: Bitmap): Uri? {
    return withContext(Dispatchers.IO) {
        val tempCacheDir = File(context.cacheDir, "shared_images")
        if (!tempCacheDir.exists()) {
            tempCacheDir.mkdirs()
        }
        val tempFile = File(tempCacheDir, "share_${System.currentTimeMillis()}.jpg")
        try {
            FileOutputStream(tempFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }
            FileProvider.getUriForFile(context, "${context.packageName}.provider", tempFile)
        } catch (e: Exception) {
            Log.e(TAG_PHOTO_PREVIEW, "Paylaşım için geçici dosya oluşturulurken hata", e)
            null
        }
    }
}
