package com.pmgaurav.safestrideai.detection

import android.graphics.RectF
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FalseDetectionFilter @Inject constructor() {

    companion object {
        private const val PERSON_MIN_CONFIDENCE = 0.50f
        private const val PERSON_MIN_BOX_AREA = 0.0008f
        private const val MIN_CONFIDENCE = 0.35f
        private const val MIN_BOX_AREA  = 0.0008f
        private const val MAX_BOX_AREA = 0.98f
        private const val MIN_ASPECT_RATIO = 0.05f
        private const val MAX_ASPECT_RATIO = 15.0f
        private const val MAX_DUPLICATE_IOU = 0.45f
    }

    data class FilterState(
        val passed: Int = 0,
        val rejectedConfidence: Int = 0,
        val rejectedSize: Int = 0,
        val rejectedDuplicate: Int = 0,
    )

    private var state = FilterState()

    fun filter(detections: List<RawDetection>): List<RawDetection> {
        state = FilterState()
        val candidates = detections.filter { det ->
            val isPerson = det.className.lowercase() == "person"
            val confThreshold = if (isPerson) PERSON_MIN_CONFIDENCE else MIN_CONFIDENCE
            val areaThreshold = if (isPerson) PERSON_MIN_BOX_AREA   else MIN_BOX_AREA

            val area = det.boundingBox.width() * det.boundingBox.height()
            val ar   = if (det.boundingBox.height() > 0f)
                det.boundingBox.width() / det.boundingBox.height() else 0f

            if (det.confidence < confThreshold) {
                state = state.copy(rejectedConfidence = state.rejectedConfidence + 1)
                return@filter false
            }
            if (area !in areaThreshold..MAX_BOX_AREA) {
                state = state.copy(rejectedSize = state.rejectedSize + 1)
                return@filter false
            }
            if (!isPerson && ar !in MIN_ASPECT_RATIO..MAX_ASPECT_RATIO) {
                state = state.copy(rejectedSize = state.rejectedSize + 1)
                return@filter false
            }
            true
        }
        val result = mutableListOf<RawDetection>()
        for (det in candidates.sortedByDescending { it.confidence }) {
            val isDuplicate = result.any { existing ->
                existing.className == det.className &&
                iou(existing.boundingBox, det.boundingBox) > MAX_DUPLICATE_IOU
            }
            if (!isDuplicate) {
                result.add(det)
                state = state.copy(passed = state.passed + 1)
            } else {
                state = state.copy(rejectedDuplicate = state.rejectedDuplicate + 1)
            }
        }
        return result
    }
    private fun iou(a: RectF, b: RectF): Float {
        val interLeft = maxOf(a.left,b.left)
        val interTop = maxOf(a.top,b.top)
        val interRight = minOf(a.right,b.right)
        val interBottom = minOf(a.bottom,b.bottom)
        if ((interRight <= interLeft) || (interBottom <= interTop)) return 0f
        val inter = (interRight - interLeft) * (interBottom - interTop)
        val aArea = a.width() * a.height()
        val bArea = b.width() * b.height()
        val union = aArea + bArea - inter
        return if (union <= 0f) 0f else inter / union
    }

    @Suppress("unused")
    fun getState() = state
}

