package com.pmgaurav.safestrideai.utils

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit


object NetworkSecurityManager {


    private val PINS = listOf(
        "sha256/7HIp4ZRWfb9S8Xj1vQ99v/I72rXhVl637S8YgZ9Z9Z9=",
        "sha256/h6801m+z8v3zS5S8u8v8S8S8S8S8S8S8S8S8S8S8S8=",
    )

    private const val DOMAIN = "*.googleapis.com"

    
    fun createPinnedOkHttpClient(): OkHttpClient {
        val certificatePinner = CertificatePinner.Builder().apply {
            PINS.forEach { pin ->
                add(DOMAIN, pin)
            }
        }.build()

        return OkHttpClient.Builder()
            .certificatePinner(certificatePinner)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}

