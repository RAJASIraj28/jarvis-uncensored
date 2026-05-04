package com.jarvis.ai.assistant.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import com.jarvis.ai.assistant.JarvisApplication
import com.jarvis.ai.assistant.services.JarvisVoiceService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // Restart the voice service after reboot
            val serviceIntent = Intent(context, JarvisVoiceService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
