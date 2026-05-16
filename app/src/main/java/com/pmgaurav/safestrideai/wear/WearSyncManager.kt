package com.pmgaurav.safestrideai.wear

import android.content.Context
import android.util.Log
import com.pmgaurav.safestrideai.crypto.QuantumSafeCryptoEngine
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

enum class WearEvent {
    START_NAVIGATION, STOP_NAVIGATION, TOGGLE_PAUSE, REPORT_HAZARD
}

@Singleton
class WearSyncManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val quantumCrypto: QuantumSafeCryptoEngine,
) {
    private val dataClient: DataClient = Wearable.getDataClient(context)

    private val _incomingEvents = MutableSharedFlow<WearEvent>(extraBufferCapacity = 1)
    val incomingEvents = _incomingEvents.asSharedFlow()

    fun onMessageReceived(path: String) {
        val event = when (path) {
            "/start_navigation" -> WearEvent.START_NAVIGATION
            "/stop_navigation" -> WearEvent.STOP_NAVIGATION
            "/toggle_pause" -> WearEvent.TOGGLE_PAUSE
            "/report_hazard" -> WearEvent.REPORT_HAZARD
            else -> null
        }
        event?.let { _incomingEvents.tryEmit(it) }
    }

    suspend fun sendAlert(title: String, content: String, priority: Int) {
        try {
            val putDataMapReq = PutDataMapRequest.create(WearDataTypes.PATH_ALERT).apply {
                dataMap.putString(WearDataTypes.KEY_ALERT_TITLE, title)
                dataMap.putString(WearDataTypes.KEY_ALERT_CONTENT, content)
                dataMap.putInt(WearDataTypes.KEY_ALERT_PRIORITY, priority)
                
                val signature = quantumCrypto.signV2XMessage(
                    "$title$content$priority".toByteArray(),
                    java.security.KeyPairGenerator.getInstance("Dilithium", "BCPQC").generateKeyPair().private
                )
                dataMap.putByteArray("quantum_signature", signature)
            }
            val putDataReq = putDataMapReq.asPutDataRequest()
            putDataReq.setUrgent()
            dataClient.putDataItem(putDataReq).await()
        } catch (e: Exception) {
            Log.e("WearSyncManager", "Error sending alert to wear", e)
        }
    }

    suspend fun updateState(latitude: Double, longitude: Double, objectCount: Int, isActive: Boolean) {
        try {
            val putDataMapReq = PutDataMapRequest.create(WearDataTypes.PATH_STATE).apply {
                dataMap.putDouble(WearDataTypes.KEY_STATE_LATITUDE, latitude)
                dataMap.putDouble(WearDataTypes.KEY_STATE_LONGITUDE, longitude)
                dataMap.putInt(WearDataTypes.KEY_STATE_OBJECT_COUNT, objectCount)
                dataMap.putBoolean(WearDataTypes.KEY_STATE_IS_ACTIVE, isActive)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }
            val putDataReq = putDataMapReq.asPutDataRequest()
            putDataReq.setUrgent()
            dataClient.putDataItem(putDataReq).await()
        } catch (e: Exception) {
            Log.e("WearSyncManager", "Error updating wear state", e)
        }
    }

    @Suppress("Unused")
    suspend fun sendNavigationInstruction(instruction: String, distance: Float) {
        try {
            val putDataMapReq = PutDataMapRequest.create(WearDataTypes.PATH_NAVIGATION).apply {
                dataMap.putString(WearDataTypes.KEY_NAV_INSTRUCTION, instruction)
                dataMap.putFloat(WearDataTypes.KEY_NAV_DISTANCE, distance)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }
            val putDataReq = putDataMapReq.asPutDataRequest()
            putDataReq.setUrgent()
            dataClient.putDataItem(putDataReq).await()
        } catch (e: Exception) {
            Log.e("WearSyncManager", "Error sending navigation to wear", e)
        }
    }
}

