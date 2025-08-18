package com.rootcrack.aigarage.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

object FileUtil {

    private const val TAG = "FileUtil"
    private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

    const val APP_MEDIA_SUBDIRECTORY = "AIGarage"
    const val DIRECTORY_NAME_APP_SPECIFIC_SUBFOLDER = APP_MEDIA_SUBDIRECTORY


    /**
     * CameraX için ImageCapture.OutputFileOptions oluşturur.
     * Android 10 (Q) ve üzeri için MediaStore'u kullanır ve IS_PENDING mekanizmasını destekler.
     * Android 9 (P) ve altı için uygulamanın harici özel dizinini kullanır.
     */
    fun getPhotoOutputFileOptions(context: Context): ImageCapture.OutputFileOptions? {
        val appName = context.applicationInfo.loadLabel(context.packageManager).toString()
        val timeStamp = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
        val fileName = "IMG_${timeStamp}.jpg"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 (API 29) ve üzeri: MediaStore'u kullan
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + appName + File.separator + APP_MEDIA_SUBDIRECTORY)
                // IS_PENDING durumu, dosya yazma işlemi bitene kadar diğer uygulamaların erişimini kısıtlar.
                // Fotoğraf kaydedildikten sonra makeFileVisibleInGallery içinde 0'a çekilecek.
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val contentResolver = context.contentResolver
            try {
                val contentUri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                ImageCapture.OutputFileOptions.Builder(contentResolver, contentUri, contentValues).build()
            } catch (e: Exception) {
                Log.e(TAG, "MediaStore için OutputFileOptions oluşturulamadı (Q+): ${e.message}", e)
                null
            }
        } else {
            // Android 9 (API 28) ve altı: Harici özel depolama dizinini kullan
            val mediaDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), APP_MEDIA_SUBDIRECTORY)
            if (!mediaDir.exists() && !mediaDir.mkdirs()) {
                Log.e(TAG, "Medya dizini oluşturulamadı: ${mediaDir.absolutePath}")
                return null
            }
            val photoFile = File(mediaDir, fileName)
            ImageCapture.OutputFileOptions.Builder(photoFile).build()
        }
    }

    /**
     * MediaStore'a kaydedilen bir dosyanın galeride görünür olmasını sağlar.
     * Android 10 (Q) ve üzeri için IS_PENDING işaretini 0'a günceller.
     * Android 9 (P) ve altı için MediaScannerConnection'ı kullanır.
     */
    fun makeFileVisibleInGallery(context: Context, imageUri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            try {
                context.contentResolver.update(imageUri, contentValues, null, null)
                Log.d(TAG, "MediaStore'da IS_PENDING güncellendi: $imageUri")
            } catch (e: Exception) {
                Log.e(TAG, "MediaStore'da IS_PENDING güncellenirken hata: ${e.message}", e)
                scanFile(context, imageUri, "image/jpeg")
            }
        } else {
            val filePath = imageUri.path ?: getPathFromUri(context, imageUri)
            if (filePath != null) {
                scanFile(context, Uri.fromFile(File(filePath)), "image/jpeg")
            } else {
                Log.w(TAG, "Dosya yolu URI'den alınamadı, galeride görünürlük için taranmıyor: $imageUri")
            }
        }
    }

    /**
     * Belirli bir dosyayı MediaScanner ile tarayarak galeride görünür hale getirir.
     */
    private fun scanFile(context: Context, uriToScan: Uri, mimeType: String?) {
        val path = if ("file".equals(uriToScan.scheme, ignoreCase = true)) {
            uriToScan.path
        } else {
            getPathFromUri(context, uriToScan)
        }

        if (path != null) {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(path),
                arrayOf(mimeType)
            ) { scannedPath, scannedUri ->
                Log.i(TAG, "Dosya tarandı. Path: $scannedPath, URI: $scannedUri")
            }
        } else {
            @Suppress("DEPRECATION") val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            mediaScanIntent.data = uriToScan
            context.sendBroadcast(mediaScanIntent)
            Log.i(TAG, "MediaScanner'a tarama isteği gönderildi (Intent ile): $uriToScan")
        }
    }

    /**
     * content:// URI'den dosya yolunu (path) almaya çalışır.
     */
    private fun getPathFromUri(context: Context, uri: Uri): String? {
        if ("content".equals(uri.scheme, ignoreCase = true)) {
            try {
                context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DATA), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                        return cursor.getString(columnIndex)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Content URI'den dosya yolu alınırken hata: $uri", e)
            }
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }
        return null
    }

    /**
     * Uygulamanın özel dizininde yeni bir resim dosyası oluşturur.
     */
    fun createImageFile(context: Context): File? {
        return try {
            val timeStamp = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
            val imageFileName = "JPEG_${timeStamp}_"
            val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES + File.separator + APP_MEDIA_SUBDIRECTORY)
            if (storageDir != null && (!storageDir.exists() && !storageDir.mkdirs())) {
                Log.e(TAG, "Medya dizini oluşturulamadı: ${storageDir.absolutePath}")
                return null
            }
            File.createTempFile(imageFileName, ".jpg", storageDir)
        } catch (e: Exception) {
            Log.e(TAG, "Resim dosyası oluşturulurken hata", e)
            null
        }
    }

    /**
     * Verilen bir dosya için FileProvider URI'si oluşturur.
     */
    fun getUriForFile(context: Context, file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    /**
     * Verilen bir URI'deki içeriği uygulamanın özel depolama alanındaki belirtilen bir alt dizine kopyalar.
     * Kopyalanan dosya için benzersiz bir isim oluşturur.
     *
     * @param context Context nesnesi.
     * @param uri Kopyalanacak içeriğin URI'si.
     * @param subDirectoryName Uygulamanın özel Pictures dizini altında oluşturulacak alt dizinin adı.
     * @return Kopyalanan dosyanın File nesnesi veya hata durumunda null.
     */
    fun copyUriToAppSpecificDirectory(context: Context, uri: Uri, subDirectoryName: String): File? {
        val inputStream: InputStream?
        try {
            inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "URI'den InputStream açılamadı: $uri")
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "URI'den InputStream açılırken hata: $uri", e)
            return null
        }

        val timeStamp = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
        val fileName = "COPIED_${timeStamp}_${uri.lastPathSegment ?: "file"}.jpg" // Orijinal isme benzer bir isim

        val parentDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        if (parentDir == null) {
            Log.e(TAG, "Uygulamanın harici özel Pictures dizini alınamadı.")
            return null
        }

        val targetDirectory = File(parentDir, subDirectoryName)
        if (!targetDirectory.exists()) {
            if (!targetDirectory.mkdirs()) {
                Log.e(TAG, "Hedef alt dizin oluşturulamadı: ${targetDirectory.absolutePath}")
                return null
            }
        }

        val outputFile = File(targetDirectory, fileName)

        try {
            FileOutputStream(outputFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            inputStream.close() // InputStream'i burada kapatmak daha güvenli
            Log.d(TAG, "Dosya başarıyla kopyalandı: ${outputFile.absolutePath}")
            return outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Dosya kopyalanırken hata: ${outputFile.absolutePath}", e)
            inputStream.close() // Hata durumunda da inputStream'i kapatmaya çalış
            if (outputFile.exists()) {
                outputFile.delete()
            }
            return null
        }
    }
}
