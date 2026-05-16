package com.pmgaurav.safestrideai.detection

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LabelValidator @Inject constructor() {

    companion object {
        private val ROAD_IMPOSSIBLE_REMAPS = mapOf(
            "train" to "truck",
            "airplane" to null,
            "boat" to null,
            "elephant" to "person",
            "horse" to "motorcycle",
            "cow" to null,
            "sheep" to null,
            "bear" to null,
            "zebra" to null,
            "giraffe" to null,
            "frisbee" to null,
            "skis" to null,
            "snowboard" to null,
            "surfboard" to null,
            "tennis racket" to null,
            "wine glass" to null,
            "fork"  to null,
            "knife" to null,
            "toothbrush" to null,
            "hair drier" to null,
        )
        private val CLASS_CONFIDENCE_THRESHOLDS = mapOf(
            "person" to 0.50f,
            "bicycle" to 0.45f,
            "motorcycle" to 0.50f,
            "car" to 0.55f,
            "truck" to 0.60f,
            "bus" to 0.70f,
            "traffic light" to 0.45f,
            "stop sign" to 0.45f,
        )
        private const val DEFAULT_THRESHOLD = 0.50f
    }

    fun remapForRoadContext(detection: RawDetection): RawDetection? {
        val originalLabel = detection.className.lowercase().trim()

        val remappedLabel = if (ROAD_IMPOSSIBLE_REMAPS.containsKey(originalLabel)) {
            ROAD_IMPOSSIBLE_REMAPS[originalLabel] ?: return null
        } else {
            originalLabel
        }

        val threshold = CLASS_CONFIDENCE_THRESHOLDS[remappedLabel] ?: DEFAULT_THRESHOLD
        if (detection.confidence < threshold) return null

        return if (remappedLabel != originalLabel) {
            detection.copy(className = remappedLabel)
        } else {
            detection
        }
    }

    fun isSafetyClass(label: String): Boolean {
        return label.lowercase() in setOf(
            "person", "bicycle", "car", "motorcycle", "bus", "truck",
            "traffic light", "stop sign"
        )
    }
}

