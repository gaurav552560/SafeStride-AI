package com.pmgaurav.safestrideai.prediction

import android.content.Context
import android.util.Log
import com.pmgaurav.safestrideai.utils.DetectedObject
import java.util.*
import kotlin.math.*


@Suppress("Unused")
class PredictiveHazardEngine(context: Context) {

    private val trajectoryHistory = mutableMapOf<Int, Deque<TrajectoryPoint>>()
    
    private val windowSize = 10
    private val predictionHorizonMs = 2000L

    init {
        Log.d("PredictiveHazard", "Engine initialized for context: ${context.packageName}")
    }

    data class TrajectoryPoint(
        val x: Float,
        val y: Float,
        val timestamp: Long,
        val velocityX: Float = 0f,
        val velocityY: Float = 0f
    )

    data class PredictedHazard(
        val id: Int,
        val label: String,
        val confidence: Float,
        val timeToImpact: Float,
        val predictedX: Float,
        val predictedY: Float,
        val riskScore: Float
    )

    @Suppress("Unused")
    fun updateAndPredict(detections: List<DetectedObject>): List<PredictedHazard> {
        val now = System.currentTimeMillis()
        val predictions = mutableListOf<PredictedHazard>()

        detections.forEach { det ->
            val center = det.boundingBox.let { (it.left + it.right) / 2f to (it.top + it.bottom) / 2f }
            val history = trajectoryHistory.getOrPut(det.id) { ArrayDeque() }


            var vx = 0f
            var vy = 0f
            if (history.isNotEmpty()) {
                val last = history.peekLast()!!
                val dt = (now - last.timestamp) / 1000f
                if (dt > 0) {
                    vx = (center.first - last.x) / dt
                    vy = (center.second - last.y) / dt
                }
            }

            history.addLast(TrajectoryPoint(center.first, center.second, now, vx, vy))
            if (history.size > windowSize) history.removeFirst()


            if (history.size >= 3) {
                val predicted = predictFuturePosition(history)
                val risk = calculateRisk(det, vx, vy, predicted)
                
                if (risk > 0.6f) {
                    predictions.add(PredictedHazard(
                        id = det.id,
                        label = det.label,
                        confidence = det.confidence,
                        timeToImpact = det.ttc,
                        predictedX = predicted.first,
                        predictedY = predicted.second,
                        riskScore = risk
                    ))
                }
            }
        }


        val activeIds = detections.map { it.id }.toSet()
        trajectoryHistory.keys.retainAll(activeIds)

        return predictions
    }

    private fun predictFuturePosition(history: Deque<TrajectoryPoint>, horizonMs: Long = predictionHorizonMs): Pair<Float, Float> {
        val last = history.peekLast() ?: return 0f to 0f
        val avgVx = history.map { it.velocityX }.average().toFloat()
        val avgVy = history.map { it.velocityY }.average().toFloat()
        
        val dt = horizonMs / 1000f
        return (last.x + avgVx * dt) to (last.y + avgVy * dt)
    }

    private fun calculateRisk(det: DetectedObject, vx: Float, vy: Float, predicted: Pair<Float, Float>): Float {
        val speed = sqrt(vx * vx + vy * vy)
        val distanceFactor = 1.0f / (det.distanceMeters.coerceAtLeast(1.0f))
        val ttcFactor = if (det.ttc < 3f) 1.2f else 1.0f
        

        val predictedDistToCenter = sqrt((predicted.first - 0.5f).pow(2) + (predicted.second - 0.5f).pow(2))
        val dangerZoneFactor = (1.0f - predictedDistToCenter).coerceIn(0f, 1f)
        

        val headingToCenter = if (speed > 0) {
            val dxToCenter = 0.5f - (det.boundingBox.left + det.boundingBox.right) / 2f
            val dyToCenter = 0.5f - (det.boundingBox.top + det.boundingBox.bottom) / 2f
            val dot = (vx * dxToCenter + vy * dyToCenter) / (speed * sqrt(dxToCenter*dxToCenter + dyToCenter*dyToCenter))
            dot.coerceIn(0f, 1f)
        } else 0f

        return (speed * 0.2f + distanceFactor * 0.3f + headingToCenter * 0.3f + dangerZoneFactor * 0.2f) * ttcFactor
    }
}

