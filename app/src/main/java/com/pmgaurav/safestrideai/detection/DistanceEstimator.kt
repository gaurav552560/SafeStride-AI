package com.pmgaurav.safestrideai.detection

import android.graphics.RectF
import android.util.Log
import com.pmgaurav.safestrideai.utils.AppConstants
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DistanceEstimator"

@Singleton
class DistanceEstimator @Inject constructor() {

    companion object {
        private val REAL_WORLD_HEIGHTS = mapOf(
            "person" to AppConstants.HEIGHT_PERSON,
            "bicycle" to AppConstants.HEIGHT_CYCLE,
            "car" to AppConstants.HEIGHT_CAR,
            "motorcycle" to AppConstants.HEIGHT_CYCLE,
            "bus" to AppConstants.HEIGHT_BUS,
            "truck" to AppConstants.HEIGHT_TRUCK,
            "train" to AppConstants.HEIGHT_TRUCK,
            "traffic light" to 0.60f,
            "stop sign" to 0.75f,
        )
        private const val FOCAL_LENGTH_PX = AppConstants.FOCAL_LENGTH_PX
        private const val IMAGE_HEIGHT_PX = 480f
        const val IMAGE_WIDTH_PX = 640f
        private const val MIN_DISTANCE_M = 0.5f
        private const val MAX_DISTANCE_M = 150.0f
        private const val FALLBACK_DISTANCE_M = 20.0f


        fun formatDistance(distanceM: Float): String = when {
            distanceM >= 100f -> ">100m"
            distanceM >= 50f  -> "${"%.0f".format(distanceM)}m"
            distanceM >= 10f  -> "${"%.0f".format(distanceM)}m"
            else             -> "${"%.1f".format(distanceM)}m"
        }
    }
    fun estimate(box: RectF, label: String): Float {
        val realHeight = REAL_WORLD_HEIGHTS[label.lowercase()]
            ?: REAL_WORLD_HEIGHTS["car"]!!
        val boxHeightNorm = box.bottom - box.top
        val boxHeightPx   = boxHeightNorm * IMAGE_HEIGHT_PX

        if (boxHeightPx < 2f) {
            Log.v(TAG, "Box too small (${boxHeightPx}px) for $label â€” using fallback")
            return FALLBACK_DISTANCE_M
        }

        val distanceM = (FOCAL_LENGTH_PX * realHeight) / boxHeightPx

        val clamped = distanceM.coerceIn(MIN_DISTANCE_M, MAX_DISTANCE_M)

        Log.v(TAG, "$label: boxH=${boxHeightPx}px realH=${realHeight}m â†’ dist=${clamped}m")

        return clamped
    }
}

