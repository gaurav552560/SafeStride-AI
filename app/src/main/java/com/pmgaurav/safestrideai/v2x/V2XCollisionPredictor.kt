package com.pmgaurav.safestrideai.v2x

import com.pmgaurav.safestrideai.location.GPSLocationEngine
import com.pmgaurav.safestrideai.utils.DetectedObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class V2XCollisionPredictor @Inject constructor() {

    data class V2XCollisionRisk(
        val tier: Int,
        val primaryRisk: CollisionRisk?,
        val fusedTTC: Float,
        val warningType: WarningType,
        val message: String
    ) {
        companion object {
            val NONE = V2XCollisionRisk(0, null, Float.MAX_VALUE, WarningType.SINGLE_SOURCE, "")
        }
    }

    data class CollisionRisk(
        val source: RiskSource,
        val ttcSeconds: Float,
        val vehicleId: String,
        val confidence: Float,
        val className: String,
        val isAroundCorner: Boolean = false
    )

    companion object {
        private const val WEIGHT_CAMERA = 0.6f
        private const val WEIGHT_V2X    = 0.4f
    }

    enum class RiskSource { CAMERA, V2X }
    enum class WarningType { SINGLE_SOURCE, FUSED_CAMERA_V2X, AROUND_CORNER }

    fun predictCollision(
        cameraDetections: List<DetectedObject>,
        bsmMessages: List<V2XCommunicationEngine.BasicSafetyMessage>,
        pedestrianLocation: GPSLocationEngine.LocationData
    ): V2XCollisionRisk {
        val risks = mutableListOf<CollisionRisk>()


        cameraDetections.forEach { det ->
            if (det.ttc < 7.0f) {
                risks.add(CollisionRisk(
                    source = RiskSource.CAMERA,
                    ttcSeconds = det.ttc,
                    vehicleId = "cam-${det.id}",
                    confidence = det.confidence,
                    className = det.label
                ))
            }
        }


        for (bsm in bsmMessages) {
            val ttc = calculateV2XTTC(pedestrianLocation, bsm)
            if (ttc < 8.0f) {
                val isAroundCorner = !isLineOfSight(pedestrianLocation, bsm)
                risks.add(CollisionRisk(
                    source = RiskSource.V2X,
                    ttcSeconds = ttc,
                    vehicleId = bsm.vehicleId,
                    confidence = 0.9f,
                    className = "vehicle (V2X)",
                    isAroundCorner = isAroundCorner
                ))
            }
        }

        return fuseRisks(risks)
    }

    private fun calculateV2XTTC(
        ped: GPSLocationEngine.LocationData,
        veh: V2XCommunicationEngine.BasicSafetyMessage
    ): Float {

        val dist = haversineDistance(ped.latitude, ped.longitude, veh.latitude, veh.longitude)
        

        val pedVx = ped.speed * sin(Math.toRadians(ped.bearing.toDouble())).toFloat()
        val pedVy = ped.speed * cos(Math.toRadians(ped.bearing.toDouble())).toFloat()
        
        val vehVx = veh.speed * sin(Math.toRadians(veh.heading.toDouble())).toFloat()
        val vehVy = veh.speed * cos(Math.toRadians(veh.heading.toDouble())).toFloat()
        
        val relVx = vehVx - pedVx
        val relVy = vehVy - pedVy
        val relSpeed = sqrt(relVx * relVx + relVy * relVy)
        
        if (relSpeed <= 0.5f) return Float.MAX_VALUE
        

        val dx = (veh.latitude - ped.latitude).toFloat()
        val dy = (veh.longitude - ped.longitude).toFloat()
        val dotProduct = relVx * dx + relVy * dy
        
        if (dotProduct >= 0) return Float.MAX_VALUE
        
        return dist / relSpeed
    }

    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val r = 6371000f
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (r * c).toFloat()
    }

    private fun isLineOfSight(ped: GPSLocationEngine.LocationData, veh: V2XCommunicationEngine.BasicSafetyMessage): Boolean {

        val distance = haversineDistance(ped.latitude, ped.longitude, veh.latitude, veh.longitude)


        return veh.rssi > -70 || distance < 10f
    }

    private fun fuseRisks(risks: List<CollisionRisk>): V2XCollisionRisk {
        if (risks.isEmpty()) return V2XCollisionRisk.NONE
        
        val camRisk = risks.filter { it.source == RiskSource.CAMERA }.minByOrNull { it.ttcSeconds }
        val v2xRisk = risks.filter { it.source == RiskSource.V2X }.minByOrNull { it.ttcSeconds }
        
        val fusedTTC = when {
            camRisk != null && v2xRisk != null -> (camRisk.ttcSeconds * WEIGHT_CAMERA + v2xRisk.ttcSeconds * WEIGHT_V2X)
            camRisk != null -> camRisk.ttcSeconds
            else -> v2xRisk!!.ttcSeconds
        }

        val bestRisk = risks.minByOrNull { it.ttcSeconds }!!
        
        val isFused = camRisk != null && v2xRisk != null
        val warningType = when {
            bestRisk.isAroundCorner && bestRisk.ttcSeconds < 6.0f -> WarningType.AROUND_CORNER
            isFused -> WarningType.FUSED_CAMERA_V2X
            else -> WarningType.SINGLE_SOURCE
        }

        val message = when (warningType) {
            WarningType.AROUND_CORNER -> "Vehicle approaching from around corner!"
            WarningType.FUSED_CAMERA_V2X -> "CRITICAL: Multi-sensor confirmed collision risk!"
            else -> "${bestRisk.className} in ${fusedTTC.toInt()}s"
        }

        return V2XCollisionRisk(
            tier = determineTier(fusedTTC),
            primaryRisk = bestRisk,
            fusedTTC = fusedTTC,
            warningType = warningType,
            message = message
        )
    }

    private fun determineTier(ttc: Float): Int = when {
        ttc < 2.0f -> 3
        ttc < 4.0f -> 2
        ttc < 7.0f -> 1
        else -> 0
    }
}

