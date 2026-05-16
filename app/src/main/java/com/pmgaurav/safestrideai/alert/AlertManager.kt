package com.pmgaurav.safestrideai.alert 
 
import android.content.Context 
import android.media.AudioAttributes 
import android.media.AudioManager
import android.media.SoundPool 
import android.os.Bundle
import android.speech.tts.TextToSpeech
import com.pmgaurav.safestrideai.detection.TrackedObject
import com.pmgaurav.safestrideai.detection.RiskTier
import com.pmgaurav.safestrideai.gesture.HapticLanguage
import com.pmgaurav.safestrideai.wear.WearSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

data class AlertItem(
    val objectId: Int,
    val label: String,
    val tier: RiskTier,
    val ttc: Float,
    val distance: Float,
    val timestamp: Long = System.currentTimeMillis()
) : Comparable<AlertItem> {
    override fun compareTo(other: AlertItem): Int {
        return if (this.tier != other.tier) other.tier.priority - this.tier.priority
               else this.ttc.compareTo(other.ttc)
    }
}

class AlertManager(
    private val context: Context,
    private val wearSyncManager: WearSyncManager? = null,
    private val hapticLanguage: HapticLanguage? = null
) : TextToSpeech.OnInitListener {
 
    private var soundPool: SoundPool? = null 
    private var beepSoftId = -1
    private var beepMediumId = -1
    private var alarmDangerId = -1
    
    private val alertCooldowns = mutableMapOf<Int, Long>()
    private var lastTier3AlertTime = 0L
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    
    private val priorityQueue = PriorityQueue<AlertItem>()
 
    fun initialize() { 
        val attrs = AudioAttributes.Builder() 
            .setUsage(AudioAttributes.USAGE_ALARM) 
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION) 
            .build() 
        soundPool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attrs).build() 
        
        beepSoftId = loadSound("beep_soft")
        beepMediumId = loadSound("beep_medium")
        alarmDangerId = loadSound("alarm_danger")

        tts = TextToSpeech(context, this)
    }

    @android.annotation.SuppressLint("DiscouragedApi")
    private fun loadSound(name: String): Int {
        return try {
            val id = context.resources.getIdentifier(name, "raw", context.packageName)
            if (id != 0) soundPool?.load(context, id, 1) ?: -1 else -1
        } catch (_: Exception) {
            -1
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { ttsEngine ->
                val systemLocale = context.resources.configuration.locales[0]
                val result = ttsEngine.setLanguage(systemLocale)
                
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    ttsEngine.language = Locale.US
                }
                
                ttsEngine.setSpeechRate(1.2f)
                ttsEngine.setPitch(1.1f)
                isTtsReady = true
            }
        }
    }

    fun processTrackedObjects(tracked: List<TrackedObject>) {
        if (tracked.isEmpty()) {
            priorityQueue.clear()
            stopCriticalAlert()
            return
        }

        priorityQueue.clear()
        for (obj in tracked) {
            if (obj.riskTier != RiskTier.SAFE) {
                priorityQueue.add(AlertItem(
                    objectId = obj.id,
                    label = obj.label,
                    tier = obj.riskTier,
                    ttc = obj.ttcSeconds,
                    distance = obj.depthMeters
                ))
            }
        }

        val highestAlert = priorityQueue.poll()
        if (highestAlert != null) {
            fireAlert(highestAlert)
        } else {
            stopCriticalAlert()
        }
    }

    private fun fireAlert(alert: AlertItem) {
        val now = System.currentTimeMillis()
        
        if (alert.tier == RiskTier.DANGER) {
            if (now - lastTier3AlertTime < 800L) return
            lastTier3AlertTime = now
        } else {
            val lastAlertTime = alertCooldowns[alert.objectId] ?: 0L
            val cooldownMs = if (alert.tier == RiskTier.CAUTION) 1500L else 3000L
            if (now - lastAlertTime < cooldownMs) return
        }

        alertCooldowns[alert.objectId] = now

        vibrateForTier(alert.tier, alert.label)

        playAudioForTier(alert.tier)

        speakAlert(alert)

        wearSyncManager?.let {
            CoroutineScope(Dispatchers.IO).launch {
                it.sendAlert(
                    title = "HAZARD: ${alert.label}",
                    content = "Distance: ${"%.1f".format(alert.distance)}m",
                    priority = alert.tier.priority
                )
            }
        }
    }

    private fun vibrateForTier(tier: RiskTier, label: String = "") {
        val haptic = hapticLanguage ?: return
        when (tier) {
            RiskTier.CAUTION -> haptic.vibrate(HapticLanguage.VibrationPattern.ALERT_WARNING)
            RiskTier.DANGER -> haptic.vibrate(HapticLanguage.VibrationPattern.CRITICAL_DANGER)
            else -> {}
        }
        
        if (tier != RiskTier.SAFE && label.isNotEmpty()) {
            val isPerson = label.lowercase() == "person"
            haptic.vibrateForObject(isPerson)
        }
    }

    private fun playAudioForTier(tier: RiskTier) {
        val pool = soundPool ?: return
        requestAudioFocus()
        when (tier) {
            RiskTier.CAUTION -> pool.play(beepMediumId, 0.85f, 0.85f, 2, 0, 1.2f)
            RiskTier.DANGER -> pool.play(alarmDangerId, 1.0f, 1.0f, 3, 2, 1.5f)
            else -> {}
        }
    }

    fun speakAlert(alert: AlertItem) {
        if (!isTtsReady) return
        
        val hindiLabels = mapOf(
            "car" to "gaadi",
            "person" to "insaan",
            "bus" to "bus",
            "truck" to "truck",
            "bicycle" to "cycle",
            "motorbike" to "bike",
            "motorcycle" to "bike",
            "autorickshaw" to "rickshaw",
            "auto-rickshaw" to "rickshaw",
            "dog" to "kutta",
            "cow" to "gaaye",
        )

        val currentLocale = tts?.voice?.locale ?: Locale.getDefault()
        val isHindi = currentLocale.language == "hi"

        val label = if (isHindi) hindiLabels[alert.label.lowercase()] ?: alert.label else alert.label
        val displayText = if (isHindi) label else alert.label
        
        val message = when (alert.tier) {
            RiskTier.DANGER -> if (isHindi) "Khatra! $displayText bahut kareeb hai!" else "Danger! $displayText very close!"
            RiskTier.CAUTION -> if (isHindi) "Saavdhan! $displayText aa rahi hai." else "Warning! $displayText approaching."
            else -> ""
        }

        if (message.isEmpty()) return

        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
        }
        
        val queueMode = if (alert.tier == RiskTier.DANGER) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(message, queueMode, params, "alert_${alert.objectId}")
    }

    private fun requestAudioFocus() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener {}
                .build()
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }
    }

    fun release() { 
        soundPool?.release() 
        soundPool = null 
        tts?.stop()
        tts?.shutdown()
        hapticLanguage?.cancel()
    } 

    fun stopCriticalAlert() {
        hapticLanguage?.cancel()
        soundPool?.autoPause()
        tts?.stop()
    }

    fun vibrateSimple(duration: Long = com.pmgaurav.safestrideai.utils.AppConstants.VIBRATION_DURATION_MS) {
        hapticLanguage?.vibrateOneShot(duration)
    }

    fun showVisualAlert(message: String, tier: Int) {
        if (!isTtsReady) return
        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
        }
        val queueMode = if (tier == 3) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(message, queueMode, params, "visual_alert")
    }
}

