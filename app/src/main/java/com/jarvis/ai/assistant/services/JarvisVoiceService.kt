package com.jarvis.ai.assistant.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.jarvis.ai.assistant.JarvisApplication
import com.jarvis.ai.assistant.MainActivity
import com.jarvis.ai.assistant.R
import com.jarvis.ai.assistant.utils.JarvisAIBrain
import com.jarvis.ai.assistant.services.FullControlService
import kotlin.math.sqrt

class JarvisVoiceService : Service(), RecognitionListener {

    // ─── Dependencies (no direct instantiation of AccessibilityService!) ─
    private val aiBrain = JarvisAIBrain()
    private val handler = Handler(Looper.getMainLooper())

    // ─── Speech Recognition ───────────────────────────────────────────
    private var speechRecognizer: SpeechRecognizer? = null
    private var isSpeechListening = false

    // ─── Voice Activity Detection (VAD) with AudioRecord ─────────────
    private var audioRecord: AudioRecord? = null
    private var isVadRunning = false

    private val SAMPLE_RATE      = 16_000
    private val VOICE_THRESHOLD  = 2_000f   // RMS to trigger "I heard something"
    private val MIN_VOICE_MS     = 500L     // must sustain for 500 ms before STT

    companion object {
        var instance: JarvisVoiceService? = null
        const val CHANNEL_ID      = "jarvis_voice_channel"
        const val NOTIFICATION_ID = 1001
        
        // Broadcast Actions for UI
        const val ACTION_LISTENING_START = "com.jarvis.ai.assistant.LISTENING_START"
        const val ACTION_LISTENING_STOP = "com.jarvis.ai.assistant.LISTENING_STOP"
        const val ACTION_TRANSCRIPTION = "com.jarvis.ai.assistant.TRANSCRIPTION"
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_IS_FINAL = "extra_is_final"
    }

    // ─── Lifecycle ────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(this)
        }

        aiBrain.initialize(null) // accessibility service injected on-demand via singleton

        JarvisApplication.instance.speak("JARVIS always-listening activated. Speak naturally, sir!")

        // Start VAD first; STT starts once voice is detected
        startVoiceActivityDetection()
        // Also kick off STT directly so it's ready
        handler.postDelayed({ startSpeechRecognition() }, 1500)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isVadRunning = false
        isSpeechListening = false
        audioRecord?.stop()
        audioRecord?.release()
        speechRecognizer?.destroy()
        instance = null
        super.onDestroy()
    }

    // ─── Voice Activity Detection ─────────────────────────────────────

    private fun startVoiceActivityDetection() {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (bufferSize == AudioRecord.ERROR_BAD_VALUE) return

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            audioRecord?.startRecording()
            isVadRunning = true
        } catch (e: SecurityException) {
            // RECORD_AUDIO not granted yet — STT alone will handle listening
            return
        }

        val buffer = ShortArray(bufferSize)
        var voiceStartMs: Long? = null

        val vadRunnable = object : Runnable {
            override fun run() {
                if (!isVadRunning) return

                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    val rms = calculateRMS(buffer, read)

                    if (rms > VOICE_THRESHOLD) {
                        if (voiceStartMs == null) {
                            voiceStartMs = System.currentTimeMillis()
                        }
                    } else {
                        voiceStartMs?.let { startMs ->
                            if (System.currentTimeMillis() - startMs >= MIN_VOICE_MS) {
                                // Sustained voice detected → ensure STT is active
                                if (!isSpeechListening) startSpeechRecognition()
                            }
                        }
                        voiceStartMs = null
                    }
                }
                handler.postDelayed(this, 60)
            }
        }
        handler.post(vadRunnable)
    }

    private fun calculateRMS(buffer: ShortArray, length: Int): Float {
        var sum = 0.0
        for (i in 0 until length) sum += buffer[i].toLong() * buffer[i].toLong()
        return sqrt((sum / length).toFloat())
    }

    // ─── Speech Recognition ───────────────────────────────────────────

    private fun startSpeechRecognition() {
        if (isSpeechListening) return
        isSpeechListening = true
        sendBroadcast(Intent(ACTION_LISTENING_START))
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            isSpeechListening = false
            sendBroadcast(Intent(ACTION_LISTENING_STOP))
            handler.postDelayed({ startSpeechRecognition() }, 1000)
        }
    }

    private fun restartSpeechRecognition(delayMs: Long = 600) {
        isSpeechListening = false
        sendBroadcast(Intent(ACTION_LISTENING_STOP))
        handler.postDelayed({ startSpeechRecognition() }, delayMs)
    }

    // ─── RecognitionListener ──────────────────────────────────────────

    override fun onReadyForSpeech(params: Bundle?) {}

    override fun onBeginningOfSpeech() {
        // Don't speak while user is speaking — would cause echo
    }

    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}

    override fun onPartialResults(partialResults: Bundle?) {
        // React to longer partials to feel instantaneous
        val partial = partialResults
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull() ?: return
            
        // Broadcast partial text
        val intent = Intent(ACTION_TRANSCRIPTION).apply {
            putExtra(EXTRA_TEXT, partial)
            putExtra(EXTRA_IS_FINAL, false)
        }
        sendBroadcast(intent)

        if (partial.length > 5) {
            // Quick wake-word check
            if (partial.lowercase().contains("jarvis") && partial.length < 12) {
                JarvisApplication.instance.speak("Yes sir?")
            }
        }
    }

    override fun onResults(results: Bundle?) {
        isSpeechListening = false
        sendBroadcast(Intent(ACTION_LISTENING_STOP))
        val command = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull() ?: run { restartSpeechRecognition(); return }

        // Broadcast final text
        val bIntent = Intent(ACTION_TRANSCRIPTION).apply {
            putExtra(EXTRA_TEXT, command)
            putExtra(EXTRA_IS_FINAL, true)
        }
        sendBroadcast(bIntent)

        // Process through AI Brain (accessibility service via singleton)
        val accessSvc = JarvisAccessibilityService.instance
        
        // Handle device control directly if it matches control keywords
        val controlKeywords = listOf("brightness", "volume", "type", "click", "zoom", "screenshot", "photo", "scroll", "swipe")
        val response = if (controlKeywords.any { command.lowercase().contains(it) }) {
            FullControlService.instance?.liveCommand(command.lowercase()) ?: aiBrain.processCommand(command.lowercase(), accessSvc, this)
        } else {
            aiBrain.processCommand(command.lowercase(), accessSvc, this)
        }
        
        JarvisApplication.instance.speak(response)

        // Proactive follow-up after a brief pause
        handler.postDelayed({
            JarvisApplication.instance.speak("Anything else, sir?",
                android.speech.tts.TextToSpeech.QUEUE_ADD)
        }, 3000)

        restartSpeechRecognition(1000)
    }

    override fun onError(error: Int) {
        isSpeechListening = false
        sendBroadcast(Intent(ACTION_LISTENING_STOP))
        val delay = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 300L
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1500L
            else -> 1000L
        }
        restartSpeechRecognition(delay)
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    // ─── Notification ─────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "JARVIS Always Listening",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "JARVIS is listening for your voice commands"
            setShowBadge(false)
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS Active")
            .setContentText("Always listening for your commands, sir...")
            .setSmallIcon(R.drawable.ic_jarvis_notification)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
