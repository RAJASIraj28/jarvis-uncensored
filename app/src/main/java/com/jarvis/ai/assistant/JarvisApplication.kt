package com.jarvis.ai.assistant

import android.app.Application
import android.speech.tts.TextToSpeech
import java.util.*

class JarvisApplication : Application() {

    companion object {
        lateinit var instance: JarvisApplication
            private set
        lateinit var tts: TextToSpeech
            private set
        var isTtsReady = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        initTTS()
    }

    private fun initTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts.setLanguage(Locale.US)
                if (result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    isTtsReady = true
                    tts.setSpeechRate(0.85f)
                    tts.setPitch(0.90f)
                }
            }
        }
    }

    /** Centralised speak — safe to call from any thread/class */
    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        if (isTtsReady) {
            tts.speak(text, queueMode, null, "jarvis_${System.currentTimeMillis()}")
        }
    }

    override fun onTerminate() {
        if (::tts.isInitialized) { tts.stop(); tts.shutdown() }
        super.onTerminate()
    }
}
