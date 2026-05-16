package com.pmgaurav.safestrideai.utils

import com.pmgaurav.safestrideai.data.HazardPin
import kotlin.math.*

object SafetyUtils {

    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371e3 
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val a = sin(deltaPhi / 2) * sin(deltaPhi / 2) +
                cos(phi1) * cos(phi2) *
                sin(deltaLambda / 2) * sin(deltaLambda / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return r * c
    }

    fun clusterHazards(hazards: List<HazardPin>, clusterRadius: Double = 30.0): List<List<HazardPin>> {
        val clusters = mutableListOf<MutableList<HazardPin>>()
        val visited = mutableSetOf<Long>()

        for (hazard in hazards) {
            if (hazard.id in visited) continue

            val currentCluster = mutableListOf<HazardPin>()
            val queue = ArrayDeque<HazardPin>()
            queue.add(hazard)
            visited.add(hazard.id)

            while (queue.isNotEmpty()) {
                val h = queue.removeFirst()
                currentCluster.add(h)

                for (neighbor in hazards) {
                    if (neighbor.id !in visited) {
                        val distance = calculateDistance(h.latitude, h.longitude, neighbor.latitude, neighbor.longitude)
                        if (distance <= clusterRadius) {
                            visited.add(neighbor.id)
                            queue.add(neighbor)
                        }
                    }
                }
            }
            clusters.add(currentCluster)
        }
        return clusters
    }

    fun calculateSafetyScore(latitude: Double, longitude: Double, hazards: List<HazardPin>, radius: Double = 100.0): Float {
        if (hazards.isEmpty()) return 100f

        var penalty = 0f
        val radiusSq = radius * radius
        for (hazard in hazards) {

            val dLat = (latitude - hazard.latitude) * 111111.0
            val dLng = (longitude - hazard.longitude) * 111111.0
            val distSq = (dLat * dLat) + (dLng * dLng)
            
            if (distSq <= radiusSq) {
                val distance = sqrt(distSq)
                val distanceWeight = (radius - distance) / radius
                val hazardWeight = hazard.severity / 5f
                penalty += (distanceWeight * hazardWeight).toFloat() * 20f
            }
        }

        return max(0f, 100f - penalty)
    }
}

