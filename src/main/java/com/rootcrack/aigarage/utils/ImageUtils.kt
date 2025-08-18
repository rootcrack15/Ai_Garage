// C:/Users/amilcakmak/AndroidStudioProjects/AIGarage/app/src/main/java/com/rootcrack/aigarage/utils/ImageUtils.kt
package com.rootcrack.aigarage.utils

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Base64
import androidx.core.graphics.applyCanvas
import java.io.ByteArrayOutputStream

object ImageUtils {

    fun invertMaskBitmap(originalMask: Bitmap): Bitmap {
        val invertedBitmap = Bitmap.createBitmap(originalMask.width, originalMask.height, originalMask.config!!)
        val paint = Paint()
        // Renkleri tersine çevirmek için ColorMatrix kullanıldı
        // Siyah (0) -> Beyaz (255)
        // Beyaz (255) -> Siyah (0)
        val matrix = ColorMatrix(floatArrayOf(
            -1f,  0f,  0f,  0f, 255f,
            0f, -1f,  0f,  0f, 255f,
            0f,  0f, -1f,  0f, 255f,
            0f,  0f,  0f,  1f,   0f  // Alfa kanalı dokunulmaz
        ))
        paint.colorFilter = ColorMatrixColorFilter(matrix)

        invertedBitmap.applyCanvas {
            drawBitmap(originalMask, 0f, 0f, paint)
        }
        return invertedBitmap
    }

    fun convertBitmapToBase64(bitmap: Bitmap, quality: Int = 100): String {
        val outputStream = ByteArrayOutputStream()
        // Maskeler için PNG genellikle daha iyidir çünkü kayıpsızdır.
        // Eğer kalite önemli değilse JPEG de kullanılabilir ama maske için PNG önerilir.
        bitmap.compress(Bitmap.CompressFormat.PNG, quality, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP) // NO_WRAP, Base64 çıktısında satır sonu karakterlerini engeller.
    }

    fun decodeBase64ToBitmap(base64String: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: IllegalArgumentException) {
            // Log error or handle it
            e.printStackTrace()
            null
        }
    }
}