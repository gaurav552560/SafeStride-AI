package com.pmgaurav.safestrideai.federated

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.*
import kotlin.math.*

@Singleton
class FederatedLearningEngine @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val MODEL_FILE = "pedestrian_vehicle_detect.tflite"
        

        private const val DP_EPSILON = 1.0f
        private const val DP_CLIP_NORM = 1.0f
    }

    private val localTrainingBuffer = mutableListOf<LocalTrainingExample>()

    data class LocalTrainingExample(
        val inputFeatures: FloatArray,
        val labelVector: FloatArray,
        val confidenceWeight: Float,
        val timestamp: Long,
        val wasAlertCorrect: Boolean
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as LocalTrainingExample
            if (!inputFeatures.contentEquals(other.inputFeatures)) return false
            if (!labelVector.contentEquals(other.labelVector)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = inputFeatures.contentHashCode()
            result = 31 * result + labelVector.contentHashCode()
            return result
        }
    }

    data class FederatedUpdate(
        val clientId: String,
        val weightDelta: Array<FloatArray>,
        val numExamples: Int,
        val roundId: Int,
        val timestamp: Long
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as FederatedUpdate
            return weightDelta.contentDeepEquals(other.weightDelta)
        }

        override fun hashCode(): Int {
            return weightDelta.contentDeepHashCode()
        }
    }

    fun bufferData(
        detections: List<com.pmgaurav.safestrideai.utils.DetectedObject>,
        location: com.pmgaurav.safestrideai.location.GPSLocationEngine.LocationData?
    ) {
        val locationTag = location?.let { " at [${it.latitude}, ${it.longitude}]" } ?: ""
        detections.forEach { det ->
            Log.d("FedLearn", "Buffering detection: ${det.label}$locationTag")
            collectTrainingExample(FloatArray(128), det.label, det.confidence, true)
        }
    }

    fun collectTrainingExample(
        imageFeatures: FloatArray,
        detectedClass: String,
        confidence: Float,
        userFeedbackCorrect: Boolean?
    ) {
        if (confidence < 0.85f && userFeedbackCorrect != true) return

        val example = LocalTrainingExample(
            inputFeatures = imageFeatures,
            labelVector = buildLabelVector(detectedClass),
            confidenceWeight = confidence,
            timestamp = System.currentTimeMillis(),
            wasAlertCorrect = userFeedbackCorrect ?: true
        )

        localTrainingBuffer.add(example)
        if (localTrainingBuffer.size > 500) {
            localTrainingBuffer.removeAt(0)
        }
    }

    suspend fun runLocalTrainingRound(): FederatedUpdate? {
        if (localTrainingBuffer.size < 10) return null

        return withContext(Dispatchers.Default) {
            Log.d("FedLearn", "Loading model from $MODEL_FILE using context $context")
            Log.d("FedLearn", "Starting local training with ${localTrainingBuffer.size} examples")
            
            val mockDelta = Array(1) { FloatArray(10) { (Random().nextFloat() - 0.5f) * 0.01f } }
            

            val privateDelta = applyDifferentialPrivacy(mockDelta)
            
            val update = FederatedUpdate(
                clientId = UUID.randomUUID().toString(),
                weightDelta = privateDelta,
                numExamples = localTrainingBuffer.size,
                roundId = 1,
                timestamp = System.currentTimeMillis()
            )
            
            localTrainingBuffer.clear()
            update
        }
    }

    private fun applyDifferentialPrivacy(
        delta: Array<FloatArray>,
        epsilon: Float = DP_EPSILON,
        clipNorm: Float = DP_CLIP_NORM
    ): Array<FloatArray> {
        val rng = Random()
        val noiseStd = (clipNorm / epsilon) * sqrt(2f * ln(1.25f / 0.001f))
        
        return delta.map { layer ->
            layer.map { w ->
                val clipped = w.coerceIn(-clipNorm, clipNorm)
                clipped + (rng.nextGaussian() * noiseStd).toFloat()
            }.toFloatArray()
        }.toTypedArray()
    }

    private fun buildLabelVector(detectedClass: String): FloatArray {
        Log.d("FedLearn", "Building label vector for $detectedClass")
        val vector = FloatArray(16)
        val index = detectedClass.hashCode().absoluteValue % 16
        vector[index] = 1.0f
        return vector
    }
}

