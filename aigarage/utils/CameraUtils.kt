package com.rootcrack.aigarage.utils // veya fonksiyonunuzun bulunduğu paket

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor

const val TAG_CAMERA_UTILS = "CameraUtils" // Bu dosya için özel TAG

fun takePicture(
    context: Context,
    controller: LifecycleCameraController,
    onImageCaptured: (savedUri: Uri?, isVideo: Boolean) -> Unit,
    onErrorOccurred: (imageCaptureException: ImageCaptureException) -> Unit // onError -> onErrorOccurred olarak değiştirdim, daha açıklayıcı
) {
    val mainExecutor: Executor = ContextCompat.getMainExecutor(context)
    val simpleDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    val timeStamp = simpleDateFormat.format(Date())
    val displayName = "AIGARAGE_${timeStamp}.jpg"

    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 (Q) ve üzeri için Resimler/AIGarage klasörüne kaydet
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "AIGarage")
            put(MediaStore.MediaColumns.IS_PENDING, 1) // Dosya yazılırken beklemede olarak işaretle
        } else {
            // Android 9 (Pie) ve altı için:
            // WRITE_EXTERNAL_STORAGE izni Manifest'te olmalı ve kullanıcı tarafından verilmiş olmalı.
            // Bu durumda MediaStore genellikle DIRECTORY_PICTURES altına doğrudan yazar.
            // "AIGarage" alt klasörü için ek FileOutputStream mantığı gerekebilir,
            // ancak ImageCapture.OutputFileOptions ile ContentResolver kullanırken bu genellikle ihmal edilir
            // ve varsayılan resimler klasörüne yazılır.
            // ÖNEMLİ: Bu blokta hala MediaStore.Images.Media.EXTERNAL_CONTENT_URI'ye yazılacak.
        }
    }

    val outputOptions = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    ).build()

    controller.takePicture(
        outputOptions,
        mainExecutor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val savedUri = outputFileResults.savedUri
                Log.d(TAG_CAMERA_UTILS, "Fotoğraf MediaStore'a kaydedildi (ön URI): $savedUri")

                if (savedUri == null) {
                    Log.e(TAG_CAMERA_UTILS, "onImageSaved çağrıldı ancak savedUri null.")
                    onErrorOccurred(
                        ImageCaptureException(
                            ImageCapture.ERROR_FILE_IO,
                            "Saved URI was null after capture.",
                            null
                        )
                    )
                    return
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val updateValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0) // Beklemede işaretini kaldır
                    }
                    try {
                        val updatedRows = context.contentResolver.update(savedUri, updateValues, null, null)
                        if (updatedRows > 0) {
                            Log.d(TAG_CAMERA_UTILS, "MediaStore IS_PENDING güncellendi: $savedUri")
                        } else {
                            Log.w(TAG_CAMERA_UTILS, "MediaStore IS_PENDING güncellenemedi (etkilenen satır yok): $savedUri")
                            // Bu durumda bile URI geçerli olabilir, galeri bazen yine de algılar.
                        }
                    } catch (e: Exception) {
                        Log.e(TAG_CAMERA_UTILS, "MediaStore IS_PENDING güncellenirken hata: $savedUri", e)
                        // Bu hata olsa bile URI ile devam etmeyi deneyebiliriz,
                        // çünkü dosya fiziksel olarak yazılmış olabilir.
                    }
                }

                // MediaStore'a bildirildikten sonra (IS_PENDING=0 ile veya eski sürümlerde direkt kayıtla)
                // çoğu galeri uygulaması bunu algılamalıdır.
                // MediaScannerConnection genellikle IS_PENDING düzgün çalışıyorsa veya
                // File Output ile doğrudan dosyaya yazıp sonra MediaStore'a ekliyorsanız daha çok gereklidir.
                // Ancak, bazı inatçı durumlar için veya emin olmak adına eklenebilir.
                // Bunun için güvenilir bir dosya yolu gereklidir.
                // `savedUri` bir content:// URI'sidir ve doğrudan `File(savedUri.path)` her zaman çalışmaz.
                /*
                val filePath = getPathFromUri(context, savedUri) // Bu yardımcı fonksiyonu implemente etmeniz gerekir
                if (filePath != null) {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(filePath),
                        arrayOf("image/jpeg") // MIME type
                    ) { path, uri ->
                        Log.d(TAG_CAMERA_UTILS, "Media Scanner tamamlandı. Taranan yol: $path, Sonuç URI: $uri")
                        // onImageCaptured içindeki savedUri'nin bu yeni uri ile güncellenmesi gerekebilir,
                        // ancak genellikle ilk savedUri yeterlidir.
                    }
                } else {
                    Log.w(TAG_CAMERA_UTILS, "MediaScanner için dosya yolu alınamadı: $savedUri")
                }
                */

                Log.i(TAG_CAMERA_UTILS, "Fotoğraf işlendi, URI: $savedUri")
                onImageCaptured(savedUri, false)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG_CAMERA_UTILS, "ImageCapture.OnImageSavedCallback Hata Kodu: ${exception.imageCaptureError}", exception)
                onErrorOccurred(exception)
            }
        }
    )
}

// İsteğe Bağlı Yardımcı Fonksiyon (getPathFromUri):
// Content URI'den gerçek dosya yolunu almak karmaşık olabilir ve her zaman garanti edilmez.
// Genellikle MediaStore'dan sorgu yaparak _data sütununu almayı içerir.
// Ancak, Scoped Storage ile bu sütuna erişim kısıtlanmıştır.
// Bu yüzden MediaScanner'ı content URI ile doğrudan kullanmak yerine,
// IS_PENDING'in doğru çalışmasına güvenmek daha modern bir yaklaşımdır.
/*
fun getPathFromUri(context: Context, uri: Uri): String? {
    if ("content".equals(uri.scheme, ignoreCase = true)) {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    return cursor.getString(columnIndex)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG_CAMERA_UTILS, "getPathFromUri hatası", e)
        }
    } else if ("file".equals(uri.scheme, ignoreCase = true)) {
        return uri.path
    }
    return null
}
*/

