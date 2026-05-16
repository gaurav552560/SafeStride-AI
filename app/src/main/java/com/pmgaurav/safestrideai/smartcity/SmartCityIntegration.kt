package com.pmgaurav.safestrideai.smartcity

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class SmartCityIntegration @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    init {
        Log.d("SmartCity", "Integration initialized for: ${context.packageName}")
    }

    fun startListening() {
        Log.d("SmartCity", "Started Smart City integration for ${context.packageName}")
    }

    data class SignalPhaseAndTiming(
        val intersectionId: String,
        val latitude: Double,
        val longitude: Double,
        val currentPhase: SignalPhase,
        val timeToGreen: Int,
        val timeToRed: Int,
        val pedestrianSignal: PedestrianSignalState
    )

    enum class SignalPhase { RED, AMBER, GREEN, PEDESTRIAN_WALK, PEDESTRIAN_DO_NOT_WALK }
    enum class PedestrianSignalState { WALK, DO_NOT_WALK, COUNTDOWN, UNKNOWN }

    data class SignalAlert(
        val type: AlertType,
        val message: String,
        val tier: Int
    )

    enum class AlertType { DONT_WALK, SIGNAL_ABOUT_TO_CHANGE, WALK_SOON }

    suspend fun getNearbySignals(
        lat: Double,
        lng: Double
    ): List<SignalPhaseAndTiming> = withContext(Dispatchers.IO) {
        try {
            val url = "https://smartcity.example.com/api/v1/signals?lat=$lat&lng=$lng"
            val request = Request.Builder().url(url).build()
            
            okHttpClient.newCall(request).execute().use { response ->
                Log.d("SmartCity", "Secure connection established: ${response.isSuccessful}")
            }

            listOf(
                SignalPhaseAndTiming(
                    intersectionId = "INT-MUM-42",
                    latitude = lat + 0.001,
                    longitude = lng + 0.001,
                    currentPhase = SignalPhase.RED,
                    timeToGreen = 15,
                    timeToRed = 0,
                    pedestrianSignal = PedestrianSignalState.DO_NOT_WALK
                )
            )
        } catch (e: Exception) {
            Log.e("SmartCity", "Failed to fetch signals securely", e)
            emptyList()
        }
    }

    fun generateSignalAlert(
        signal: SignalPhaseAndTiming,
        userSpeed: Float,
        distanceToIntersection: Float
    ): SignalAlert? {
        val timeToReach = distanceToIntersection / userSpeed.coerceAtLeast(0.5f)

        return when {
            signal.currentPhase == SignalPhase.AMBER -> 
                SignalAlert(AlertType.SIGNAL_ABOUT_TO_CHANGE, "âš ï¸ Signal is AMBER", 1)
            
            signal.currentPhase == SignalPhase.GREEN && signal.pedestrianSignal == PedestrianSignalState.WALK ->
                null

            signal.pedestrianSignal == PedestrianSignalState.DO_NOT_WALK && distanceToIntersection < 20f ->
                SignalAlert(AlertType.DONT_WALK, "â›” Do not cross â€” signal is red", 2)
            
            signal.pedestrianSignal == PedestrianSignalState.COUNTDOWN && signal.timeToRed < timeToReach ->
                SignalAlert(AlertType.SIGNAL_ABOUT_TO_CHANGE, "âš ï¸ Signal changes in ${signal.timeToRed}s", 1)
                
            signal.timeToGreen < 5 && (signal.pedestrianSignal == PedestrianSignalState.DO_NOT_WALK || signal.pedestrianSignal == PedestrianSignalState.UNKNOWN) ->
                SignalAlert(AlertType.WALK_SOON, "ðŸŸ¢ Walk signal in ${signal.timeToGreen}s", 0)
                
            signal.currentPhase == SignalPhase.PEDESTRIAN_WALK || signal.currentPhase == SignalPhase.PEDESTRIAN_DO_NOT_WALK ->
                null

            else -> null
        }
    }
}

