package com.pmgaurav.safestrideai.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.*
import java.security.spec.ECGenParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuantumKeyStoreManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val quantumCrypto: QuantumSafeCryptoEngine
) {

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun getClassicalV2XKey(): PrivateKey {
        if (!hasClassicalKey("V2X_CLASSICAL_KEY")) {
            generateClassicalV2XKey()
        }
        return keyStore.getKey("V2X_CLASSICAL_KEY", null) as PrivateKey
    }

    private fun generateClassicalV2XKey() {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore"
        )
        keyPairGenerator.initialize(
            KeyGenParameterSpec.Builder(
                "V2X_CLASSICAL_KEY",
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .build()
        )
        keyPairGenerator.generateKeyPair()
    }

    private fun storePQCPublicKey(alias: String, publicKey: PublicKey) {
        val prefs = context.getSharedPreferences("quantum_keys", Context.MODE_PRIVATE)
        prefs.edit { 
            putString("pub_$alias", Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP))
        }
    }

    fun rotateV2XKeys(): KeyPair {
        deleteClassicalKey("V2X_CLASSICAL_KEY")

        generateClassicalV2XKey()
        
        val newQuantumKeyPair = quantumCrypto.generateDilithiumKeyPair()
        storePQCPublicKey("V2X_DILITHIUM_PUB", newQuantumKeyPair.public)
        
        val newKyberKeyPair = quantumCrypto.generateKyberKeyPair()
        storePQCPublicKey("SECURE_CHANNEL_KYBER_PUB", newKyberKeyPair.public)
        
        return newQuantumKeyPair
    }

    fun establishSecureChannel(recipientPublicKey: PublicKey): ByteArray {
        val encapsulation = quantumCrypto.encapsulateSharedSecret(recipientPublicKey)
        return encapsulation.sharedSecret
    }

    fun receiveSecureChannel(ciphertext: ByteArray, privateKey: PrivateKey): ByteArray {
        return quantumCrypto.decapsulateSharedSecret(ciphertext, privateKey)
    }

    fun hasClassicalKey(alias: String): Boolean {
        return keyStore.containsAlias(alias)
    }

    fun deleteClassicalKey(alias: String) {
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }
}

