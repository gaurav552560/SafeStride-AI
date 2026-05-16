package com.pmgaurav.safestrideai.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.pmgaurav.safestrideai.fusion.MultiModalSensorFusion
import com.pmgaurav.safestrideai.v2x.V2XCommunicationEngine
import com.pmgaurav.safestrideai.smartcity.SmartCityIntegration
import com.pmgaurav.safestrideai.accessibility.AISceneDescriber
import com.pmgaurav.safestrideai.alert.AlertManager
import com.pmgaurav.safestrideai.ar.AROverlayManager
import com.pmgaurav.safestrideai.camera.CameraManager
import com.pmgaurav.safestrideai.data.HazardPin
import com.pmgaurav.safestrideai.detection.*
import com.pmgaurav.safestrideai.gesture.GestureControlEngine
import com.pmgaurav.safestrideai.gesture.HapticLanguage
import com.pmgaurav.safestrideai.location.GPSLocationEngine
import com.pmgaurav.safestrideai.repository.*
import com.pmgaurav.safestrideai.utils.*
import com.pmgaurav.safestrideai.service.DetectionForegroundService
import com.pmgaurav.safestrideai.wear.WearSyncManager
import com.pmgaurav.safestrideai.wear.WearEvent
import com.google.ar.core.Frame
import com.google.ar.core.Session
import android.content.Intent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

