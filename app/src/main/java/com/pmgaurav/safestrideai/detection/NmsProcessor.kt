package com.pmgaurav.safestrideai.detection

import android.graphics.RectF
import android.util.Log
import com.pmgaurav.safestrideai.utils.AppConstants
import com.pmgaurav.safestrideai.utils.FrameUtils
import com.pmgaurav.safestrideai.utils.RectF as UtilsRectF

object NmsProcessor {

    private const val TAG = "NmsProcessor"
    private val CLASS_IOU_THRESHOLDS = mapOf(
        "car"           to 0.30f,
        "truck"         to 0.30f,
        "bus"           to 0.30f,
        "motorcycle"    to 0.35f,
        "autorickshaw"  to 0.35f,
        "auto-rickshaw" to 0.35f,
        "bicycle"       to 0.35f,
        "scooter"       to 0.35f,
        "person"        to 0.40f,
        "tractor"       to 0.28f,
        "tempo"         to 0.28f,
        "animal"        to 0.38f,
        "cow"           to 0.32f,
        "dog"           to 0.38f
    )
    private const val DEFAULT_IOU = AppConstants.NMS_IOU_THRESHOLD

    fun applyNms(
        detections: List<RawDetection>
    ): List<DetectionResult> {
        if (detections.isEmpty()) return emptyList()

        val result = detections
            .groupBy { it.className.lowercase() }
            .flatMap { (className, classDets) ->
                val iouThreshold = CLASS_IOU_THRESHOLDS[className] ?: DEFAULT_IOU
                nmsForClass(classDets, iouThreshold, className)
            }
            .sortedByDescending { it.confidence }

        Log.d(TAG, "NMS: ${detections.size} â†’ ${result.size} detections")
        return result
    }

    private fun nmsForClass(
        detections:   List<RawDetection>,
        iouThreshold: Float,
        className:    String
    ): List<DetectionResult> {

        if (detections.isEmpty()) return emptyList()

        val sorted     = detections.sortedByDescending { it.confidence }.toMutableList()
        val kept       = mutableListOf<DetectionResult>()
        val suppressed = mutableSetOf<Int>()

        for (i in sorted.indices) {
            if (i in suppressed) continue

            val best = sorted[i]
            kept.add(
                DetectionResult(
                    label       = best.className,
                    confidence  = best.confidence,
                    boundingBox = best.boundingBox
                )
            )

            for (j in i + 1 until sorted.size) {
                if (j in suppressed) continue
                val iouVal = iou(best.boundingBox, sorted[j].boundingBox)
                if (iouVal > iouThreshold) {
                    suppressed.add(j)
                }
            }
        }

        if (detections.size > kept.size) {
            Log.v(TAG, "$className: ${detections.size} â†’ ${kept.size} (IoU=$iouThreshold)")
        }

        return kept
    }

    private fun iou(a: RectF, b: RectF): Float {
        val rectA = UtilsRectF(a.left, a.top, a.right, a.bottom)
        val rectB = UtilsRectF(b.left, b.top, b.right, b.bottom)
        return FrameUtils.iou(rectA, rectB)
    }
}
