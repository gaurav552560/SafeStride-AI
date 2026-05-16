package com.pmgaurav.safestrideai.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.core.graphics.createBitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder


class LabelMapDiagnostic(private val context: Context) {

    companion object {
        private const val TAG = "🏷️ LabelDiagnostic"
        private const val MODEL_FILE = "pedestrian_vehicle_detect.tflite"
        private const val LABEL_FILE = "labelmap.txt"
    }

    fun run() {
        val rawLabels: List<String> = try {
            FileUtil.loadLabels(context, LABEL_FILE)
        } catch (_: Exception) {
            Log.e(TAG, "FileUtil failed")
            emptyList()
        }

        val manualLabels: List<String> = try {
            context.assets.open(LABEL_FILE)
                .bufferedReader()
                .readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        } catch (_: Exception) {
            Log.e(TAG, "Manual read failed")
            emptyList()
        }

        Log.d(TAG, "")
        Log.d(TAG, "FileUtil.loadLabels() → ${rawLabels.size} labels:")
        rawLabels.forEachIndexed { i, l -> Log.d(TAG, "  FileUtil[$i] = '$l'") }

        Log.d(TAG, "")
        Log.d(TAG, "Raw file read → ${manualLabels.size} lines:")
        manualLabels.forEachIndexed { i, l -> Log.d(TAG, "  Raw[$i] = '$l'") }

        Log.d(TAG, "")
        Log.d(TAG, "Loading model: $MODEL_FILE")

        val interpreter = try {
            val buf = FileUtil.loadMappedFile(context, MODEL_FILE)
            Interpreter(buf, Interpreter.Options().apply { numThreads = 1 })
        } catch (_: Exception) {
            Log.e(TAG, "Cannot load model")
            return
        }

        Log.d(TAG, "Output tensors: ${interpreter.outputTensorCount}")
        for (i in 0 until interpreter.outputTensorCount) {
            val t = interpreter.getOutputTensor(i)
            Log.d(TAG, "  Output[$i]: name='${t.name()}' shape=${t.shape().toList()}")
        }

        Log.d(TAG, "")
        Log.d(TAG, "Running test inference with colored bitmaps...")
        Log.d(TAG, "NOTE: These are synthetic — real indices come from camera")

        runTestInference(interpreter, rawLabels, manualLabels)

        interpreter.close()
    }

    private fun runTestInference(
        interpreter:  Interpreter,
        rawLabels:    List<String>,
        manualLabels: List<String>,
    ) {
        val inputTensor = interpreter.getInputTensor(0)
        val shape       = inputTensor.shape()
        val h           = shape[1]; val w = shape[2]
        val isUint8     = inputTensor.dataType().toString() == "UINT8"
        val bpp         = if (isUint8) 1 else 4

        val inputBuf = ByteBuffer
            .allocateDirect(1 * h * w * 3 * bpp)
            .apply { order(ByteOrder.nativeOrder()) }

        val maxDet  = try { interpreter.getOutputTensor(0).shape()[1] } catch (_: Exception) { 25 }
        val outBoxes   = Array(1) { Array(maxDet) { FloatArray(4) } }
        val outClasses = Array(1) { FloatArray(maxDet) }
        val outScores  = Array(1) { FloatArray(maxDet) }
        val outCount   = FloatArray(1)
        val outMap     = mapOf(0 to outBoxes, 1 to outClasses, 2 to outScores, 3 to outCount)

        val bmp = createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(100, 100, 100))
        }
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        inputBuf.rewind()
        for (px in pixels) {
            val r = (px shr 16) and 0xFF
            val g = (px shr 8)  and 0xFF
            val b =  px         and 0xFF
            if (isUint8) {
                inputBuf.put(r.toByte()); inputBuf.put(g.toByte()); inputBuf.put(b.toByte())
            } else {
                inputBuf.putFloat(r / 127.5f - 1f)
                inputBuf.putFloat(g / 127.5f - 1f)
                inputBuf.putFloat(b / 127.5f - 1f)
            }
        }

        try {
            interpreter.runForMultipleInputsOutputs(arrayOf(inputBuf), outMap)
            Log.d(TAG, "Test inference OK. outputCount=${outCount[0]}")

            Log.d(TAG, "Top raw outputs (no threshold):")
            (0 until minOf(5, maxDet)).forEach { i ->
                val rawIdx    = outClasses[0][i].toInt()
                val score     = outScores[0][i]
                val labelRaw  = rawLabels.getOrNull(rawIdx) ?: "OUT_OF_RANGE"
                val labelMan  = manualLabels.getOrNull(rawIdx) ?: "OUT_OF_RANGE"
                val labelOff1 = rawLabels.getOrNull(rawIdx + 1) ?: "OUT_OF_RANGE"
                Log.d(TAG, "  slot[$i]: classIdx=$rawIdx score=${"%.4f".format(score)}")
                Log.d(TAG, "  FileUtil[idx]  = '$labelRaw'")
                Log.d(TAG, "  Manual[idx]    = '$labelMan'")
                Log.d(TAG, "  FileUtil[idx+1]= '$labelOff1'")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Test inference failed: ${e.message}")
        }

        bmp.recycle()
    }
}

