package com.pmgaurav.safestrideai.utils

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PerformanceMonitor @Inject constructor(
    @param:ApplicationContext @Suppress("Unused") private val context: Context
) {
    fun getMemoryUsageMb(): Int {
        val runtime = Runtime.getRuntime()
        val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        return usedMem.toInt()
    }

    @Suppress("Unused")
    fun getCpuTemperature(): Float {
        return try {
            val file = java.io.File("/sys/class/thermal/thermal_zone0/temp")
            if (file.exists()) {
                val tempText = file.readText().trim()
                tempText.toFloat() / 1000f
            } else {
                35f
            }
        } catch (e: Exception) {
            Log.v("Performance", "Thermal data unavailable: ${e.message}")
            35f
        }
    }
}

