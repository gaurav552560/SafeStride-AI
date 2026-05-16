package com.pmgaurav.safestrideai

import com.pmgaurav.safestrideai.fusion.MultiModalSensorFusion
import com.pmgaurav.safestrideai.v2x.V2XCollisionPredictor
import com.pmgaurav.safestrideai.smartcity.CrowdSafetyIntelligence

interface SafeStrideAPI {

    fun getCurrentSafetyState(): MultiModalSensorFusion.FusedResult

    fun getV2XRisks(): V2XCollisionPredictor.V2XCollisionRisk

    fun getCrowdMetrics(): CrowdSafetyIntelligence.CrowdDensityResult

    suspend fun submitFederatedUpdate()

    fun registerAlertListener(listener: AlertListener)

    interface AlertListener {
        fun onHazardDetected(label: String, distance: Float, tier: Int)
        fun onV2XWarning(message: String, ttc: Float)
        fun onSmartCityEvent(type: String, message: String)
    }
}

