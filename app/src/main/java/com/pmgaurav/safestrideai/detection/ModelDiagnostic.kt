package com.pmgaurav.safestrideai.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ModelDiagnostic(private val context: Context) {

    companion object {
        private const val TAG = "🔬 ModelDiagnostic"
        private const val MODEL_FILE = "pedestrian_vehicle_detect.tflite"
    }

    fun run() {
        Log.d(TAG, "═══════════════════════════════════════")
        Log.d(TAG, "  TFLite MODEL DIAGNOSTIC REPORT")
        Log.d(TAG, "═══════════════════════════════════════")

        val options = Interpreter.Options().apply { numThreads = 1 }

        val interpreter = try {
            val buf = FileUtil.loadMappedFile(context, MODEL_FILE)
            Interpreter(buf, options)
        } catch (e: Exception) {
            Log.e(TAG, "❌ CANNOT LOAD MODEL: ${e.message}")
            Log.e(TAG, "   Check: assets/$MODEL_FILE exists?")
            return
        }

        Log.d(TAG, "✅ Model loaded successfully")
        Log.d(TAG, "")


        Log.d(TAG, "INPUT TENSORS (${interpreter.inputTensorCount}):")
        for (i in 0 until interpreter.inputTensorCount) {
            val t = interpreter.getInputTensor(i)
            Log.d(TAG, "  [$i] name=${t.name()}")
            Log.d(TAG, "      shape=${t.shape().toList()}")
            Log.d(TAG, "      dtype=${t.dataType()}")
            Log.d(TAG, "      quant=${t.quantizationParams()}")


            when (t.dataType().toString()) {
                "UINT8" ->
                    Log.d(TAG, "      ⚠️  UINT8 model → use raw 0-255 pixel values, NO float division")
                "FLOAT32" ->
                    Log.d(TAG, "      ✅ FLOAT32 model → normalize pixels to [-1,1] or [0,1]")
            }
        }

        Log.d(TAG, "")
        Log.d(TAG, "OUTPUT TENSORS (${interpreter.outputTensorCount}):")


        for (i in 0 until interpreter.outputTensorCount) {
            val t = interpreter.getOutputTensor(i)
            Log.d(TAG, "  [$i] name=${t.name()}")
            Log.d(TAG, "      shape=${t.shape().toList()}")
            Log.d(TAG, "      dtype=${t.dataType()}")


            val shape = t.shape().toList()
            val guess = when {
                shape.size == 3 && shape[2] == 4 -> "📦 BOUNDING BOXES [batch, num_det, 4]"
                shape.size == 2 && shape[1] > 10  -> "🏷️  CLASS INDICES  [batch, num_det]"
                shape.size == 2 && (shape[1] in 2..999) -> "📊 SCORES         [batch, num_det]"
                shape.size == 1 -> "🔢 COUNT[1]"
                shape.size == 2 && shape[1] > 1000 -> "🏷️  CLASS SCORES   [batch, num_classes] — single-shot?"
                else -> "❓ UNKNOWN"
            }
            Log.d(TAG, " GUESS: $guess")
        }

        Log.d(TAG, "")

        Log.d(TAG, "RUNNING TEST INFERENCE WITH SYNTHETIC IMAGE...")
        runTestInference(interpreter)

        interpreter.close()
        Log.d(TAG, "═══════════════════════════════════════")
    }

    private fun runTestInference(interpreter: Interpreter) {
        val testBitmap = createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(testBitmap)
        canvas.drawColor(Color.rgb(100, 100, 100))

        val inputTensor = interpreter.getInputTensor(0)
        val inputShape  = inputTensor.shape()
        val h = inputShape[1]
        val w = inputShape[2]
        val isUint8 = inputTensor.dataType().toString() == "UINT8"

        val inputBuffer: ByteBuffer = if (isUint8) {
            ByteBuffer.allocateDirect(1 * h * w * 3).apply { order(ByteOrder.nativeOrder()) }
        } else {
            ByteBuffer.allocateDirect(1 * h * w * 3 * 4).apply { order(ByteOrder.nativeOrder()) }
        }

        val scaledBitmap = testBitmap.scale(w, h, true)
        val pixels = IntArray(w * h)
        scaledBitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8)  and 0xFF
            val b = pixel           and 0xFF

            if (isUint8) {
                inputBuffer.put(r.toByte())
                inputBuffer.put(g.toByte())
                inputBuffer.put(b.toByte())
            } else {
                inputBuffer.putFloat((r - 127.5f) / 127.5f)
                inputBuffer.putFloat((g - 127.5f) / 127.5f)
                inputBuffer.putFloat((b - 127.5f) / 127.5f)
            }
        }

        val outputCount = interpreter.outputTensorCount
        val outputs = HashMap<Int, Any>()

        for (i in 0 until outputCount) {
            val shape = interpreter.getOutputTensor(i).shape()
            val dtype = interpreter.getOutputTensor(i).dataType().toString()
            val buf: Any = when (dtype) {
                "UINT8" -> Array<Any>(shape[0]) {
                    if (shape.size == 3) Array(shape[1]) { ByteArray(shape[2]) }
                    else ByteArray(shape[1])
                }
                else -> when (shape.size) {
                    1 -> FloatArray(shape[0])
                    2 -> Array(shape[0]) { FloatArray(shape[1]) }
                    3 -> Array(shape[0]) { Array(shape[1]) { FloatArray(shape[2]) } }
                    else -> FloatArray(1)
                }
            }
            outputs[i] = buf
        }

        try {
            interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)
            Log.d(TAG, "✅ Test inference completed without crash!")

            for (i in 0 until outputCount) {
                val out = outputs[i]
                Log.d(TAG, "Output[$i] sample values:")
                when (out) {
                    is FloatArray -> Log.d(TAG, "  ${out.take(5).toList()}")
                    is Array<*> -> {
                        when (val first = out.firstOrNull()) {
                            is FloatArray -> Log.d(TAG, "  ${first.take(5).toList()}")
                            is Array<*> -> {
                                val second = first.firstOrNull()
                                if (second is FloatArray) Log.d(TAG, "  ${second.take(5).toList()}")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Test inference CRASHED: ${e.message}")
            Log.e(TAG, " This means input buffer format is wrong!")
        }

        testBitmap.recycle()
        scaledBitmap.recycle()
    }
}

