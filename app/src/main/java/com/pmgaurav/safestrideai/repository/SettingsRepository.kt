package com.pmgaurav.safestrideai.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val onboardingComplete: Boolean,
    val alertVoiceEnabled: Boolean,
    val alertVibrateEnabled: Boolean,
    val detectionConfidence: Float,
    val batteryOptimizationEnabled: Boolean,
    val arOverlaysEnabled: Boolean,
    val v2xEnabled: Boolean,
    val cloudSyncEnabled: Boolean,
    val forceCpu: Boolean,
    val userType: String
)

@Singleton
class SettingsRepository @Inject constructor(@param:ApplicationContext private val context: Context) {

    companion object {
        val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val KEY_ALERT_VOICE = booleanPreferencesKey("alert_voice")
        val KEY_ALERT_VIBRATE = booleanPreferencesKey("alert_vibrate")
        val KEY_DETECTION_CONFIDENCE = floatPreferencesKey("detection_confidence")
        val KEY_BATTERY_OPTIMIZATION = booleanPreferencesKey("battery_optimization")
        val KEY_AR_OVERLAYS = booleanPreferencesKey("ar_overlays")
        val KEY_V2X_ENABLED = booleanPreferencesKey("v2x_enabled")
        val KEY_CLOUD_SYNC = booleanPreferencesKey("cloud_sync")
        val KEY_FORCE_CPU = booleanPreferencesKey("force_cpu")
        val KEY_USER_TYPE = androidx.datastore.preferences.core.stringPreferencesKey("user_type")
    }

    val forceCpu: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_FORCE_CPU] ?: false
    }

    suspend fun setForceCpu(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_FORCE_CPU] = enabled
        }
    }

    val onboardingComplete: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_ONBOARDING_COMPLETE] ?: false
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ONBOARDING_COMPLETE] = complete
        }
    }

    val alertVoiceEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_ALERT_VOICE] ?: true
    }

    val alertVibrateEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_ALERT_VIBRATE] ?: true
    }

    val detectionConfidence: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[KEY_DETECTION_CONFIDENCE] ?: 0.5f
    }

    val batteryOptimizationEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_BATTERY_OPTIMIZATION] ?: true
    }

    val arOverlaysEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AR_OVERLAYS] ?: false
    }

    val v2xEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_V2X_ENABLED] ?: true
    }

    val cloudSyncEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_CLOUD_SYNC] ?: false
    }

    val userType: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_USER_TYPE] ?: "PEDESTRIAN"
    }

    val settings: Flow<AppSettings> = combine<Any, AppSettings>(
        onboardingComplete,
        alertVoiceEnabled,
        alertVibrateEnabled,
        detectionConfidence,
        batteryOptimizationEnabled,
        arOverlaysEnabled,
        v2xEnabled,
        cloudSyncEnabled,
        forceCpu,
        userType,
    ) { args ->
        AppSettings(
            onboardingComplete = args[0] as Boolean,
            alertVoiceEnabled = args[1] as Boolean,
            alertVibrateEnabled = args[2] as Boolean,
            detectionConfidence = args[3] as Float,
            batteryOptimizationEnabled = args[4] as Boolean,
            arOverlaysEnabled = args[5] as Boolean,
            v2xEnabled = args[6] as Boolean,
            cloudSyncEnabled = args[7] as Boolean,
            forceCpu = args[8] as Boolean,
            userType = args[9] as String
        )
    }

    suspend fun setAlertVoiceEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ALERT_VOICE] = enabled
        }
    }

    suspend fun setAlertVibrateEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ALERT_VIBRATE] = enabled
        }
    }

    suspend fun setDetectionConfidence(confidence: Float) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DETECTION_CONFIDENCE] = confidence
        }
    }

    suspend fun setBatteryOptimizationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BATTERY_OPTIMIZATION] = enabled
        }
    }

    suspend fun setArOverlaysEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AR_OVERLAYS] = enabled
        }
    }

    suspend fun setV2xEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_V2X_ENABLED] = enabled
        }
    }

    suspend fun setCloudSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CLOUD_SYNC] = enabled
        }
    }

    suspend fun setUserType(type: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_USER_TYPE] = type
        }
    }
}

