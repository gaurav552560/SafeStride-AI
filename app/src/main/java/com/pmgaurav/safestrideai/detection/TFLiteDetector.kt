package com.pmgaurav.safestrideai.detection

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.pmgaurav.safestrideai.utils.AppConstants
import com.pmgaurav.safestrideai.utils.ImageUtils
import com.pmgaurav.safestrideai.optimization.EdgeComputingOptimizer
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TFLiteDetector"

@Singleton
class TFLiteDetector @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val optimizer: EdgeComputingOptimizer
) {
    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()

    @Volatile private var ready = false

    private var inputSize   = AppConstants.MODEL_INPUT_WIDTH
    private var maxDetections = AppConstants.MAX_DETECTIONS
    private var isQuantized = true
    private var isNightMode = false

    private var outputBoxes: Array<Array<FloatArray>> = emptyArray()
    private var outputClasses: Array<FloatArray> = emptyArray()
    private var outputScores: Array<FloatArray> = emptyArray()
    private var outputCount: FloatArray = FloatArray(1)

    companion object {
        private const val MODEL_FILE = AppConstants.MODEL_FILE
        private const val LABELS_FILE = "labelmap.txt"
    }

    fun initialize(): Boolean {
        Log.d(TAG, "initialize() called. ready=$ready")
        if (ready) return true

        return try {
            Log.d(TAG, "Attempting to load labels from $LABELS_FILE...")
            val rawLabels = context.assets.open(LABELS_FILE)
                .bufferedReader()
                .readLines()
                .map { it.trim().replace("\r", "") }
                .filter { it.isNotEmpty() }


            labels = rawLabels

            Log.d(TAG, "Labels loaded: ${labels.size}. First 5: ${labels.take(5)}")

            Log.d(TAG, "Loading model file $MODEL_FILE...")
            val modelBuf = loadModelFile()
            Log.d(TAG, "Model buffer: ${modelBuf.capacity()} bytes")

            val options = Interpreter.Options().apply {
                setNumThreads(4)
                useXNNPACK = true
                
                val delegates = optimizer.createOptimalDelegate()
                if (delegates.isNotEmpty()) {
                    delegates.forEach { addDelegate(it) }
                    Log.d(TAG, "Using hardware acceleration delegates: ${delegates.size}")
                } else {
                    Log.d(TAG, "Using CPU with 4 threads (GPU/NNAPI disabled or unavailable)")
                }
            }

            Log.d(TAG, "Creating TFLite Interpreter...")
            interpreter = Interpreter(modelBuf, options)

            val inShape = interpreter!!.getInputTensor(0).shape()
            Log.d(TAG, "Input tensor shape: ${inShape.toList()}")
            inputSize = inShape.getOrElse(1) { 300 }

            val outShape0 = interpreter!!.getOutputTensor(0).shape()
            Log.d(TAG, "Output[0] shape (boxes): ${outShape0.toList()}")
            maxDetections = outShape0.getOrElse(1) { 10 }

            isQuantized = interpreter!!.getInputTensor(0).dataType() ==
                    org.tensorflow.lite.DataType.UINT8

            Log.d(TAG, "Quantized: $isQuantized, inputSize: $inputSize, maxDetections: $maxDetections")

            outputBoxes   = Array(1) { Array(maxDetections) { FloatArray(4) } }
            outputClasses = Array(1) { FloatArray(maxDetections) }
            outputScores  = Array(1) { FloatArray(maxDetections) }
            outputCount   = FloatArray(1)

            Log.d(TAG, "Starting warm-up inference...")
            val dummyInput = if (isQuantized) {
                ByteBuffer.allocateDirect(inputSize * inputSize * 3).apply { rewind() }
            } else {
                ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4)
                    .order(ByteOrder.nativeOrder()).apply { rewind() }
            }
            runInference(dummyInput)
            Log.d(TAG, "Warm-up inference complete")

            ready = true
            warmUp()
            Log.d(TAG, "✅ TFLiteDetector ready")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Init failed: ${e.message}", e)
            ready = false
            false
        }
    }

    fun isReady() = ready

    private fun warmUp() {
        try {
            val dummyBitmap = androidx.core.graphics.createBitmap(inputSize, inputSize)
            val dummyBuffer = preprocessBitmap(dummyBitmap)
            clearOutputBuffers()
            val outputs = mapOf(0 to outputBoxes, 1 to outputClasses, 2 to outputScores, 3 to outputCount)
            interpreter?.runForMultipleInputsOutputs(arrayOf(dummyBuffer), outputs)
            dummyBitmap.recycle()
            Log.d(TAG, "Warm-up inference complete — detector confirmed functional")
        } catch (e: Exception) {
            Log.w(TAG, "Warm-up failed (non-fatal): ${e.message}")
        }
    }

    fun detect(bitmap: Bitmap): List<RawDetection> {
        val interpreterInstance = interpreter
        if (!ready || (interpreterInstance == null)) {
            Log.w(TAG, "detect() called but not ready")
            return emptyList()
        }

        return try {
            val inputBuffer = preprocessBitmap(bitmap)
            clearOutputBuffers()

            val outputs = mapOf(
                0 to outputBoxes,
                1 to outputClasses,
                2 to outputScores,
                3 to outputCount,
            )
            
            interpreterInstance.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)

            var count = outputCount[0].toInt().coerceIn(0, maxDetections)
            if (count == 0) {

                for (i in 0 until maxDetections) {
                    if (outputScores[0][i] > 0.15f) count = i + 1
                }
            }

            val threshold = getConfidenceThreshold()
            val results = mutableListOf<RawDetection>()

            val imgW = bitmap.width.toFloat()
            val imgH = bitmap.height.toFloat()
            val maxEdge = maxOf(imgW, imgH)
            

            val scaleToModel = inputSize.toFloat() / maxEdge
            val scaledW = imgW * scaleToModel
            val scaledH = imgH * scaleToModel
            

            val offsetX = (inputSize - scaledW) / 2f
            val offsetY = (inputSize - scaledH) / 2f

            for (i in 0 until count) {
                val score = outputScores[0][i]
                if (score < threshold) continue

                val classIdx = outputClasses[0][i].toInt()
                val label = resolveLabel(classIdx)
                val box = outputBoxes[0][i]
                val modelLeft   = box[1] * inputSize
                val modelTop    = box[0] * inputSize
                val modelRight  = box[3] * inputSize
                val modelBottom = box[2] * inputSize
                val scaledLeft   = modelLeft - offsetX
                val scaledTop    = modelTop - offsetY
                val scaledRight  = modelRight - offsetX
                val scaledBottom = modelBottom - offsetY
                val leftPx   = scaledLeft / scaleToModel
                val topPx    = scaledTop / scaleToModel
                val rightPx  = scaledRight / scaleToModel
                val bottomPx = scaledBottom / scaleToModel
                val rectF = android.graphics.RectF(
                    (leftPx / imgW).coerceIn(0f, 1f),
                    (topPx / imgH).coerceIn(0f, 1f),
                    (rightPx / imgW).coerceIn(0f, 1f),
                    (bottomPx / imgH).coerceIn(0f, 1f)
                )

                if (rectF.width() <= 0f || rectF.height() <= 0f) continue

                results.add(RawDetection(
                    classIndex  = classIdx,
                    className   = label,
                    confidence  = score,
                    boundingBox = rectF
                ))
            }
            
            if (results.isNotEmpty()) {
                Log.d(TAG, "SUCCESS: Found ${results.size} objects")
            }
            results
        } catch (e: Exception) {
            Log.e(TAG, "Inference error: ${e.message}")
            emptyList()
        }
    }
    private fun resolveLabel(classIdx: Int): String {
        val adjustedIdx = classIdx + 1
        
        val coco91Labels = mapOf(
            1 to "person", 2 to "bicycle", 3 to "car", 4 to "motorcycle", 5 to "airplane",
            6 to "bus", 7 to "train", 8 to "truck", 9 to "boat", 10 to "traffic light",
            11 to "fire hydrant", 13 to "stop sign", 14 to "parking meter", 17 to "cat",
            18 to "dog", 19 to "horse", 21 to "cow", 22 to "elephant", 91 to "auto_rickshaw",
        )

        return labels.getOrElse(adjustedIdx) { 
            coco91Labels[adjustedIdx] ?: "unknown"
        }
    }

    private fun getConfidenceThreshold(): Float {
        return AppConstants.CONFIDENCE_THRESHOLD
    }

    private fun preprocessBitmap(bitmap: Bitmap): ByteBuffer {
        Log.e("DEBUG_DETECT", "preprocessBitmap called (FitAndPad)")

        val targetSize = inputSize
        val scaled = ImageUtils.fitAndPad(bitmap, targetSize)
        updateNightMode(scaled)

        val finalBitmap = if (isNightMode) preprocessBitmapWithNightEnhancement(scaled) else scaled
        return if (isQuantized) {
            val buf = ByteBuffer.allocateDirect(inputSize * inputSize * 3)
            buf.order(ByteOrder.nativeOrder())
            buf.rewind()
            val pixels = IntArray(inputSize * inputSize)
            finalBitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
            for (px in pixels) {
                buf.put(((px shr 16) and 0xFF).toByte())  
                buf.put(((px shr  8) and 0xFF).toByte())  
                buf.put(( px and 0xFF).toByte())
            }
            buf.rewind()

            if (finalBitmap != bitmap) finalBitmap.recycle()
            if (scaled != bitmap && scaled != finalBitmap) scaled.recycle()
            
            buf
        } else {
            val buf = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4)
            buf.order(ByteOrder.nativeOrder())
            buf.rewind()
            val pixels = IntArray(inputSize * inputSize)
            finalBitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
            for (px in pixels) {
                buf.putFloat((((px shr 16) and 0xFF) - 127.5f) / 127.5f)
                buf.putFloat((((px shr  8) and 0xFF) - 127.5f) / 127.5f)
                buf.putFloat((( px and 0xFF) - 127.5f) / 127.5f)
            }
            buf.rewind()

            if (finalBitmap != bitmap) finalBitmap.recycle()
            if (scaled != bitmap && scaled != finalBitmap) scaled.recycle()

            buf
        }
    }

    private fun preprocessBitmapWithNightEnhancement(bitmap: Bitmap): Bitmap {
        val enhancedPaint = android.graphics.ColorMatrix().apply {
            val contrast = 1.8f
            val brightness = 40f
            set(floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        
        val enhanced = androidx.core.graphics.createBitmap(
            bitmap.width, 
            bitmap.height, 
            bitmap.config ?: Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(enhanced)
        val paint = android.graphics.Paint().apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(enhancedPaint)
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return enhanced
    }
    private fun updateNightMode(bitmap: Bitmap) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        var total = 0.0
        var count = 0
        for (i in pixels.indices step 10) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 255.0
            val g = ((p shr 8)  and 0xFF) / 255.0
            val b = (p           and 0xFF) / 255.0
            total += 0.2126 * r + 0.7152 * g + 0.0722 * b
            count++
        }
        val brightness = total / count.coerceAtLeast(1)
        val wasNight = isNightMode
        isNightMode = brightness < 0.12
        
        if (wasNight != isNightMode) {
            Log.d(TAG, "🌙 Night mode changed: $isNightMode (brightness: ${"%.3f".format(brightness)})")
        }
    }

    private fun runInference(inputBuffer: ByteBuffer) {
        val outputs = mapOf(
            0 to outputBoxes,
            1 to outputClasses,
            2 to outputScores,
            3 to outputCount,
        )
        interpreter?.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)
    }

    private fun clearOutputBuffers() {
        outputBoxes.forEach { arr -> arr.forEach { it.fill(0f) } }
        outputClasses.forEach { it.fill(0f) }
        outputScores.forEach { it.fill(0f) }
        outputCount.fill(0f)
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fd = context.assets.openFd(MODEL_FILE)
        return FileInputStream(fd.fileDescriptor).channel.map(
            FileChannel.MapMode.READ_ONLY,
            fd.startOffset,
            fd.declaredLength
        )
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        ready = false
        Log.d(TAG, "TFLiteDetector closed")
    }
}
