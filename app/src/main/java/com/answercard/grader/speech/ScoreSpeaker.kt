package com.answercard.grader.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class ScoreSpeaker(context: Context) {
    private var ready = false
    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }

    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        tts.setLanguage(Locale.CHINA)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "score-${System.currentTimeMillis()}")
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
