package com.pmgaurav.safestrideai.detection

import android.graphics.Bitmap
import android.util.Log
import com.pmgaurav.safestrideai.alert.AlertManager
import com.pmgaurav.safestrideai.optimization.EdgeComputingOptimizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DetectionPipeline"

data class PipelineStats(
    val totalFrames: Int = 0,
    val droppedFrames: Int = 0,
    val suppressedFrames: Int = 0,
    val processingTimeMs: Long = 0,
    val fps: Float = 0f,
)

@Singleton
class DetectionPipeline @Inject constructor(
    private val tfLiteDetector: TFLiteDetector,
    private val opticalFlowTracker: OpticalFlowTracker,
    private val falseFilter: FalseDetectionFilter,
    private val sceneValidator: SceneValidator,
    private val labelValidator: LabelValidator,
    private val alertManager: AlertManager,
    optimizer: EdgeComputingOptimizer,
) {
    data class PipelineResults(
        val trackedObjects: List<TrackedObject>,
        val frameWidth: Int,
        val frameHeight: Int
    )

    private val _trackedObjects = MutableStateFlow<PipelineResults>(PipelineResults(emptyList(), 480, 640))
    val trackedObjects: StateFlow<PipelineResults> = _trackedObjects

    private val _pipelineStatsFlow = MutableStateFlow(PipelineStats())
    val pipelineStatsFlow: StateFlow<PipelineStats> = _pipelineStatsFlow

    private var isProcessing = false
    private var totalFrames = 0
    private var droppedFrames = 0
    private var suppressedFrames = 0
    private var processingTimeMs = 0L

    private var lastFpsTime = System.currentTimeMillis()
    private var framesSinceLastFps = 0
    private var currentFps = 0f
    
    private var targetFps = 30
    private var lastProcessedTime = 0L

    init {
        optimizer.monitorThermalState { newTarget ->
            targetFps = newTarget
            Log.i(TAG, "🌡️ Thermal Status Change: Throttling to $targetFps FPS")
        }
    }

    @Suppress("Unused")
    fun getStats() = PipelineStats(
        totalFrames = totalFrames,
        droppedFrames = droppedFrames,
        suppressedFrames = suppressedFrames,
        processingTimeMs = processingTimeMs,
        fps = currentFps
    )

    fun isProcessing() = isProcessing

    fun processFrame(bitmap: Bitmap): List<TrackedObject> {
        Log.e("DEBUG_PIPELINE", "processFrame called - START")
        totalFrames++
        Log.e(TAG, "BRIDGE: Received frame #$totalFrames")


        val nowMs = System.currentTimeMillis()
        val frameIntervalMs = 1000L / targetFps
        if (nowMs - lastProcessedTime < frameIntervalMs) {
            droppedFrames++
            return emptyList()
        }
        lastProcessedTime = nowMs

        if (!tfLiteDetector.isReady()) {
            Log.e(TAG, "BRIDGE: Detector NOT READY")
            suppressedFrames++
            return emptyList()
        }


        val sceneResult = sceneValidator.validate(bitmap)
        if (!sceneResult.isValid) {
            Log.w(TAG, "Scene invalid: ${sceneResult.reason}")
            return emptyList()
        }

        isProcessing = true
        val startMs = System.currentTimeMillis()

        return try {

            val rawDetections: List<RawDetection> = tfLiteDetector.detect(bitmap)
            Log.d(TAG, "PIPELINE_DEBUG: [1] Raw detections: ${rawDetections.size}")


            val filteredRaw = falseFilter.filter(rawDetections)
            Log.d(TAG, "PIPELINE_DEBUG: [2] After FalseFilter: ${filteredRaw.size}")


            val remappedRaw = filteredRaw.mapNotNull {
                val remapped = labelValidator.remapForRoadContext(it)

                if (remapped != null && labelValidator.isSafetyClass(remapped.className)) remapped else null
            }
            Log.d(TAG, "PIPELINE_DEBUG: [3] After LabelValidation: ${remappedRaw.size}")


            val nmsResults = NmsProcessor.applyNms(remappedRaw)
            Log.d(TAG, "PIPELINE_DEBUG: [4] After NMS: ${nmsResults.size}")
            

            opticalFlowTracker.setAdaptiveQuality(currentFps)
            val tracked = opticalFlowTracker.update(bitmap, nmsResults)
            Log.d(TAG, "PIPELINE_DEBUG: [5] After Tracking: ${tracked.size}")


            alertManager.processTrackedObjects(tracked)

            _trackedObjects.value = PipelineResults(tracked, bitmap.width, bitmap.height)
            
            processingTimeMs = System.currentTimeMillis() - startMs
            
            framesSinceLastFps++
            val now = System.currentTimeMillis()
            if ((now - lastFpsTime) >= 1000L) {
                currentFps = (framesSinceLastFps * 1000f) / (now - lastFpsTime)
                framesSinceLastFps = 0
                lastFpsTime = now
            }


            val stats = PipelineStats(
                totalFrames = totalFrames,
                droppedFrames = droppedFrames,
                suppressedFrames = suppressedFrames,
                processingTimeMs = processingTimeMs,
                fps = currentFps
            )
            _pipelineStatsFlow.value = stats
            Log.e("DEBUG_PIPELINE", "Stats Updated: FPS=$currentFps, Objects=${tracked.size}")

            Log.e(TAG, "BRIDGE: Finished frame #$totalFrames in ${processingTimeMs}ms. Objects=${tracked.size}")
            tracked
        } catch (e: Exception) {
            Log.e(TAG, "BRIDGE: Error processing frame: ${e.message}", e)
            emptyList()
        } finally {
            isProcessing = false
        }
    }

    fun release() {
        tfLiteDetector.close()
        opticalFlowTracker.release()
        sceneValidator.reset()
        _trackedObjects.value = PipelineResults(emptyList(), 480, 640)
    }

    fun reset() {
        isProcessing = false
        totalFrames = 0
        droppedFrames = 0
        suppressedFrames = 0
        currentFps = 0f
        _trackedObjects.value = PipelineResults(emptyList(), 480, 640)
    }
}

