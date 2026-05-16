package com.pmgaurav.safestrideai.optimization

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.pmgaurav.safestrideai.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.tensorflow.lite.Delegate
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EdgeComputingOptimizer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepo: SettingsRepository
) {

    @Suppress("DEPRECATION")
    fun createOptimalDelegate(): List<Delegate> {
        val delegates = mutableListOf<Delegate>()


        val forceCpu = runBlocking { settingsRepo.forceCpu.first() }
        if (forceCpu) {
            Log.i("Edge", "ðŸ›¡ï¸ Stability Mode: Forcing CPU inference")
            return delegates
        }


        try {


            val gpuOptions = GpuDelegate.Options().apply {
                isPrecisionLossAllowed = true
                inferencePreference = GpuDelegate.Options.INFERENCE_PREFERENCE_FAST_SINGLE_ANSWER
            }
            delegates.add(GpuDelegate(gpuOptions))
            Log.d("Edge", "âœ… GPU Accelerator: ACTIVE")
            return delegates
        } catch (e: Exception) {
            Log.d("Edge", "GPU failed: ${e.message}")
        }


        try {
            val nnapiOptions = NnApiDelegate.Options().apply {
                executionPreference = NnApiDelegate.Options.EXECUTION_PREFERENCE_FAST_SINGLE_ANSWER
                allowFp16 = true
            }
            delegates.add(NnApiDelegate(nnapiOptions))
            Log.d("Edge", "âœ… NNAPI Accelerator: ACTIVE")
            return delegates
        } catch (e: Exception) {
            Log.d("Edge", "NNAPI failed: ${e.message}")
        }

        Log.w("Edge", "âš ï¸ CPU Fallback: ACTIVE")
        return delegates
    }

    fun monitorThermalState(onThrottleRequired: (Int) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.addThermalStatusListener(context.mainExecutor) { status ->
                val targetFps = when (status) {
                    PowerManager.THERMAL_STATUS_NONE, PowerManager.THERMAL_STATUS_LIGHT -> 30
                    PowerManager.THERMAL_STATUS_MODERATE -> 15
                    PowerManager.THERMAL_STATUS_SEVERE -> 10
                    PowerManager.THERMAL_STATUS_CRITICAL -> 5
                    else -> 0
                }
                onThrottleRequired(targetFps)
            }
        }
    }
}

