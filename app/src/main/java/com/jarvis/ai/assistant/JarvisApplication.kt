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

        /** True once TextToSpeech engine is successfully initialised */
        var isTtsReady = false
            private set

        /**
         * True once the [tts] object has been created (even before the engine
         * reports SUCCESS).  Used to guard [stop]/[shutdown] in onTerminate().
         */
        var isTtsCreated = false
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
        // Mark as created right after construction
        isTtsCreated = true
    }

    /** Centralised speak — safe to call from any thread/class */
    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        // isTtsReady is only true AFTER the engine has confirmed SUCCESS
        // so it already implies that tts has been created and configured.
        if (isTtsReady) {
            tts.speak(text, queueMode, null, "jarvis_${System.currentTimeMillis()}")
        } else {
            android.util.Log.w("JarvisApplication", "speak() called before TTS ready: $text")
        }
    }

    override fun onTerminate() {
        // Use the creation flag — avoids accessing the backing field of a
        // companion-object lateinit var, which the Kotlin compiler forbids.
        if (isTtsCreated) {
            tts.stop()
            tts.shutdown()
        }
        super.onTerminate()
    }
}
