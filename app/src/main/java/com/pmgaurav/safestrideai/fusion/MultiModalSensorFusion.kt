package com.pmgaurav.safestrideai.fusion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.pmgaurav.safestrideai.data.DetectionEvent
import com.pmgaurav.safestrideai.location.GPSLocationEngine
import com.pmgaurav.safestrideai.repository.SettingsRepository
import com.pmgaurav.safestrideai.repository.SessionRepository
import com.pmgaurav.safestrideai.smartcity.CrowdSafetyIntelligence
import com.pmgaurav.safestrideai.smartcity.SmartCityIntegration
import com.pmgaurav.safestrideai.data.HazardPin
import com.pmgaurav.safestrideai.detection.TrackedObject
import com.pmgaurav.safestrideai.federated.FederatedLearningEngine
import com.pmgaurav.safestrideai.utils.*
import com.pmgaurav.safestrideai.v2x.V2XCollisionPredictor
import com.pmgaurav.safestrideai.v2x.V2XCommunicationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.math.*

@Singleton
class MultiModalSensorFusion @Inject constructor(
    @ApplicationContext context: Context,
    private val v2xPredictor: V2XCollisionPredictor,
    private val crowdIntelligence: CrowdSafetyIntelligence,
    private val smartCity: SmartCityIntegration,
    private val sessionRepo: SessionRepository,
    private val settingsRepo: SettingsRepository,
    private val fedLearnEngine: FederatedLearningEngine
) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val imuAssistant by lazy { IMUAssistant() }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var currentSessionId: Long? = null
    private var userConfidenceThreshold: Float = 0.5f
    private var isV2XEnabled: Boolean = true
    

    private val cachedSignals = mutableListOf<SmartCityIntegration.SignalPhaseAndTiming>()
    private var lastSignalFetchLocation: Pair<Double, Double>? = null
    

    private var lastEventLogTime = 0L
    private val eventLogInterval = 3000L

    init {

        serviceScope.launch {
            settingsRepo.detectionConfidence.collectLatest { confidence ->
                userConfidenceThreshold = confidence
            }
        }
        serviceScope.launch {
            settingsRepo.v2xEnabled.collectLatest { enabled ->
                isV2XEnabled = enabled
            }
        }
    }

    fun setSessionId(id: Long?) {
        currentSessionId = id
    }

    data class FusedResult(
        val detections: List<FusedDetection>,
        val userStatus: UserStatus,
        val crowdMetrics: CrowdSafetyIntelligence.CrowdDensityResult? = null,
        val crowdAlert: String? = null,
        val smartCityAlert: SmartCityIntegration.SignalAlert? = null,
        val safetyScore: Int = 100,
        val v2xRisk: V2XCollisionPredictor.V2XCollisionRisk = V2XCollisionPredictor.V2XCollisionRisk.NONE
    )

    data class FusedDetection(
        val id: Int,
        val label: String,
        val distance: Float,
        val ttc: Float,
        val dangerTier: Int,
        val confidence: Float,
        val source: String
    )

    data class UserStatus(
        val isFalling: Boolean,
        val cameraShake: Float,
        val activity: String
    )

    fun fuse(
        cameraDetections: List<DetectedObject>,
        trackedObjects: List<TrackedObject>,
        bsmMessages: List<V2XCommunicationEngine.BasicSafetyMessage>,
        location: GPSLocationEngine.LocationData?,
        nearbyHazards: List<HazardPin> = emptyList()
    ): FusedResult {
        val v2xRisk = if (isV2XEnabled && location != null) {
            v2xPredictor.predictCollision(cameraDetections, bsmMessages, location)
        } else {
            V2XCollisionPredictor.V2XCollisionRisk.NONE
        }

        val filteredCameraDetections = cameraDetections.filter { it.confidence >= userConfidenceThreshold }
        
        val fusedDetections = mutableListOf<FusedDetection>()

        filteredCameraDetections.forEach { det ->
            fusedDetections.add(FusedDetection(
                id = det.id,
                label = det.label,
                distance = det.distanceMeters,
                ttc = det.ttc,
                dangerTier = when {
                    det.ttc in 0f..AppConstants.TTC_DANGER_THRESHOLD_S || 
                    det.distanceMeters < AppConstants.DISTANCE_DANGER_M -> 3
                    
                    det.ttc in AppConstants.TTC_DANGER_THRESHOLD_S..AppConstants.TTC_WARNING_THRESHOLD_S ||
                    det.distanceMeters < AppConstants.DISTANCE_WARNING_M -> 2
                    
                    det.ttc in AppConstants.TTC_WARNING_THRESHOLD_S..AppConstants.TTC_ADVISORY_THRESHOLD_S ||
                    det.distanceMeters < AppConstants.DISTANCE_ADVISORY_M -> 1

                    else -> 0
                },
                confidence = det.confidence,
                source = "CAMERA"
            ))
        }

        v2xRisk.primaryRisk?.let { risk ->
            if (risk.confidence >= userConfidenceThreshold) {
                fusedDetections.add(FusedDetection(
                    id = risk.vehicleId.hashCode(),
                    label = risk.className,
                    distance = 0f,
                    ttc = risk.ttcSeconds,
                    dangerTier = v2xRisk.tier,
                    confidence = risk.confidence,
                    source = if (risk.isAroundCorner) "V2X_AROUND_CORNER" else "V2X"
                ))
            }
        }

        val crowdMetrics = crowdIntelligence.analyzeCrowd(trackedObjects)
        val crowdAlert = crowdIntelligence.getSafetyAlert(crowdMetrics)


        var smartCityAlert: SmartCityIntegration.SignalAlert? = null
        if (location != null) {
            syncNearbySignals(location)
            

            val nearestSignal = cachedSignals.minByOrNull { sig ->
                val results = FloatArray(1)
                android.location.Location.distanceBetween(
                    location.latitude, location.longitude,
                    sig.latitude, sig.longitude,
                    results
                )
                results[0]
            }

            if (nearestSignal != null) {
                val results = FloatArray(1)
                android.location.Location.distanceBetween(
                    location.latitude, location.longitude,
                    nearestSignal.latitude, nearestSignal.longitude,
                    results
                )
                val distToIntersection = results[0]


                if (distToIntersection < 50f) {
                    smartCityAlert = smartCity.generateSignalAlert(
                        signal = nearestSignal,
                        userSpeed = location.speed,
                        distanceToIntersection = distToIntersection
                    )
                }
            }
        }

        var baseScore = 100
        

        if (location != null && nearbyHazards.isNotEmpty()) {
            val environmentalScore = SafetyUtils.calculateSafetyScore(
                location.latitude, location.longitude, nearbyHazards
            )
            baseScore = environmentalScore.toInt()
        }


        if (v2xRisk.tier >= 2) baseScore -= 30
        if (crowdMetrics.safetyRisk > 0.7f) baseScore -= 20
        if (smartCityAlert != null && smartCityAlert.tier >= 2) baseScore -= 25
        fusedDetections.forEach { if (it.dangerTier == 3) baseScore -= 40 }
        if (imuAssistant.isFalling) baseScore -= 50
        
        val finalScore = baseScore.coerceIn(0, 100)

        currentSessionId?.let { sessionId ->
            val now = System.currentTimeMillis()
            val criticalDetections = fusedDetections.filter { it.dangerTier >= 3 }
            
            if ((criticalDetections.isNotEmpty() || imuAssistant.isFalling || (smartCityAlert?.tier ?: 0) >= 2) && (now - lastEventLogTime > eventLogInterval)) {
                lastEventLogTime = now
                serviceScope.launch {
                    criticalDetections.forEach { critical ->

                        sessionRepo.logEvent(
                            DetectionEvent(
                                sessionId = sessionId,
                                latitude = location?.latitude,
                                longitude = location?.longitude,
                                detectedObject = critical.label,
                                confidence = critical.confidence,
                                distanceToHazard = critical.distance,
                                alertTriggered = true
                            )
                        )
                        

                        AnalyticsUtils.logDangerEvent(
                            type = critical.label,
                            severity = "DANGER",
                            ttc = critical.ttc
                        )
                    }
                    
                    smartCityAlert?.let { alert ->
                        if (alert.tier >= 2) {
                            sessionRepo.logEvent(
                                DetectionEvent(
                                    sessionId = sessionId,
                                    latitude = location?.latitude,
                                    longitude = location?.longitude,
                                    detectedObject = "SIGNAL_VIOLATION_RISK",
                                    confidence = 1.0f,
                                    alertTriggered = true
                                )
                            )
                        }
                    }

                    if (imuAssistant.isFalling) {
                        sessionRepo.logEvent(
                            DetectionEvent(
                                sessionId = sessionId,
                                latitude = location?.latitude,
                                longitude = location?.longitude,
                                detectedObject = "USER_FALL",
                                confidence = 1.0f,
                                alertTriggered = true
                            )
                        )
                    }
                }
            }
        }

        val fusedResult = FusedResult(
            detections = fusedDetections.sortedBy { it.ttc },
            userStatus = UserStatus(
                isFalling = imuAssistant.isFalling,
                cameraShake = imuAssistant.cameraShake,
                activity = if (imuAssistant.isFalling) "FALL_DETECTED" else "WALKING"
            ),
            crowdMetrics = crowdMetrics,
            crowdAlert = crowdAlert,
            smartCityAlert = smartCityAlert,
            safetyScore = finalScore,
            v2xRisk = v2xRisk
        )

        if (filteredCameraDetections.isNotEmpty()) {
            fedLearnEngine.bufferData(filteredCameraDetections, location)
        }

        return fusedResult
    }

    private fun syncNearbySignals(location: GPSLocationEngine.LocationData) {
        val last = lastSignalFetchLocation
        val distanceMoved = if (last != null) {
            val results = FloatArray(1)
            android.location.Location.distanceBetween(
                location.latitude, location.longitude,
                last.first, last.second,
                results
            )
            results[0]
        } else {
            Float.MAX_VALUE
        }


        if (distanceMoved > 500f || cachedSignals.isEmpty()) {
            lastSignalFetchLocation = location.latitude to location.longitude
            serviceScope.launch {
                val newSignals = smartCity.getNearbySignals(location.latitude, location.longitude)
                if (newSignals.isNotEmpty()) {
                    synchronized(cachedSignals) {
                        cachedSignals.clear()
                        cachedSignals.addAll(newSignals)
                    }
                    Log.d("Fusion", "Smart City Cache Updated: ${newSignals.size} signals")
                }
            }
        }
    }

    inner class IMUAssistant : SensorEventListener {
        var isFalling = false
        var cameraShake = 0f
        
        init {
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }

        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitude = sqrt((x * x) + (y * y) + (z * z))
                
                if (magnitude !in 2f..35f) {
                    isFalling = true
                }
                
                cameraShake = abs(magnitude - 9.8f)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }
}

