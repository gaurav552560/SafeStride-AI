package com.pmgaurav.safestrideai.utils

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

object AnalyticsUtils {
    private var firebaseAnalytics: FirebaseAnalytics? = null

    fun initialize(context: Context) {
        firebaseAnalytics = FirebaseAnalytics.getInstance(context)
    }

    fun logEvent(name: String, params: Bundle? = null) {
        firebaseAnalytics?.logEvent(name, params)
    }

    fun logDangerEvent(type: String, severity: String, ttc: Float) {
        val bundle = Bundle().apply {
            putString("hazard_type", type)
            putString("severity", severity)
            putFloat("ttc", ttc)
        }
        logEvent("hazard_detected", bundle)
    }

    fun logError(error: String, throwable: Throwable? = null) {
        FirebaseCrashlytics.getInstance().log(error)
        throwable?.let { FirebaseCrashlytics.getInstance().recordException(it) }
    }
}

