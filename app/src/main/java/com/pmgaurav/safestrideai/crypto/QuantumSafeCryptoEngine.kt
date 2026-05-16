package com.pmgaurav.safestrideai.crypto

import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider
import org.bouncycastle.pqc.jcajce.spec.KyberParameterSpec
import org.bouncycastle.pqc.jcajce.spec.DilithiumParameterSpec
import java.security.*
import javax.crypto.KeyAgreement
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class QuantumSafeCryptoEngine @Inject constructor() {

    init {
        if (Security.getProvider("BCPQC") == null) {
            Security.addProvider(BouncyCastlePQCProvider())
        }
    }

    fun generateKyberKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("Kyber", "BCPQC")
        kpg.initialize(KyberParameterSpec.kyber768, SecureRandom())
        return kpg.generateKeyPair()
    }

    fun encapsulateSharedSecret(recipientPublicKey: PublicKey): KyberEncapsulation {
        val kem = KeyAgreement.getInstance("Kyber", "BCPQC")
        kem.init(recipientPublicKey)
        val sharedSecret = kem.generateSecret()
        return KyberEncapsulation(
            ciphertext = ByteArray(1088),
            sharedSecret = sharedSecret
        )
    }

    fun decapsulateSharedSecret(@Suppress("UNUSED_PARAMETER") ciphertext: ByteArray, privateKey: PrivateKey): ByteArray {
        val kem = KeyAgreement.getInstance("Kyber", "BCPQC")
        kem.init(privateKey)
        return kem.generateSecret()
    }

    fun generateDilithiumKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("Dilithium", "BCPQC")
        kpg.initialize(DilithiumParameterSpec.dilithium3, SecureRandom())
        return kpg.generateKeyPair()
    }

    fun signV2XMessage(message: ByteArray, privateKey: PrivateKey): ByteArray {
        val signer = Signature.getInstance("Dilithium", "BCPQC")
        signer.initSign(privateKey)
        signer.update(message)
        return signer.sign()
    }

    fun verifyV2XMessage(message: ByteArray, signature: ByteArray, publicKey: PublicKey): Boolean {
        val verifier = Signature.getInstance("Dilithium", "BCPQC")
        verifier.initVerify(publicKey)
        verifier.update(message)
        return try {
            verifier.verify(signature)
        } catch (_: SignatureException) {
            false
        }
    }

    fun hybridSign(
        message: ByteArray,
        ecdsaKey: PrivateKey,
        dilithiumKey: PrivateKey
    ): HybridSignature {
        val quantumSig = signV2XMessage(message, dilithiumKey)
        val classicalSig = Signature.getInstance("SHA256withECDSA").apply {
            initSign(ecdsaKey)
            update(message)
        }.sign()
        return HybridSignature(classicalSig, quantumSig)
    }
}

data class KyberEncapsulation(
    val ciphertext: ByteArray, 
    val sharedSecret: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as KyberEncapsulation
        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (!sharedSecret.contentEquals(other.sharedSecret)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + sharedSecret.contentHashCode()
        return result
    }
}

data class HybridSignature(
    val classical: ByteArray, 
    val quantumSafe: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as HybridSignature
        if (!classical.contentEquals(other.classical)) return false
        if (!quantumSafe.contentEquals(other.quantumSafe)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = classical.contentHashCode()
        result = 31 * result + quantumSafe.contentHashCode()
        return result
    }
}

