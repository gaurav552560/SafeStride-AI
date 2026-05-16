package com.pmgaurav.safestrideai

import android.app.Application
import com.pmgaurav.safestrideai.detection.LabelMapDiagnostic
import com.pmgaurav.safestrideai.utils.*
import dagger.hilt.android.HiltAndroidApp
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
import java.security.Security
import javax.inject.Inject

import com.pmgaurav.safestrideai.detection.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class SafeStrideApp : Application() {

    @Inject
    lateinit var errorHandler: AppErrorHandler

    override fun onCreate() {
        super.onCreate()
        
        try {
            System.loadLibrary("opencv_java4")
            android.util.Log.d("SafeStrideApp", "OpenCV loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.e("SafeStrideApp", "Failed to load OpenCV: ${e.message}")
        }

        AnalyticsUtils.initialize(this)


        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
        if (Security.getProvider("BCJSSE") == null) {
            Security.insertProviderAt(BouncyCastleJsseProvider(BouncyCastleProvider()), 1)
        }


        if (SecurityUtils.isDeviceRooted()) {
            errorHandler.setError(AppError.RootedDeviceError)
            AnalyticsUtils.logEvent("device_rooted")
            AnalyticsUtils.logError("CRITICAL: Device is rooted. Security integrity cannot be guaranteed.")
        }

        if (BuildConfig.DEBUG) {
            CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
                LabelMapDiagnostic(this@SafeStrideApp).run()
                ModelDiagnostic(this@SafeStrideApp).run()
            }
        }
    }
}

