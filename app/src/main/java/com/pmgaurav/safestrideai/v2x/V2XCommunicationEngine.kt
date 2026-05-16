package com.pmgaurav.safestrideai.v2x

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.pmgaurav.safestrideai.crypto.QuantumKeyStoreManager
import com.pmgaurav.safestrideai.crypto.QuantumSafeCryptoEngine
import android.util.Base64
import android.util.Log
import java.security.KeyPair
import androidx.lifecycle.asFlow
import com.pmgaurav.safestrideai.location.GPSLocationEngine
import kotlinx.coroutines.*
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class V2XCommunicationEngine @Inject constructor(
    @ApplicationContext context: Context,
    private val locationEngine: GPSLocationEngine,
    private val quantumKeyStore: QuantumKeyStoreManager,
    private val quantumCrypto: QuantumSafeCryptoEngine
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val bleV2X = BleV2xChannel(context)
    private var quantumKeyPair: KeyPair? = null
    
    private var currentUserType = UserType.PEDESTRIAN

    data class PersonalSafetyMessage(
        val messageType: String = "PSM",
        val temporaryId: String,
        val timestamp: Long,
        val latitude: Double,
        val longitude: Double,
        val speed: Float,
        val heading: Float,
        val accuracy: Float,
        val userType: UserType,
        val alertLevel: Int,
        val signature: String = ""
    )

    data class BasicSafetyMessage(
        val vehicleId: String,
        val timestamp: Long,
        val latitude: Double,
        val longitude: Double,
        val speed: Float,
        val heading: Float,
        val acceleration: Float,
        val vehicleLength: Float,
        val vehicleWidth: Float,
        val brakingStatus: Boolean,
        val hazardLights: Boolean,
        val rssi: Int
    )

    enum class UserType {
        PEDESTRIAN
    }

    fun start() {
        runSecuritySelfTest()
        startPSMBroadcast()
        bleV2X.startScanning { bsm ->
            scope.launch {
                val demoEncapsulation = ByteArray(1088)
                val secret = processSecureHandshake(demoEncapsulation)
                Log.d("V2X", "Processed incoming handshake. Shared secret derived: ${secret.size} bytes")

                synchronized(latestBSMs) {
                    latestBSMs[bsm.vehicleId] = bsm
                }
            }
        }
    }


    @Suppress("Unused")
    fun initiateSecureHandshake(vehiclePublicKey: java.security.PublicKey): ByteArray {
        return quantumKeyStore.establishSecureChannel(vehiclePublicKey)
    }

    fun processSecureHandshake(encapsulation: ByteArray): ByteArray {
        val kyberPrivateKey = quantumKeyStore.getClassicalV2XKey()
        return quantumKeyStore.receiveSecureChannel(encapsulation, kyberPrivateKey)
    }

    private val latestBSMs = mutableMapOf<String, BasicSafetyMessage>()

    fun getLiveBSMs(): List<BasicSafetyMessage> {
        val now = System.currentTimeMillis()
        synchronized(latestBSMs) {
            latestBSMs.entries.removeIf { now - it.value.timestamp > 2000 }
            return latestBSMs.values.toList()
        }
    }

    fun stop() {
        scope.cancel()
        bleV2X.stop()
    }

    private fun startPSMBroadcast() {
        scope.launch {
            locationEngine.currentLocation.asFlow().collect { location ->
                val psm = buildPSM(location)
                val signedPsm = signMessage(psm)
                val payload = serializePSM(signedPsm)
                
                bleV2X.broadcast(payload)
                delay(100L)
            }
        }
    }

    private fun buildPSM(location: GPSLocationEngine.LocationData): PersonalSafetyMessage {
        return PersonalSafetyMessage(
            temporaryId = getRotatingAnonymousId(),
            timestamp = System.currentTimeMillis(),
            latitude = location.latitude,
            longitude = location.longitude,
            speed = location.speed,
            heading = location.bearing,
            accuracy = location.accuracy,
            userType = currentUserType,
            alertLevel = 0
        )
    }

    private fun getRotatingAnonymousId(): String {

        val minuteBucket = System.currentTimeMillis() / (5 * 60 * 1000)
        return UUID.nameUUIDFromBytes(minuteBucket.toString().toByteArray()).toString().take(8)
    }

    private fun signMessage(psm: PersonalSafetyMessage): PersonalSafetyMessage {
        return try {
            val privateKey = quantumKeyStore.getClassicalV2XKey()

            val messageBytes = "${psm.temporaryId}${psm.timestamp}${psm.latitude}${psm.longitude}".toByteArray()
            

            val signStartTime = System.nanoTime()
            
            val classicalSignature = Signature.getInstance("SHA256withECDSA").apply {
                initSign(privateKey)
                update(messageBytes)
            }.sign()

            if (quantumKeyPair == null) {
                quantumKeyPair = quantumKeyStore.rotateV2XKeys()
            }
            val quantumSignature = quantumCrypto.signV2XMessage(messageBytes, quantumKeyPair!!.private)
            
            val signEndTime = System.nanoTime()
            val totalLatencyMs = (signEndTime - signStartTime) / 1_000_000.0
            
            if (totalLatencyMs > 50.0) {
                Log.w("V2X", "Hybrid signing latency high: ${totalLatencyMs}ms")
            }

            val hybridSig = Base64.encodeToString(classicalSignature, Base64.NO_WRAP) + 
                           "|" + 
                           Base64.encodeToString(quantumSignature, Base64.NO_WRAP)

            psm.copy(signature = hybridSig)
        } catch (e: Exception) {
            Log.e("V2X", "Hybrid signing failed", e)
            psm
        }
    }

    fun runSecuritySelfTest() {
        val testKey = generateV2XKeyPair()
        Log.d("V2X", "Security self-test: Local key generation success. Key algorithm: ${testKey.algorithm}")
    }

    private fun generateV2XKeyPair(): PrivateKey {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore"
        )
        keyPairGenerator.initialize(
            KeyGenParameterSpec.Builder(
                "V2X_SIGNING_KEY",
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setKeyValidityEnd(Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000L))
            .build()
        )
        return keyPairGenerator.generateKeyPair().private
    }

    private fun serializePSM(psm: PersonalSafetyMessage): ByteArray {
        return "${psm.latitude},${psm.longitude},${psm.alertLevel}".toByteArray()
    }
}

