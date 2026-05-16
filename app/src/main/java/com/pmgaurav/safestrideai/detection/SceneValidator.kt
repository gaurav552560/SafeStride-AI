package com.pmgaurav.safestrideai.detection

import android.graphics.Bitmap
import androidx.core.graphics.get
import javax.inject.Inject
import javax.inject.Singleton

sealed class SceneType {
    object Outdoor : SceneType()
    @Suppress("unused")
    object Indoor  : SceneType()
    object Night   : SceneType()
    object Unknown : SceneType()
}

data class SceneValidationResult(
    val isValid: Boolean,
    val sceneType: SceneType,
    val reason: String,
    val brightness: Float
)

@Singleton
class SceneValidator @Inject constructor() {

    private var frameCount = 0
    private var consecutiveBlackFrames = 0

    companion object {
        private const val MIN_BRIGHTNESS    = 3f
        private const val MAX_BRIGHTNESS    = 252f  
        private const val NIGHT_THRESHOLD   = 40f
    }

    fun validate(bitmap: Bitmap): SceneValidationResult {
        frameCount++
        val brightness = calculateBrightness(bitmap)

        return when {
            brightness < MIN_BRIGHTNESS -> {
                consecutiveBlackFrames++
                SceneValidationResult(
                    isValid = true,
                    sceneType = SceneType.Unknown,
                    reason = "Frame too dark (brightness=${"%.1f".format(brightness)})",
                    brightness = brightness
                )
            }
            brightness > MAX_BRIGHTNESS -> SceneValidationResult(
                isValid = false,
                sceneType = SceneType.Unknown,
                reason = "Frame overexposed",
                brightness = brightness
            )
            else -> {
                consecutiveBlackFrames = 0
                val sceneType = when {
                    brightness < NIGHT_THRESHOLD -> SceneType.Night
                    else -> SceneType.Outdoor
                }
                SceneValidationResult(
                    isValid = true,
                    sceneType = sceneType,
                    reason = "OK",
                    brightness = brightness
                )
            }
        }
    }

    private fun calculateBrightness(bitmap: Bitmap): Float {
        val step = maxOf(bitmap.width / 16, 1)
        var sum = 0L
        var count = 0
        
        for (x in 0 until bitmap.width step step) {
            for (y in 0 until bitmap.height step step) {
                val px = bitmap[x, y]
                val r = (px shr 16) and 0xFF
                val g = (px shr 8)  and 0xFF
                val b =  px         and 0xFF
                sum += (r * 299 + g * 587 + b * 114) / 1000
                count++
            }
        }
        return if (count > 0) sum.toFloat() / count else 0f
    }

    fun reset() { frameCount = 0; consecutiveBlackFrames = 0 }

    @Suppress("unused")
    fun analyzeScene(bitmap: Bitmap): SceneType = validate(bitmap).sceneType
}

