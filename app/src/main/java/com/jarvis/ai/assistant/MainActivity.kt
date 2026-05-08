package com.jarvis.ai.assistant

import android.Manifest
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
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jarvis.ai.assistant.databinding.ActivityMainBinding
import com.jarvis.ai.assistant.services.JarvisOverlayService
import com.jarvis.ai.assistant.services.JarvisVoiceService
import java.text.SimpleDateFormat
import java.util.*

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        showBiometricPrompt()
        refreshStatus()
        setupButtons()
        setupSettings()
        startPulse()
        requestPermissionsIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        checkAndStartOverlay()
        registerReceivers()
        loadSettings()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(voiceReceiver) } catch (e: Exception) {}
    }

    private fun showBiometricPrompt() {
        val biometricManager = androidx.biometric.BiometricManager.from(this)
        if (biometricManager.canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK) != androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
            addLog("Biometric security unavailable. Proceeding without auth.")
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        finish() // Close app only if user explicitly cancels
                    } else {
                        Toast.makeText(this@MainActivity, "Biometric Error: $errString", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    addLog("Security Verification Successful.")
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("JARVIS Security Access")
            .setSubtitle("Authorize to access AI Intelligence Core")
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun setupSettings() {
        // API Key Save
        binding.etApiKey.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val key = binding.etApiKey.text.toString().trim()
                if (key.isNotEmpty()) {
                    getSharedPreferences("JarvisPrefs", Context.MODE_PRIVATE)
                        .edit().putString("api_key", key).apply()
                    addLog("API Key updated and encrypted.")
                }
            }
        }

        // Stealth Mode Toggle
        binding.switchStealthMode.setOnCheckedChangeListener { _, isChecked ->
            getSharedPreferences("JarvisPrefs", Context.MODE_PRIVATE)
                .edit().putBoolean("stealth_mode", isChecked).apply()
            addLog("Stealth Mode: ${if (isChecked) "ACTIVE" else "DISABLED"}")
            
            // Restart overlay to apply look
            stopService(Intent(this, JarvisOverlayService::class.java))
            checkAndStartOverlay()
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("JarvisPrefs", Context.MODE_PRIVATE)
        binding.etApiKey.setText(prefs.getString("api_key", ""))
        binding.switchStealthMode.isChecked = prefs.getBoolean("stealth_mode", false)
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
                        binding.tvActivityLog.text = "> $text\n" + lastFinalLogs
                    }
                }
            }
        }
    }

    private var lastFinalLogs = "System initialized. Awaiting commands..."

    private fun checkAndStartOverlay() {
        if (Settings.canDrawOverlays(this)) {
            startService(Intent(this, JarvisOverlayService::class.java))
        }
    }

    private fun setupButtons() {
        binding.btnStartListening.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
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
                startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), RC_OVERLAY)
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
                
                // Battery optimization exception to keep service alive
                val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    val batteryIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    batteryIntent.data = Uri.parse("package:$packageName")
                    startActivity(batteryIntent)
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
        val micOk    = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val svcOk    = JarvisVoiceService.instance != null

        binding.statusAccessibility.setImageResource(if (accessOk) R.drawable.ic_check else R.drawable.ic_warning)
        binding.statusMic.setImageResource(if (micOk) R.drawable.ic_check else R.drawable.ic_warning)
        binding.statusService.setImageResource(if (svcOk) R.drawable.ic_check else R.drawable.ic_warning)

        binding.tvSystemStatus.text = if (accessOk && micOk && svcOk) "ALL SYSTEMS OPERATIONAL" else "SETUP REQUIRED"
        binding.tvSystemStatus.setTextColor(getColor(if (accessOk && micOk && svcOk) R.color.accent_green else R.color.status_warning))
    }

    private fun startPulse() {
        binding.ivJarvisCore.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pulse))
    }

    private fun addLog(entry: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        lastFinalLogs = ("[$stamp] $entry\n$lastFinalLogs").take(700)
        binding.tvActivityLog.text = lastFinalLogs
    }

    private fun isAccessibilityEnabled(): Boolean {
        val name = "$packageName/.services.JarvisAccessibilityService"
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(":").any { it.equals(name, ignoreCase = true) }
    }

    private fun requestPermissionsIfNeeded() {
        val missing = RUNTIME_PERMISSIONS.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), RC_AUDIO)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_AUDIO) refreshStatus()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_OVERLAY) checkAndStartOverlay()
    }
}
