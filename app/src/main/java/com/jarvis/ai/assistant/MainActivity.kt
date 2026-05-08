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
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jarvis.ai.assistant.databinding.ActivityMainBinding
import com.jarvis.ai.assistant.services.JarvisOverlayService
import com.jarvis.ai.assistant.services.JarvisVoiceService
import com.jarvis.ai.assistant.utils.DeviceStatsManager
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var deviceManager: DeviceStatsManager
    private val statsHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val RC_AUDIO   = 101
        private const val RC_OVERLAY = 102
        private val RUNTIME_PERMISSIONS = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.ACCESS_FINE_LOCATION
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                add(Manifest.permission.ANSWER_PHONE_CALLS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // showBiometricPrompt() // Moved to user demand
        deviceManager = DeviceStatsManager(this)
        refreshStatus()
        setupButtons()
        setupSettings()
        startPulse()
        startStatsUpdateLoop()
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
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                        finish() // Close app only if user explicitly cancels
                    } else {
                        Toast.makeText(this@MainActivity, "Security Bypass: $errString", Toast.LENGTH_SHORT).show()
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
            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
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

    private fun safeGetColor(colorResId: Int): Int {
        return ContextCompat.getColor(this, colorResId)
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

        // Biometric Verify
        binding.btnVerifyIdentity.setOnClickListener {
            showBiometricPrompt()
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
            
            // Overlay Permission
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }

            // All other permissions (Accessibility)
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            
            // Notification Listener Permission
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (e: Exception) {}
            
            // Camera/Audio/SMS/etc permissions
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
        binding.tvSystemStatus.setTextColor(safeGetColor(if (accessOk && micOk && svcOk) R.color.accent_green else R.color.status_warning))
    }

    private fun startPulse() {
        try {
            binding.ivJarvisCore.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pulse))
        } catch (e: Exception) {}
    }

    private fun addLog(entry: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        lastFinalLogs = ("[$stamp] $entry\n$lastFinalLogs").take(700)
        binding.tvActivityLog.text = lastFinalLogs
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val hasMain = enabled.split(":").any { it.equals("$packageName/.services.JarvisAccessibilityService", ignoreCase = true) }
        val hasControl = enabled.split(":").any { it.equals("$packageName/.services.FullControlService", ignoreCase = true) }
        return hasMain || hasControl
    }

    private fun requestPermissionsIfNeeded() {
        val missing = RUNTIME_PERMISSIONS.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), RC_AUDIO)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_AUDIO) refreshStatus()
    }

    private fun startStatsUpdateLoop() {
        val runnable = object : Runnable {
            override fun run() {
                try {
                    updateDeviceStats()
                } catch (e: Exception) {
                    Log.e("JarvisUI", "Stats update failed", e)
                }
                statsHandler.postDelayed(this, 5000)
            }
        }
        statsHandler.postDelayed(runnable, 1000) // 1 second delay for first run
    }

    private fun updateDeviceStats() {
        if (!::binding.isInitialized) return
        
        try {
            val ram = deviceManager.getRamUsage()
            binding.tvRamVal.text = deviceManager.getFormattedRam()
            binding.pbRam.max = ram.second.toInt()
            binding.pbRam.progress = ram.first.toInt()

            val storage = deviceManager.getStorageUsage()
            binding.tvStorageVal.text = deviceManager.getFormattedStorage()
            binding.pbStorage.max = storage.second.toInt()
            binding.pbStorage.progress = storage.first.toInt()

            binding.tvBatteryVal.text = "${deviceManager.getBatteryLevel()}%"
            binding.tvCpuTemp.text = deviceManager.getCpuTemp()
        } catch (e: Exception) {
            Log.e("JarvisUI", "Error reading hardware stats", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        statsHandler.removeCallbacksAndMessages(null)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_OVERLAY) checkAndStartOverlay()
    }
}
