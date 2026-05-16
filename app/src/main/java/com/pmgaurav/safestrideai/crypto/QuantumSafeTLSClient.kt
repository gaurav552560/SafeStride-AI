package com.pmgaurav.safestrideai.crypto

import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
import java.security.SecureRandom
import java.security.Security
import javax.net.ssl.*

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuantumSafeTLSClient @Inject constructor() {

    init {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
        if (Security.getProvider("BCJSSE") == null) {
            Security.insertProviderAt(BouncyCastleJsseProvider(BouncyCastleProvider()), 1)
        }
    }

    @Suppress("unused")
    fun buildQuantumSafeOkHttpClient(): OkHttpClient {

        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as java.security.KeyStore?)
        val trustManager = tmf.trustManagers.first { it is X509TrustManager } as X509TrustManager
        
        val sslContext = SSLContext.getInstance("TLSv1.3", "BCJSSE")
        
        val secureRandom = try {
            SecureRandom.getInstance("SHA1PRNG", "BC")
        } catch (_: Exception) {
            SecureRandom()
        }
        
        sslContext.init(null, arrayOf(trustManager), secureRandom)
        
        val pqCipherSuites = arrayOf(
            "TLS_AES_256_GCM_SHA384",
            "TLS_CHACHA20_POLY1305_SHA256"
        )
        
        val connectionSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_3)
            .cipherSuites(*pqCipherSuites)
            .build()
        
        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .connectionSpecs(listOf(connectionSpec, ConnectionSpec.CLEARTEXT))
            .build()
    }
}



