package com.pmgaurav.safestrideai.smartcity

import android.content.Context
import android.util.Log
import com.pmgaurav.safestrideai.detection.TrackedObject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class CrowdSafetyIntelligence @Inject constructor(
    @ApplicationContext context: Context
) {

    init {
        Log.d("CrowdSafety", "Intelligence engine starting for: ${context.packageName}")
    }

    private val historyWindow = LinkedList<List<TrackedObject>>()
    private val windowSize = 10 

    data class CrowdDensityResult(
        val densityLevel: DensityLevel,
        val personCount: Int,
        val flowVelocity: Float,
        val turbulence: Float,
        val safetyRisk: Float
    )

    enum class DensityLevel {
        LOW, MODERATE, HIGH, CRITICAL
    }

    fun analyzeCrowd(trackedObjects: List<TrackedObject>): CrowdDensityResult {
        val people = trackedObjects.filter { it.label == "person" }
        val count = people.size

        historyWindow.addLast(people)
        if (historyWindow.size > windowSize) historyWindow.removeFirst()

        val level = when {
            count > 15 -> DensityLevel.CRITICAL
            count > 8  -> DensityLevel.HIGH
            count > 4  -> DensityLevel.MODERATE
            else       -> DensityLevel.LOW
        }

        var flowVelocity = 0f
        var turbulence = 0f

        if (historyWindow.size >= 2) {
            val currentFrame = historyWindow.last()
            val previousFrame = historyWindow[historyWindow.size - 2]
            
            val velocities = mutableListOf<Float>()
            val directions = mutableListOf<Float>()
            
            currentFrame.forEach { curr ->
                previousFrame.find { it.id == curr.id }?.let { prev ->
                    val dx = curr.box.centerX() - prev.box.centerX()
                    val dy = curr.box.centerY() - prev.box.centerY()
                    val dist = sqrt(dx * dx + dy * dy)
                    
                    velocities.add(dist)
                    directions.add(atan2(dy, dx))
                }
            }
            
            if (velocities.isNotEmpty()) {
                flowVelocity = velocities.average().toFloat()
                
                val sinSum = directions.sumOf { sin(it.toDouble()) }
                val cosSum = directions.sumOf { cos(it.toDouble()) }

                val r = sqrt(sinSum * sinSum + cosSum * cosSum) / directions.size
                turbulence = (1.0 - r).toFloat().coerceIn(0f, 1f)
            }
        }

        var risk = (count / 20f).coerceIn(0f, 0.4f)
        
        if (level == DensityLevel.CRITICAL) risk += 0.4f
        
        if (turbulence > 0.5f && level >= DensityLevel.HIGH) {
            risk += 0.2f
        }

        return CrowdDensityResult(
            level, count, flowVelocity, turbulence, risk.coerceIn(0f, 1f)
        )
    }

    fun getSafetyAlert(result: CrowdDensityResult): String? {
        return when {
            result.safetyRisk > 0.8f -> "CRITICAL: Potential stampede risk."
            result.safetyRisk > 0.6f -> "WARNING: Hazardous crowd density."
            result.densityLevel == DensityLevel.HIGH -> "CAUTION: Heavy crowd detected."
            else -> null
        }
    }
}

