package com.rootcrack.aigarage.segmentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType // Bu satırın olduğundan emin olun
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

private const val TAG = "DeepLabV3Seg"

// CITYSCAPES_LABELS_IN_SEGMENTER array'i AutoMaskPreviewScreen ile tutarlılık için eklendi.
val CITYSCAPES_LABELS_IN_SEGMENTER = arrayOf(
    "road", "sidewalk", "building", "wall", "fence", "pole", "traffic light", "traffic sign",
    "vegetation", "terrain", "sky", "person", "rider", "car", "truck", "bus", "train",
    "motorcycle", "bicycle"
)

data class SegmentationResult(
    val maskBitmap: Bitmap?,
    val maskedPixelRatio: Float, // Maskelenen piksellerin toplam piksellere oranı (% olarak değil, 0.0-1.0 aralığında)
    val acceptedAutomatically: Boolean,
    val processingTimeMs: Long = 0L // Varsayılan değer eklenebilir
)


class DeepLabV3XceptionSegmenter(
    private val context: Context,
     private val modelAssetPath: String = "deeplabv3-xception65.tflite" // Ağır olan maskeleme
    // private val modelAssetPath: String = "mask.tflite" // Hafif olan maskeleme
) {

    private var interpreter: Interpreter? = null

    // It's important to verify these values according to your model.
    private val INPUT_HEIGHT = 1025
    private val INPUT_WIDTH = 2049
    private val INPUT_CHANNELS = 3

    private val OUT_HEIGHT = 257
    private val OUT_WIDTH = 513
    private val OUT_CHANNELS = 19

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (interpreter != null) return@withContext true
            val mappedByteBuffer = loadModelFile(context, modelAssetPath)
            val options = Interpreter.Options()
            // Optional: XNNPack (CPU acceleration) is enabled by default.
            // For GPU delegate: options.addDelegate(GpuDelegate())
            interpreter = Interpreter(mappedByteBuffer, options)

            // Verify model input and output (optional but helpful)
            val outputTensor = interpreter!!.getOutputTensor(0) // Assuming single output tensor
            Log.i(TAG, "Interpreter loaded: $modelAssetPath")
            Log.i(TAG, "Model Output Shape: ${outputTensor.shape().joinToString(", ")}. Data Type: ${outputTensor.dataType()}")

            // Ensure the OUT_CHANNELS matches the model's actual output
            if (outputTensor.shape().last() != OUT_CHANNELS) {
                Log.e(TAG, "FATAL: Model output channels (${outputTensor.shape().last()}) " +
                        "does not match configured OUT_CHANNELS ($OUT_CHANNELS). Update the code.")
                // Potentially close interpreter and return false or throw an error
                // For now, we'll log and let it proceed, but this is a critical mismatch.
            }
            if (outputTensor.dataType() != DataType.FLOAT32) {
                Log.e(TAG, "FATAL: Model output DataType (${outputTensor.dataType()}) " +
                        "is not FLOAT32 as expected for this version of the segmenter. Update the code.")
            }

            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing interpreter: ${e.message}", e)
            return@withContext false
        }
    }

    fun close() {
        try {
            interpreter?.close()
            interpreter = null
            Log.i(TAG, "Interpreter closed.")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing interpreter: ${e.message}")
        }
    }

    suspend fun segment(bitmap: Bitmap, targetClassIndex: Int): SegmentationResult = withContext(Dispatchers.Default) {
        val interp = interpreter
        if (interp == null) {
            Log.e(TAG, "Interpreter not initialized. Call initialize().")
            return@withContext SegmentationResult(null, 0f, false)
        }

        // 1) Prepare input bitmap (resize and normalize)
        val inputBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_WIDTH, INPUT_HEIGHT, true)
        val inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_HEIGHT * INPUT_WIDTH * INPUT_CHANNELS * 4).apply { // 4 bytes for float
            order(ByteOrder.nativeOrder())
            val intValues = IntArray(INPUT_WIDTH * INPUT_HEIGHT)
            inputBitmap.getPixels(intValues, 0, INPUT_WIDTH, 0, 0, INPUT_WIDTH, INPUT_HEIGHT)
            var pixelIdx = 0
            for (y in 0 until INPUT_HEIGHT) {
                for (x in 0 until INPUT_WIDTH) {
                    val pixel = intValues[pixelIdx++] // ARGB
                    putFloat(((pixel shr 16 and 0xFF) - 127.5f) / 127.5f) // R
                    putFloat(((pixel shr 8 and 0xFF) - 127.5f) / 127.5f)  // G
                    putFloat(((pixel and 0xFF) - 127.5f) / 127.5f)        // B
                }
            }
            rewind()
        }

        // 2) Prepare output buffer.
        // Size = 1 * Height * Width * Number of Channels * 4 (bytes per float)
        val outputByteBuffer = ByteBuffer.allocateDirect(1 * OUT_HEIGHT * OUT_WIDTH * OUT_CHANNELS * 4)
        outputByteBuffer.order(ByteOrder.nativeOrder())

        // 3) Run the model
        try {
            interp.run(inputBuffer, outputByteBuffer)
        } catch (e: Exception) {
            Log.e(TAG, "Error running model: ${e.message}", e)
            return@withContext SegmentationResult(null, 0f, false)
        }
        outputByteBuffer.rewind()
        val outputFloatBuffer = outputByteBuffer.asFloatBuffer() // Read as FloatBuffer

        // 4) Process output to create mask (ArgMax logic)
        val maskBitmap = Bitmap.createBitmap(OUT_WIDTH, OUT_HEIGHT, Bitmap.Config.ARGB_8888)
        var positivePixelCount = 0
        var bufferPosition = 0 // Tracks the current position in the 1D float buffer

        for (y in 0 until OUT_HEIGHT) {
            for (x in 0 until OUT_WIDTH) {
                var maxScore = -Float.MAX_VALUE
                var predictedClassIndex = -1

                // Iterate through scores for all classes for the current pixel
                for (c in 0 until OUT_CHANNELS) {
                    val score = outputFloatBuffer.get(bufferPosition + c)
                    if (score > maxScore) {
                        maxScore = score
                        predictedClassIndex = c
                    }
                }
                bufferPosition += OUT_CHANNELS // Move to the next pixel's scores

                // Check if the class with the highest score is our target class
                if (predictedClassIndex == targetClassIndex) {
                    maskBitmap.setPixel(x, y, Color.WHITE)
                    positivePixelCount++
                } else {
                    maskBitmap.setPixel(x, y, Color.BLACK)
                }
            }
        }

        val totalPixels = OUT_WIDTH * OUT_HEIGHT
        Log.d(TAG, "Mask created (target index: $targetClassIndex). Masked pixel count: $positivePixelCount / $totalPixels")

        if (positivePixelCount == 0) {
            Log.w(TAG, "No pixels found for the target class.")
            maskBitmap.recycle()
            return@withContext SegmentationResult(null, 0f, false)
        }

        // 5) Scale mask to original bitmap size
        val scaledMask = Bitmap.createScaledBitmap(maskBitmap, bitmap.width, bitmap.height, true)
        maskBitmap.recycle() // Release the smaller mask bitmap

        // 6) Determine acceptance status
        val maskedPixelRatio = positivePixelCount.toFloat() / totalPixels
        // Consider "acceptable" if at least 1% of the image is masked.
        val accepted = maskedPixelRatio >= 0.01

        Log.d(TAG, "Segmentation complete. Masked ratio: $maskedPixelRatio, Auto-accepted: $accepted")

        return@withContext SegmentationResult(scaledMask, maskedPixelRatio, accepted)
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
