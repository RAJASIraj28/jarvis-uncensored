package com.jarvis.ai.assistant

import android.Manifest
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jarvis.ai.assistant.databinding.ActivityMainBinding
import com.jarvis.ai.assistant.services.JarvisOverlayService
import com.jarvis.ai.assistant.services.JarvisVoiceService
import java.text.SimpleDateFormat
import java.util.*

/**
 * MainActivity — JARVIS setup & control panel.
 * Uses ViewBinding (replaces deprecated kotlinx.android.synthetic).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    companion object {
        private const val RC_AUDIO   = 101
        private const val RC_OVERLAY = 102
        private val RUNTIME_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.CAMERA
        )
    }

    // ─── Lifecycle ────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        refreshStatus()
        setupButtons()
        startPulse()
        requestPermissionsIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        checkAndStartOverlay()
        registerReceivers()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(voiceReceiver)
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(JarvisVoiceService.ACTION_LISTENING_START)
            addAction(JarvisVoiceService.ACTION_LISTENING_STOP)
            addAction(JarvisVoiceService.ACTION_TRANSCRIPTION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(voiceReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(voiceReceiver, filter)
        }
    }

    private val voiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                JarvisVoiceService.ACTION_LISTENING_START -> {
                    // Update UI to show listening
                    binding.tvSystemStatus.text = "LISTENING..."
                    binding.tvSystemStatus.setTextColor(getColor(R.color.accent_blue))
                    binding.ivJarvisCore.clearAnimation()
                    val fastPulse = AnimationUtils.loadAnimation(this@MainActivity, R.anim.pulse_fast)
                    binding.ivJarvisCore.startAnimation(fastPulse)
                }
                JarvisVoiceService.ACTION_LISTENING_STOP -> {
                    refreshStatus()
                    startPulse()
                }
                JarvisVoiceService.ACTION_TRANSCRIPTION -> {
                    val text = intent.getStringExtra(JarvisVoiceService.EXTRA_TEXT) ?: return
                    val isFinal = intent.getBooleanExtra(JarvisVoiceService.EXTRA_IS_FINAL, false)
                    if (isFinal) {
                        addLog("You: \"$text\"")
                    } else {
                        // Optionally update a live-transcription textView if we had one.
                        // We will just put it in the log with a special prefix for now.
                        binding.tvActivityLog.text = "> $text\n" + lastFinalLogs
                    }
                }
            }
        }
    }

    private var lastFinalLogs = "System initialized. Awaiting commands..."

    private fun checkAndStartOverlay() {
        if (Settings.canDrawOverlays(this)) {
            val intent = Intent(this, JarvisOverlayService::class.java)
            startService(intent)
        }
    }

    // ─── UI ───────────────────────────────────────────────────────────

    private fun setupButtons() {
        // Start always-listening
        binding.btnStartListening.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
                startForegroundService(Intent(this, JarvisVoiceService::class.java))
                JarvisApplication.instance.speak(
                    "JARVIS always-listening service started. I'm ready, sir."
                )
                addLog("Always-listening started.")
            } else {
                requestPermissionsIfNeeded()
            }
        }

        // Enable accessibility
        binding.btnEnableAccessibility.setOnClickListener {
            JarvisApplication.instance.speak(
                "Please enable JARVIS in Accessibility Settings to unlock full phone control, sir."
            )
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // Overlay permission
        binding.btnOverlayPermission.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivityForResult(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")),
                    RC_OVERLAY
                )
            } else {
                Toast.makeText(this, "Overlay already granted ✓", Toast.LENGTH_SHORT).show()
            }
        }

        // Test TTS
        binding.btnTestVoice.setOnClickListener {
            val time = SimpleDateFormat("hh:mm a", Locale.US).format(Date())
            JarvisApplication.instance.speak(
                "JARVIS online. All systems nominal. The time is $time. How may I assist you, sir?"
            )
            addLog("Voice test at $time.")
        }

        // FAB manual trigger
        binding.fabMic.setOnClickListener {
            JarvisApplication.instance.speak("JARVIS listening. Speak your command, sir.")
            addLog("Manual listen triggered.")
        }

        // Grant Advanced Permissions
        binding.btnGrantPermissions.setOnClickListener {
            // Brightness control permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.System.canWrite(this)) {
                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                }
            }
            
            // All other permissions (Accessibility)
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            
            // Camera/Audio permissions
            requestPermissionsIfNeeded()
        }
    }

    private fun refreshStatus() {
        binding.tvTime.text = SimpleDateFormat("HH:mm", Locale.US).format(Date())

        val accessOk = isAccessibilityEnabled()
        val micOk    = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                       PackageManager.PERMISSION_GRANTED
        val svcOk    = JarvisVoiceService.instance != null

        binding.statusAccessibility.setImageResource(
            if (accessOk) R.drawable.ic_check else R.drawable.ic_warning)
        binding.statusMic.setImageResource(
            if (micOk) R.drawable.ic_check else R.drawable.ic_warning)
        binding.statusService.setImageResource(
            if (svcOk) R.drawable.ic_check else R.drawable.ic_warning)

        binding.tvSystemStatus.text = when {
            accessOk && micOk && svcOk -> "ALL SYSTEMS OPERATIONAL"
            svcOk && micOk            -> "ACCESSIBILITY REQUIRED"
            else                       -> "SETUP REQUIRED"
        }
        binding.tvSystemStatus.setTextColor(
            getColor(if (accessOk && micOk && svcOk) R.color.accent_green else R.color.status_warning)
        )
    }

    private fun startPulse() {
        val anim = AnimationUtils.loadAnimation(this, R.anim.pulse)
        binding.ivJarvisCore.startAnimation(anim)
    }

    private fun addLog(entry: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        lastFinalLogs = ("[$stamp] $entry\n$lastFinalLogs").take(700)
        binding.tvActivityLog.text = lastFinalLogs
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private fun isAccessibilityEnabled(): Boolean {
        val name = "$packageName/.services.JarvisAccessibilityService"
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(":").any { it.equals(name, ignoreCase = true) }
    }

    private fun requestPermissionsIfNeeded() {
        val missing = RUNTIME_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), RC_AUDIO)
        }
    }

    // ─── Callbacks ────────────────────────────────────────────────────

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_AUDIO) {
            val granted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (granted) {
                JarvisApplication.instance.speak("Permissions granted. JARVIS ready, sir.")
                addLog("All permissions granted.")
            } else {
                Toast.makeText(this, "Some permissions denied — voice & call features limited",
                    Toast.LENGTH_LONG).show()
                addLog("Permissions partially denied.")
            }
            refreshStatus()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_OVERLAY) {
            val granted = Settings.canDrawOverlays(this)
            addLog("Overlay: ${if (granted) "GRANTED" else "DENIED"}.")
            if (granted) {
                JarvisApplication.instance.speak("Overlay permission granted, sir.")
                checkAndStartOverlay()
            }
        }
    }
}
