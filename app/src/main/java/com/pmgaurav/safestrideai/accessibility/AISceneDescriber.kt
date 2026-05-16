package com.pmgaurav.safestrideai.accessibility

import android.content.Context
import android.speech.tts.TextToSpeech
import com.pmgaurav.safestrideai.detection.TrackedObject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AISceneDescriber @Inject constructor(@param:ApplicationContext private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech = TextToSpeech(context, this)
    private var lastDescriptionTime = 0L
    private var isTtsReady = false

    override fun onInit(status: Int) {
        android.util.Log.d("AISceneDescriber", "TTS onInit status: $status")
        if (status == TextToSpeech.SUCCESS) {
            val locale = Locale.getDefault()
            val result = tts.setLanguage(locale)
            
            if ((result == TextToSpeech.LANG_MISSING_DATA) || (result == TextToSpeech.LANG_NOT_SUPPORTED)) {
                android.util.Log.w("AISceneDescriber", "Locale $locale not supported, falling back to US")
                tts.language = Locale.US
            }
            
            android.util.Log.d("AISceneDescriber", "TTS initialized with locale: ${tts.voice?.locale}")
            isTtsReady = true
        } else {
            android.util.Log.e("AISceneDescriber", "TTS initialization failed!")
        }
    }

    fun describeScene(
        detections: List<TrackedObject>,
    ): String {
        val now = System.currentTimeMillis()
        if ((now - lastDescriptionTime) < 5000L) {
            return ""
        }
        lastDescriptionTime = now

        val description = buildDescription(detections)
        if (description.isNotEmpty()) {
            speak(description)
        }
        return description
    }

    private fun buildDescription(
        detections: List<TrackedObject>,
    ): String {
        if (detections.isEmpty()) return "Area appears clear."

        val parts = mutableListOf<String>()

        val topDetections = detections.take(2)
        topDetections.forEach { det ->
            val position = getRelativePosition(det)
            parts.add("${det.label} $position")
        }

        return parts.joinToString(". ")
    }

    fun speak(text: String) {
        android.util.Log.i("AISceneDescriber", "ðŸ—£ï¸ SPEAKING: '$text' (isTtsReady: $isTtsReady)")
        if (isTtsReady && text.isNotEmpty()) {
            val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "scene_desc")
            android.util.Log.d("AISceneDescriber", "tts.speak result code: $result")
        }
    }

    private fun getRelativePosition(det: TrackedObject): String {
        val centerX = det.box.centerX()
        return when {
            centerX < 0.33f -> "on your left"
            centerX > 0.67f -> "on your right"
            else -> "ahead"
        }
    }

    fun stop() {
        tts.stop()
        tts.shutdown()
    }
}

