package com.pmgaurav.safestrideai.camera

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.pmgaurav.safestrideai.detection.DetectionPipeline
import com.pmgaurav.safestrideai.detection.ImageProcessor
import kotlinx.coroutines.*
import javax.inject.Inject

private const val TAG = "FrameAnalyzer"

class FrameAnalyzer @Inject constructor(
    private val pipeline: DetectionPipeline,
    private val imageProcessor: ImageProcessor,
) : ImageAnalysis.Analyzer {

    private val scope = CoroutineScope(
        Dispatchers.Default + SupervisorJob() +
        CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "Coroutine error: ${e.message}", e)
        },
    )

    private var totalFrames = 0
    private var processedFrames = 0

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(image: ImageProxy) {
        totalFrames++

        if (pipeline.isProcessing()) {
            image.close()
            return
        }

        if (totalFrames % 60 == 0) {
            Log.d(TAG, "BRIDGE: Camera active. Total=$totalFrames, Processed=$processedFrames")
        }

        val bitmap: Bitmap? = try {
            imageProcessor.toBitmap(image)
        } catch (e: Exception) {
            Log.e(TAG, "BRIDGE: YUV conversion fail: ${e.message}")
            null
        } finally {
            image.close()   
        }

        if (bitmap == null) {
            return
        }

        processedFrames++
        Log.e("DEBUG_ANALYZER", "Sending frame $totalFrames to pipeline (Total Processed: $processedFrames)")
        scope.launch {
            try {
                pipeline.processFrame(bitmap)
            } catch (e: Exception) {
                Log.e(TAG, "BRIDGE: Pipeline crash: ${e.message}")
            } finally {
                bitmap.recycle()
            }
        }
    }

    fun shutdown() {
        scope.cancel()
    }
}

