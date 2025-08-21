package com.rootcrack.aigarage.segmentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

private const val TAG = "DeepLabV3Seg"

val CITYSCAPES_LABELS_IN_SEGMENTER = arrayOf(
    "road", "sidewalk", "building", "wall", "fence", "pole", "traffic light", "traffic sign",
    "vegetation", "terrain", "sky", "person", "rider", "car", "truck", "bus", "train",
    "motorcycle", "bicycle"
)

data class SegmentationResult(
    val maskBitmap: Bitmap?,
    val maskedPixelRatio: Float,
    val acceptedAutomatically: Boolean,
    val processingTimeMs: Long = 0L
)

// Açıklama: Çoklu nesne segmentasyonu için yeni data class
data class MultiObjectSegmentationResult(
    val individualResults: Map<String, SegmentationResult>,
    val combinedMaskBitmap: Bitmap?,
    val totalProcessingTimeMs: Long = 0L
)

class DeepLabV3XceptionSegmenter(
    private val context: Context,
    private val modelAssetPath: String // Model yolu artık dışarıdan alınıyor
) {

    private var interpreter: Interpreter? = null

    // Bu değerlerin her iki model için de aynı olduğunu varsayıyoruz.
    private val INPUT_HEIGHT = 1025
    private val INPUT_WIDTH = 2049
    private val INPUT_CHANNELS = 3
    private val OUT_HEIGHT = 257
    private val OUT_WIDTH = 513
    private val OUT_CHANNELS = 19

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (interpreter != null) return@withContext true
            val mappedByteBuffer = loadModelFile(context, modelAssetPath) // Parametreyi kullan
            val options = Interpreter.Options()
            interpreter = Interpreter(mappedByteBuffer, options)
            Log.i(TAG, "Interpreter yüklendi: $modelAssetPath")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Interpreter başlatılırken hata: ${e.message}", e)
            return@withContext false
        }
    }

    fun close() {
        try {
            interpreter?.close()
            interpreter = null
            Log.i(TAG, "Interpreter kapatıldı.")
        } catch (e: Exception) {
            Log.w(TAG, "Interpreter kapatılırken hata: ${e.message}")
        }
    }

    suspend fun segment(bitmap: Bitmap, targetClassIndex: Int): SegmentationResult = withContext(Dispatchers.Default) {
        val interp = interpreter
        if (interp == null) {
            return@withContext SegmentationResult(null, 0f, false)
        }

        val startTime = System.currentTimeMillis()

        val inputBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_WIDTH, INPUT_HEIGHT, true)
        val inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_HEIGHT * INPUT_WIDTH * INPUT_CHANNELS * 4).apply {
            order(ByteOrder.nativeOrder())
            val intValues = IntArray(INPUT_WIDTH * INPUT_HEIGHT)
            inputBitmap.getPixels(intValues, 0, INPUT_WIDTH, 0, 0, INPUT_WIDTH, INPUT_HEIGHT)
            var pixelIdx = 0
            for (y in 0 until INPUT_HEIGHT) {
                for (x in 0 until INPUT_WIDTH) {
                    val pixel = intValues[pixelIdx++]
                    putFloat(((pixel shr 16 and 0xFF) - 127.5f) / 127.5f) // R
                    putFloat(((pixel shr 8 and 0xFF) - 127.5f) / 127.5f)  // G
                    putFloat(((pixel and 0xFF) - 127.5f) / 127.5f)        // B
                }
            }
            rewind()
        }

        val outputByteBuffer = ByteBuffer.allocateDirect(1 * OUT_HEIGHT * OUT_WIDTH * OUT_CHANNELS * 4)
        outputByteBuffer.order(ByteOrder.nativeOrder())

        try {
            interp.run(inputBuffer, outputByteBuffer)
        } catch (e: Exception) {
            return@withContext SegmentationResult(null, 0f, false)
        }
        outputByteBuffer.rewind()
        val outputFloatBuffer = outputByteBuffer.asFloatBuffer()

        val maskBitmap = Bitmap.createBitmap(OUT_WIDTH, OUT_HEIGHT, Bitmap.Config.ARGB_8888)
        var positivePixelCount = 0
        var bufferPosition = 0

        for (y in 0 until OUT_HEIGHT) {
            for (x in 0 until OUT_WIDTH) {
                var maxScore = -Float.MAX_VALUE
                var predictedClassIndex = -1
                for (c in 0 until OUT_CHANNELS) {
                    val score = outputFloatBuffer.get(bufferPosition + c)
                    if (score > maxScore) {
                        maxScore = score
                        predictedClassIndex = c
                    }
                }
                bufferPosition += OUT_CHANNELS
                if (predictedClassIndex == targetClassIndex) {
                    maskBitmap.setPixel(x, y, Color.WHITE)
                    positivePixelCount++
                } else {
                    maskBitmap.setPixel(x, y, Color.BLACK)
                }
            }
        }

        if (positivePixelCount == 0) {
            maskBitmap.recycle()
            return@withContext SegmentationResult(null, 0f, false)
        }

        val scaledMask = Bitmap.createScaledBitmap(maskBitmap, bitmap.width, bitmap.height, true)
        maskBitmap.recycle()

        val maskedPixelRatio = positivePixelCount.toFloat() / (OUT_WIDTH * OUT_HEIGHT)
        val accepted = maskedPixelRatio >= 0.01
        val processingTime = System.currentTimeMillis() - startTime

        return@withContext SegmentationResult(scaledMask, maskedPixelRatio, accepted, processingTime)
    }

    // Açıklama: Çoklu nesne segmentasyonu için yeni method
    suspend fun segmentMultipleObjects(bitmap: Bitmap, targetClassIndices: List<Int>): MultiObjectSegmentationResult = withContext(Dispatchers.Default) {
        val interp = interpreter
        if (interp == null) {
            Log.w(TAG, "Interpreter null, çoklu segmentasyon yapılamıyor")
            return@withContext MultiObjectSegmentationResult(emptyMap(), null)
        }

        val startTime = System.currentTimeMillis()
        val individualResults = mutableMapOf<String, SegmentationResult>()
        val combinedMask = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)

        Log.d(TAG, "Çoklu segmentasyon başlatılıyor: ${targetClassIndices.size} nesne")
        
        // Açıklama: Her nesne için ayrı segmentasyon yap
        targetClassIndices.forEach { classIndex ->
            val className = CITYSCAPES_LABELS_IN_SEGMENTER.getOrNull(classIndex) ?: "unknown_$classIndex"
            Log.d(TAG, "Segmentasyon yapılıyor: $className (index: $classIndex)")
            
            val result = segment(bitmap, classIndex)
            individualResults[className] = result
            
            // Açıklama: Başarılı segmentasyonları birleştir
            result.maskBitmap?.let { mask ->
                Log.d(TAG, "Başarılı segmentasyon: $className (pixel ratio: ${result.maskedPixelRatio})")
                
                // Açıklama: Mask'i orijinal bitmap boyutuna ölçekle
                val scaledMask = if (mask.width != bitmap.width || mask.height != bitmap.height) {
                    Bitmap.createScaledBitmap(mask, bitmap.width, bitmap.height, true)
                } else {
                    mask
                }
                
                val canvas = android.graphics.Canvas(combinedMask)
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    alpha = 255
                }
                canvas.drawBitmap(scaledMask, 0f, 0f, paint)
                
                // Açıklama: Eğer mask'i ölçeklediysek, geçici bitmap'i temizle
                if (scaledMask != mask) {
                    scaledMask.recycle()
                }
            } ?: run {
                Log.w(TAG, "Segmentasyon başarısız: $className")
            }
        }

        val totalProcessingTime = System.currentTimeMillis() - startTime
        
        // Açıklama: En az bir başarılı segmentasyon varsa combined mask'i döndür
        val hasAnySuccessfulSegmentation = individualResults.values.any { it.maskBitmap != null }
        val finalCombinedMask = if (hasAnySuccessfulSegmentation) combinedMask else null

        Log.d(TAG, "Çoklu segmentasyon tamamlandı: ${individualResults.count { it.value.maskBitmap != null }}/${targetClassIndices.size} başarılı, süre: ${totalProcessingTime}ms")

        return@withContext MultiObjectSegmentationResult(
            individualResults = individualResults,
            combinedMaskBitmap = finalCombinedMask,
            totalProcessingTimeMs = totalProcessingTime
        )
    }

    @Throws(IOException::class)
    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        context.assets.openFd(modelPath).use { assetFileDescriptor ->
            FileInputStream(assetFileDescriptor.fileDescriptor).use { inputStream ->
                val fileChannel = inputStream.channel
                val startOffset = assetFileDescriptor.startOffset
                val declaredLength = assetFileDescriptor.declaredLength
                return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            }
        }
    }
}
