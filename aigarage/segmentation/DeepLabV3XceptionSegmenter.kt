package com.rootcrack.aigarage.segmentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * DeepLabV3XceptionSegmenter
 * - Bu sınıf doğrudan .tflite modelini (Interpreter kullanarak) yükler ve çalıştırır.
 * - Modelin input: [1, 1025, 2049, 3] float32 değer aralığı -1..1
 * - Modelin output: [1, 257, 513, 19] float32 (19 kanal: her kanal bir sınıf için confidence)
 *
 * NOTLAR:
 * - Bu dosya, TF Lite Task Library kullanımına bağımlı olmadan Interpreter üzerinden çalışır.
 * - Bellek gereksinimi yüksektir (ör. input / output buffer boyutları büyük). Cihaz belleğine dikkat et.
 */

private const val TAG = "DeepLabV3Seg"

data class SegmentationResult(
    val maskBitmap: Bitmap?, // binary mask (white = selected class, black = background) scaled to original size
    val meanConfidence: Float,
    val acceptedAutomatically: Boolean
)

class DeepLabV3XceptionSegmenter(
    private val context: Context,
    private val modelAssetPath: String = "deeplabv3_xception.tflite", // değiştirilebilir
    private val targetClassIndex: Int = 0, // otomatik maskede hedef sınıfın index'i (model label listesine göre)
    private val acceptThreshold: Float = 0.60f // otomatik kabul eşik değeri
) {

    private var interpreter: Interpreter? = null

    // model input/output sabitleri
    private val INPUT_HEIGHT = 1025
    private val INPUT_WIDTH = 2049
    private val INPUT_CHANNELS = 3

    private val OUT_HEIGHT = 257
    private val OUT_WIDTH = 513
    private val OUT_CHANNELS = 19

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (interpreter != null) return@withContext true
            val mapped = loadModelFile(context, modelAssetPath)
            val opts = Interpreter.Options()
            // Eğer istersen GPU delegate veya NNAPI ekleyebilirsin burada.
            interpreter = Interpreter(mapped, opts)
            Log.i(TAG, "Interpreter yüklendi: $modelAssetPath")
            return@withContext true
        } catch (e: IOException) {
            Log.e(TAG, "Model yüklenemedi: ${e.message}", e)
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Interpreter başlatılırken hata: ${e.message}", e)
            return@withContext false
        }
    }

    fun close() {
        try {
            interpreter?.close()
            interpreter = null
        } catch (e: Exception) {
            Log.w(TAG, "Interpreter kapatılırken hata: ${e.message}")
        }
    }

    /**
     * Ana segment fonksiyonu.
     * Bitmap, modelin beklediği giriş boyutuna (1025x2049) uygun olarak yeniden ölçeklenir,
     * float32 -1..1 normalize edilir, model çalıştırılır, ardından hedef sınıf için mean confidence bulunur
     * ve binary maske oluşturulur. Mask, orijinal bitmap boyutuna ölçeklenmiş olarak döndürülür.
     */
    suspend fun segment(bitmap: Bitmap): SegmentationResult = withContext(Dispatchers.Default) {
        val interp = interpreter
        if (interp == null) {
            Log.e(TAG, "Interpreter initialize edilmemiş. initialize() çağır.")
            return@withContext SegmentationResult(null, 0f, false)
        }

        // 1) Girdi bitmap'i modelin beklediği boyuta yeniden boyutlandır
        val inputBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_WIDTH, INPUT_HEIGHT, true)

        // 2) Input FloatBuffer hazırla (float32)
        val inputSize = 1L * INPUT_HEIGHT * INPUT_WIDTH * INPUT_CHANNELS
        val inputBuffer = ByteBuffer.allocateDirect((inputSize * 4).toInt()).order(ByteOrder.nativeOrder())
        // Normalize -1..1 : (pixel / 127.5) - 1 => (pixel - 127.5) / 127.5
        val intValues = IntArray(INPUT_WIDTH * INPUT_HEIGHT)
        inputBitmap.getPixels(intValues, 0, INPUT_WIDTH, 0, 0, INPUT_WIDTH, INPUT_HEIGHT)
        var pixelIndex = 0
        for (y in 0 until INPUT_HEIGHT) {
            for (x in 0 until INPUT_WIDTH) {
                val v = intValues[pixelIndex++] // ARGB
                val r = ((v shr 16) and 0xFF).toFloat()
                val g = ((v shr 8) and 0xFF).toFloat()
                val b = (v and 0xFF).toFloat()
                // normalize to [-1,1]
                inputBuffer.putFloat((r - 127.5f) / 127.5f)
                inputBuffer.putFloat((g - 127.5f) / 127.5f)
                inputBuffer.putFloat((b - 127.5f) / 127.5f)
            }
        }
        inputBuffer.rewind()

        // 3) Çıktı buffer oluştur
        val outputSize = 1 * OUT_HEIGHT * OUT_WIDTH * OUT_CHANNELS
        val outputBuffer = Array(1) { Array(OUT_HEIGHT) { Array(OUT_WIDTH) { FloatArray(OUT_CHANNELS) } } }
        // Interpreter.run için multidimensional array uygun ve güvenlidir.

        // 4) Modeli çalıştır
        try {
            interp.run(inputBuffer, outputBuffer)
        } catch (e: Exception) {
            Log.e(TAG, "Model çalıştırılırken hata: ${e.message}", e)
            return@withContext SegmentationResult(null, 0f, false)
        }

        // 5) Output'u düz bir float array'e çevir ve target class için mean confidence hesapla
        val flatOut = FloatArray(OUT_HEIGHT * OUT_WIDTH * OUT_CHANNELS)
        var idx = 0
        for (h in 0 until OUT_HEIGHT) {
            for (w in 0 until OUT_WIDTH) {
                val arr = outputBuffer[0][h][w]
                for (c in 0 until OUT_CHANNELS) {
                    flatOut[idx++] = arr[c]
                }
            }
        }

        // Mean confidence for target class
        var sum = 0f
        var count = 0
        for (i in 0 until OUT_HEIGHT) {
            for (j in 0 until OUT_WIDTH) {
                val valForClass = outputBuffer[0][i][j][targetClassIndex]
                sum += valForClass
                count++
            }
        }
        val meanConfidence = if (count > 0) sum / count else 0f

        // 6) Binary mask oluştur (OUT_HEIGHT x OUT_WIDTH). Eşik: 0.5 (kullanıcı isteğine göre değiştir)
        val perPixelThreshold = 0.5f
        val mask = Bitmap.createBitmap(OUT_WIDTH, OUT_HEIGHT, Config.ARGB_8888)
        for (y in 0 until OUT_HEIGHT) {
            for (x in 0 until OUT_WIDTH) {
                val conf = outputBuffer[0][y][x][targetClassIndex]
                if (conf >= perPixelThreshold) mask.setPixel(x, y, Color.WHITE) else mask.setPixel(x, y, Color.BLACK)
            }
        }

        // 7) Mask'i orijinal bitmap boyutuna ölçekle (orijinal bitmap parametresi)
        val scaledMask = Bitmap.createScaledBitmap(mask, bitmap.width, bitmap.height, true)

        // 8) İsteğe bağlı post-processing: burada basitçe döndürüyoruz. Daha ileri temizlik (morphology, blur) eklene bilir.

        val accepted = meanConfidence >= acceptThreshold

        return@withContext SegmentationResult(scaledMask, meanConfidence, accepted)
    }

    // Yardımcı: assets içindeki tflite dosyasını MappedByteBuffer olarak yükler
    @Throws(IOException::class)
    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(modelPath)
        FileInputStream(assetFileDescriptor.fileDescriptor).use { inputStream ->
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        }
    }
}