private const val TAG = "MainViewModel"

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    val cameraManager: CameraManager,
    val detectionPipeline: DetectionPipeline,
    val arOverlayManager: AROverlayManager,
    private val tfliteDetector: TFLiteDetector,
    private val settingsRepo: SettingsRepository,
    private val sessionRepo: SessionRepository,
    private val alertManager: AlertManager,
    private val locationEngine: GPSLocationEngine,
    private val wearSyncManager: WearSyncManager,
    private val hapticLanguage: HapticLanguage,
    private val performanceMonitor: PerformanceMonitor,
    private val errorHandler: AppErrorHandler,
    val hazardRepo: HazardRepository,
    private val gestureControlEngine: GestureControlEngine,
    private val sensorFusion: MultiModalSensorFusion,
    private val v2xEngine: V2XCommunicationEngine,
    private val smartCity: SmartCityIntegration,
    private val sceneDescriber: AISceneDescriber
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted.asStateFlow()

    private var currentSessionId: Long = -1

    val overallStats: StateFlow<OverallStats> = sessionRepo.allSessions.map { sessions ->
        OverallStats(
            totalSessions = sessions.size,
            avgSafetyScore = if (sessions.isEmpty()) 0 else sessions.map { it.averageSafetyScore }.average().toInt(),
            totalDistanceKm = sessions.sumOf { it.distanceTravelled.toDouble() }.toFloat(),
            totalAlerts = sessions.size * 2,
            tier3Total = sessions.count { it.averageSafetyScore < 50 },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OverallStats())

    val weeklyData: StateFlow<List<DayData>> = MutableStateFlow(
        listOf(
            DayData("Mon", 2, false), DayData("Tue", 4, false),
            DayData("Wed", 1, false), DayData("Thu", 5, false),
            DayData("Fri", 3, false), DayData("Sat", 0, false),
            DayData("Sun", 0, true)
        )
    ).asStateFlow()

    val settings: StateFlow<SettingsState> = settingsRepo.settings.map { appSettings ->
        SettingsState(
            audioEnabled = appSettings.alertVoiceEnabled,
            hapticFeedback = appSettings.alertVibrateEnabled,
            arOverlays = appSettings.arOverlaysEnabled,
            v2xEnabled = appSettings.v2xEnabled,
            cloudSync = appSettings.cloudSyncEnabled,
            batterySaver = appSettings.batteryOptimizationEnabled,
            forceCpu = appSettings.forceCpu,
            userType = appSettings.userType,
            detectionConfidence = appSettings.detectionConfidence
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsState())

    private var currentNearbyHazards: List<HazardPin> = emptyList()

    init {
        Log.e("DEBUG_VMODEL", "MainViewModel CREATED")
        viewModelScope.launch(Dispatchers.IO) {
            initPipeline()
        }
        viewModelScope.launch {
            settingsRepo.settings.collect { settings ->
                _uiState.update { it.copy(
                    onboardingComplete = settings.onboardingComplete,
                    confidenceThreshold = settings.detectionConfidence
                )}
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(2000L)
                _uiState.update { it.copy(
                    memoryUsageMb = performanceMonitor.getMemoryUsageMb().toLong()
                )}
            }
        }
        @OptIn(ExperimentalCoroutinesApi::class)
        viewModelScope.launch {
            _uiState.map { it.currentLocation }
                .distinctUntilChanged()
                .flatMapLatest { loc ->
                    if (loc != null) hazardRepo.observeNearbyPins(loc.latitude, loc.longitude, 500)
                    else flowOf(emptyList())
                }
                .collect { hazards ->
                    currentNearbyHazards = hazards
                    _uiState.update { it.copy(nearbyHazardsCount = hazards.size) }
                }
        }
        viewModelScope.launch {
            wearSyncManager.incomingEvents.collect { event ->
                when (event) {
                    WearEvent.TOGGLE_PAUSE -> togglePause()
                    WearEvent.REPORT_HAZARD -> reportHazard("MANUAL_HAZARD", 2)
                    WearEvent.START_NAVIGATION -> {  }
                    WearEvent.STOP_NAVIGATION -> {  }
                }
            }
        }
        viewModelScope.launch {
            errorHandler.currentError.collect { error ->
                if (error != null) {
                    reportError(error)
                }
            }
        }
    }

    private suspend fun initPipeline() {
        Log.e("DEBUG_INIT", "initPipeline: STARTING")
        _permissionsGranted.first { it }
        Log.e("DEBUG_INIT", "initPipeline: PERMISSIONS VERIFIED - Booting AI Engines...")

        if (_uiState.value.isAppReady) {
            Log.e("DEBUG_INIT", "initPipeline: ALREADY READY")
            return
        }
        
        try {
            Log.e("DEBUG_INIT", "initPipeline: Initializing TFLite...")
            val detectorOk = tfliteDetector.initialize()

            if (!detectorOk) {
                Log.e("DEBUG_INIT", "initPipeline: TFLite FAILED")
                _uiState.update { it.copy(
                    error = AppError.ModelLoadError,
                    isAppReady = false
                )}
                return
            }
            Log.e("DEBUG_INIT", "initPipeline: TFLite SUCCESS")
            sessionRepo.cleanupSessions()
            val loc = _uiState.value.currentLocation
            currentSessionId = sessionRepo.startSession(loc?.latitude, loc?.longitude)
            sensorFusion.setSessionId(currentSessionId)
            v2xEngine.start()
            smartCity.startListening()

            alertManager.initialize()
            Log.e("DEBUG_INIT", "initPipeline: AlertManager READY")

            _uiState.update { it.copy(isAppReady = true, error = null) }
            Log.e("DEBUG_INIT", "initPipeline: APP MARKED READY âœ…")

            startDetectionService()
            viewModelScope.launch(Dispatchers.Default) {
                val loggedHazards = mutableSetOf<Int>()

                detectionPipeline.trackedObjects.collect { results ->
                    val tracked = results.trackedObjects
                    tracked.forEach { obj ->
                        if (obj.riskTier == RiskTier.DANGER && !loggedHazards.contains(obj.id)) {
                            reportHazard("COLLISION_RISK_${obj.label.uppercase()}", 5)
                            loggedHazards.add(obj.id)
                        }
                    }
                    

                val cameraDets = tracked.map { 
                    DetectedObject(
                        id = it.id,
                        label = it.label,
                        confidence = it.confidence,
                        boundingBox = RectF(it.box.left, it.box.top, it.box.right, it.box.bottom),
                        ttc = it.ttcSeconds,
                        distanceMeters = it.depthMeters,
                        speedKmph = it.speedKmph
                    )
                }
                    
                    val fused = sensorFusion.fuse(
                        cameraDetections = cameraDets,
                        trackedObjects = tracked,
                        bsmMessages = v2xEngine.getLiveBSMs(),
                        location = _uiState.value.currentLocation,
                        nearbyHazards = currentNearbyHazards
                    )

                    _uiState.update { state ->
                        state.copy(
                            trackedObjects = tracked,
                            frameWidth = results.frameWidth,
                            frameHeight = results.frameHeight,
                            safetyScore = fused.safetyScore.toFloat(),
                            riskTier = tracked
                                .maxByOrNull { it.riskTier.priority }
                                ?.riskTier ?: RiskTier.SAFE
                        )
                    }
                    updateServiceStatus(
                        tier = _uiState.value.riskTier,
                        hazardsCount = _uiState.value.nearbyHazardsCount
                    )

                    if (settings.value.audioEnabled && !_uiState.value.isPaused) {
                        sceneDescriber.describeScene(tracked)
                    }
                }
            }
            viewModelScope.launch {
                detectionPipeline.pipelineStatsFlow.collect { stats ->
                    Log.e("DEBUG_VMODEL", "UI State Update Triggered: FPS=${stats.fps}")
                    _uiState.update { state ->
                        state.copy(
                            fps = stats.fps.toInt(),
                            pipelineStats = "FPS: ${"%.1f".format(stats.fps)} | Proc: ${stats.processingTimeMs}ms",
                            memoryUsageMb = performanceMonitor.getMemoryUsageMb().toLong()
                        )
                    }
                }
            }
            viewModelScope.launch {
                locationEngine.startTracking()
                locationEngine.currentLocation.asFlow().collect { loc ->
                    _uiState.update { it.copy(
                        currentLocation = loc,
                        userSpeedStatus = locationEngine.estimateUserSpeedKmph(loc.speed)
                    ) }
                    wearSyncManager.updateState(loc.latitude, loc.longitude, _uiState.value.trackedObjects.size, !_uiState.value.isPaused)
                }
            }


            viewModelScope.launch {
                settings.map { it.batterySaver }.distinctUntilChanged().collect { isEnabled ->
                    if (isEnabled) {
                        locationEngine.updateRequest(com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY, 5000L)
                    } else {
                        locationEngine.updateRequest(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 500L)
                    }
                }
            }

            Log.d(TAG, "âœ… Full pipeline initialized â€” detection ACTIVE")

        } catch (e: Exception) {
            Log.e(TAG, "âŒ Pipeline init error: ${e.message}", e)
            _uiState.update { it.copy(error = AppError.UnknownError(e.message ?: "Unknown init error")) }
        }
    }

    fun completeOnboarding() {
        Log.d(TAG, "âœ… Onboarding Complete")
        viewModelScope.launch {
            settingsRepo.setOnboardingComplete(true)
        }
    }

    fun onPermissionsResult(granted: Boolean) {
        _permissionsGranted.value = granted
        if (granted) {
            Log.d(TAG, "AI Engine connected to Permissions.")
            if (_uiState.value.isAppReady) startDetectionService()
        }
    }

    private fun startDetectionService() {
        val intent = Intent(getApplication(), DetectionForegroundService::class.java).apply {
            action = DetectionForegroundService.ACTION_START
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().startService(intent)
        }
    }

    private fun updateServiceStatus(tier: RiskTier, hazardsCount: Int) {
        val statusText = when (tier) {
            RiskTier.DANGER -> "âš ï¸ CRITICAL DANGER: Collision predicted!"
            RiskTier.CAUTION -> "ðŸŸ  Warning: Vehicle approaching"
            RiskTier.ADVISORY -> "ðŸŸ¡ Advisory: Object detected"
            RiskTier.SAFE -> if (hazardsCount > 0) "ðŸ›¡ï¸ Safe - $hazardsCount reports nearby" else "ðŸ›¡ï¸ Safe - Scanning active"
        }

        val intent = Intent(getApplication(), DetectionForegroundService::class.java).apply {
            action = DetectionForegroundService.ACTION_UPDATE
            putExtra(DetectionForegroundService.EXTRA_STATUS, statusText)
        }
        getApplication<Application>().startService(intent)
    }

    fun togglePause() {
        val newPausedState = !_uiState.value.isPaused
        _uiState.update { it.copy(isPaused = newPausedState) }
        if (newPausedState) detectionPipeline.reset()
    }

    fun setConfidence(value: Float) {
        viewModelScope.launch {
            settingsRepo.setDetectionConfidence(value)
        }
    }

    fun updateSetting(key: String, value: Boolean) {
        viewModelScope.launch {
            when (key) {
                "audio" -> settingsRepo.setAlertVoiceEnabled(value)
                "haptic" -> settingsRepo.setAlertVibrateEnabled(value)
                "ar" -> settingsRepo.setArOverlaysEnabled(value)
                "v2x" -> settingsRepo.setV2xEnabled(value)
                "cloud" -> settingsRepo.setCloudSyncEnabled(value)
                "battery" -> settingsRepo.setBatteryOptimizationEnabled(value)
                "force_cpu" -> settingsRepo.setForceCpu(value)
            }
        }
    }

    @Suppress("Unused")
    fun setUserType(type: String) {
        viewModelScope.launch {
            settingsRepo.setUserType(type)
        }
    }

    fun confirmHazard(pinId: Long) {
        viewModelScope.launch {
            hazardRepo.confirmPin(pinId)
            hapticLanguage.vibrate(HapticLanguage.VibrationPattern.CONFIRMATION)
        }
    }

    fun disputeHazard(pinId: Long) {
        viewModelScope.launch {
            hazardRepo.disputePin(pinId)
            hapticLanguage.vibrate(HapticLanguage.VibrationPattern.NOTIFICATION)
        }
    }

    fun reportHazard(type: String, severity: Int) {
        viewModelScope.launch {
            val loc = _uiState.value.currentLocation
            if (loc != null) {
                val pin = HazardPin(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    hazardType = type,
                    severity = severity,
                    timestamp = System.currentTimeMillis()
                )
                hazardRepo.insertHazard(pin)
                hapticLanguage.vibrate(HapticLanguage.VibrationPattern.CONFIRMATION)
            }
        }
    }
    @Suppress("Unused")
    fun fetchHazardsInArea(minLat: Double, maxLat: Double, minLng: Double, maxLng: Double) {
        viewModelScope.launch {
            val hazards = hazardRepo.getHazardsInArea(minLat, maxLat, minLng, maxLng)
            _uiState.update { it.copy(hazardsInView = hazards) }
        }
    }

    fun reportError(error: AppError) {
        _uiState.update { it.copy(error = error) }


        alertManager.vibrateSimple()
        val message = when(error) {
            AppError.CameraError -> "Camera error"
            AppError.ModelLoadError -> "AI model failure"
            AppError.LocationError -> "GPS signal lost"
            AppError.RootedDeviceError -> "Device security warning"
            is AppError.UnknownError -> error.message
        }
        alertManager.showVisualAlert(message, 3)
    }

    fun clearError() { 
        errorHandler.clearError()
        _uiState.update { it.copy(error = null) } 
    }

    fun onArFrame(session: Session, frame: Frame) {
        if (_uiState.value.isPaused) return
        arOverlayManager.updateOverlay(
            frame = frame,
            detections = _uiState.value.trackedObjects,
            session = session
        )
    }

    fun handleTouchEvent(event: android.view.MotionEvent): Boolean {
        return gestureControlEngine.handleTouchEvent(event)
    }

    override fun onCleared() {
        arOverlayManager.onSessionPaused()

        v2xEngine.stop()
        detectionPipeline.release()
        locationEngine.stopTracking()
        cameraManager.shutdown()
        alertManager.release()
        sceneDescriber.stop()

        getApplication<Application>().stopService(Intent(getApplication(), DetectionForegroundService::class.java))

        val loc = _uiState.value.currentLocation
        val safety = _uiState.value.safetyScore
        val totalDist = locationEngine.getTotalDistanceKm()
        
        CoroutineScope(Dispatchers.IO).launch {
            if (currentSessionId != -1L) {
                sessionRepo.endSession(
                    sessionId = currentSessionId,
                    endLat = loc?.latitude,
                    endLng = loc?.longitude,
                    dist = totalDist,
                    score = safety
                )
                sessionRepo.cleanupSessions()
            }
        }

        super.onCleared()
    }
}

data class MainUiState(
    val trackedObjects: List<TrackedObject> = emptyList(),
    val fps: Int = 0,
    val riskTier: RiskTier = RiskTier.SAFE,
    val safetyScore: Float = 100f,
    val isPaused: Boolean = false,
    val isAppReady: Boolean = false,
    val error: AppError? = null,
    val currentLocation: GPSLocationEngine.LocationData? = null,
    val onboardingComplete: Boolean = false,
    val userSpeedStatus: String = "Stationary",
    val nearbyHazardsCount: Int = 0,
    val pipelineStats: String = "",
    val memoryUsageMb: Long = 0L,
    val confidenceThreshold: Float = 0.5f,
    val frameWidth: Int = 480,
    val frameHeight: Int = 640,
    val hazardsInView: List<HazardPin> = emptyList()
)

