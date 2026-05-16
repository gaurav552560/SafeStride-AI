package com.pmgaurav.safestrideai.gesture

import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Build
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HapticLanguage @Inject constructor(private val vibrator: Vibrator) {


    enum class VibrationPattern(val pattern: LongArray, val amplitudes: IntArray) {

        ALERT_WARNING(longArrayOf(0, 200, 100, 200), intArrayOf(0, 128, 0, 128)),
        OBJECT_PERSON(longArrayOf(0, 50, 50, 50), intArrayOf(0, 255, 0, 255)),
        OBJECT_VEHICLE(longArrayOf(0, 150, 150, 150), intArrayOf(0, 255, 0, 255)),



        CONFIRMATION(longArrayOf(0, 50, 50, 50), intArrayOf(0, 100, 0, 255)),
        NOTIFICATION(longArrayOf(0, 100), intArrayOf(0, 128)),
        CRITICAL_DANGER(longArrayOf(0, 500, 100, 500), intArrayOf(0, 255, 0, 255))
    }

    fun vibrate(pattern: VibrationPattern) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern.pattern, pattern.amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern.pattern, -1)
        }
    }

    fun vibrateOneShot(duration: Long, amplitude: Int = -1) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amp = if (amplitude == -1) -1 else amplitude
            vibrator.vibrate(VibrationEffect.createOneShot(duration, amp))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    fun vibrateForObject(isPerson: Boolean) {
        if (isPerson) vibrate(VibrationPattern.OBJECT_PERSON)
        else vibrate(VibrationPattern.OBJECT_VEHICLE)
    }









    fun cancel() {
        vibrator.cancel()
    }
}

