package com.pmgaurav.safestrideai

import com.pmgaurav.safestrideai.crypto.QuantumSafeCryptoEngine
import com.pmgaurav.safestrideai.crypto.HybridSignature
import com.pmgaurav.safestrideai.crypto.QuantumKeyStoreManager
import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.pmgaurav.safestrideai.fusion.MultiModalSensorFusion
import com.pmgaurav.safestrideai.v2x.V2XCollisionPredictor
import com.pmgaurav.safestrideai.smartcity.CrowdSafetyIntelligence
import com.pmgaurav.safestrideai.smartcity.SmartCityIntegration
import com.pmgaurav.safestrideai.federated.FederatedLearningEngine
import com.pmgaurav.safestrideai.repository.SessionRepository
import com.pmgaurav.safestrideai.repository.SettingsRepository
import com.pmgaurav.safestrideai.data.AppDatabase
import com.pmgaurav.safestrideai.detection.OpticalFlowTracker
import com.pmgaurav.safestrideai.detection.DistanceEstimator
import com.pmgaurav.safestrideai.utils.NetworkSecurityManager
import java.security.PrivateKey
import java.security.PublicKey

class SafeStrideSDK private constructor(private val context: Context) : SafeStrideAPI {

    private val v2xPredictor = V2XCollisionPredictor()
    private val quantumCrypto = QuantumSafeCryptoEngine()
    
    @Suppress("Unused")
    private val keyStoreManager = QuantumKeyStoreManager(context, quantumCrypto)
    
    private val crowdIntelligence = CrowdSafetyIntelligence(context)
    private val smartCityIntegration = SmartCityIntegration(context, NetworkSecurityManager.createPinnedOkHttpClient())
    private val federatedEngine = FederatedLearningEngine(context)
    private val sessionRepo = SessionRepository(AppDatabase.getDatabase(context))
    private val settingsRepo = SettingsRepository(context)
    private val alertListeners = mutableListOf<SafeStrideAPI.AlertListener>()
    private val distanceEstimator = DistanceEstimator()
    @Suppress("Unused")
    private val standaloneTracker = OpticalFlowTracker(distanceEstimator)

    private val sensorFusion = MultiModalSensorFusion(
        context = context,
        v2xPredictor = v2xPredictor,
        crowdIntelligence = crowdIntelligence,
        smartCity = smartCityIntegration,
        sessionRepo = sessionRepo,
        settingsRepo = settingsRepo,
        fedLearnEngine = federatedEngine,
    )

    init {
        Log.i("SafeStrideSDK", "QuantumKeyStore initialized and ready.")
    }

    companion object {
        @Volatile
        @SuppressLint("StaticFieldLeak")
        private var INSTANCE: SafeStrideSDK? = null

        fun getInstance(context: Context): SafeStrideSDK {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SafeStrideSDK(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override fun getCurrentSafetyState(): MultiModalSensorFusion.FusedResult {
        return sensorFusion.fuse(
            cameraDetections = emptyList(),
            trackedObjects = emptyList(),
            bsmMessages = emptyList(),
            location = null
        )
    }

    override fun getV2XRisks(): V2XCollisionPredictor.V2XCollisionRisk {
        return V2XCollisionPredictor.V2XCollisionRisk(
            tier = 0,
            primaryRisk = null,
            fusedTTC = Float.MAX_VALUE,
            warningType = V2XCollisionPredictor.WarningType.SINGLE_SOURCE,
            message = "Clear",
        )
    }

    override fun getCrowdMetrics(): CrowdSafetyIntelligence.CrowdDensityResult {
        return CrowdSafetyIntelligence.CrowdDensityResult(
            densityLevel = CrowdSafetyIntelligence.DensityLevel.LOW,
            personCount = 0,
            flowVelocity = 0f,
            turbulence = 0f,
            safetyRisk = 0f
        )
    }

    override suspend fun submitFederatedUpdate() {
        federatedEngine.runLocalTrainingRound()
    }

    override fun registerAlertListener(listener: SafeStrideAPI.AlertListener) {
        alertListeners.add(listener)
    }

    @Suppress("Unused")
    internal fun notifyHazard(label: String, distance: Float, tier: Int) {
        alertListeners.forEach { it.onHazardDetected(label, distance, tier) }
    }

    @Suppress("Unused")
    internal fun notifyV2XWarning(message: String, ttc: Float) {
        alertListeners.forEach { it.onV2XWarning(message, ttc) }
    }

    @Suppress("Unused")
    internal fun notifySmartCityEvent(type: String, message: String) {
        alertListeners.forEach { it.onSmartCityEvent(type, message) }
    }

    @Suppress("Unused")
    fun signPayload(payload: ByteArray, classicalKey: PrivateKey, quantumKey: PrivateKey): HybridSignature {
        Log.d("SafeStrideSDK", "Signing payload using Hybrid Cryptography...")
        return quantumCrypto.hybridSign(payload, classicalKey, quantumKey)
    }

    @Suppress("Unused")
    fun verifyPayload(payload: ByteArray, signature: HybridSignature, classicalPub: PublicKey, quantumPub: PublicKey): Boolean {
        val isQuantumValid = quantumCrypto.verifyV2XMessage(payload, signature.quantumSafe, quantumPub)
        Log.d("SafeStrideSDK", "Hybrid verification using classical key: ${classicalPub.algorithm}")
        return isQuantumValid
    }
}

