package com.jarvis.ai.assistant.services

import android.app.Service
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.ImageView
import com.jarvis.ai.assistant.JarvisApplication
import com.jarvis.ai.assistant.R

class JarvisOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var ivCore: ImageView

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this).inflate(R.layout.layout_overlay_hud, null)
        ivCore = overlayView.findViewById(R.id.iv_hud_core)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        setupDragAndClick()
        windowManager.addView(overlayView, params)
        startPulseAnimation()
        
        val filter = IntentFilter().apply {
            addAction(JarvisVoiceService.ACTION_LISTENING_START)
            addAction(JarvisVoiceService.ACTION_LISTENING_STOP)
            addAction(JarvisVoiceService.ACTION_VOLUME_LEVEL)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(listeningReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(listeningReceiver, filter)
        }
    }

    private val listeningReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                JarvisVoiceService.ACTION_LISTENING_START -> {
                    ivCore.clearAnimation()
                    val fastPulse = AnimationUtils.loadAnimation(this@JarvisOverlayService, R.anim.pulse_fast)
                    ivCore.startAnimation(fastPulse)
                }
                JarvisVoiceService.ACTION_LISTENING_STOP -> {
                    ivCore.scaleX = 1.0f
                    ivCore.scaleY = 1.0f
                    startPulseAnimation()
                }
                JarvisVoiceService.ACTION_VOLUME_LEVEL -> {
                    val volume = intent.getFloatExtra(JarvisVoiceService.EXTRA_VOLUME, 0f)
                    val scale = 1.0f + (volume / 8000f).coerceIn(0f, 0.5f)
                    overlayView.scaleX = scale
                    overlayView.scaleY = scale
                }
            }
        }
    }

    private fun setupDragAndClick() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = false
        var startTime = 0L

        overlayView.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    startTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isClick = false
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    windowManager.updateViewLayout(overlayView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val duration = System.currentTimeMillis() - startTime
                    if (isClick) {
                        if (duration > 800) {
                            handleOverlayLongClick()
                        } else {
                            handleOverlayClick()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun handleOverlayClick() {
        val voiceSvc = JarvisVoiceService.instance
        if (voiceSvc != null) {
            JarvisApplication.instance.speak("Ready sir.")
        } else {
            val intent = Intent(this, JarvisVoiceService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }
    }

    private fun handleOverlayLongClick() {
        JarvisApplication.instance.speak("Scanning screen for analysis, sir.")
        FullControlService.instance?.takeScreenshot()
        // The brain will pick it up on the next command or we could trigger a vision analysis here
    }

    private fun startPulseAnimation() {
        ivCore.clearAnimation()
        val anim = AnimationUtils.loadAnimation(this, R.anim.pulse)
        ivCore.startAnimation(anim)
    }

    private fun checkStealthMode() {
        val prefs = getSharedPreferences("JarvisPrefs", Context.MODE_PRIVATE)
        val isStealth = prefs.getBoolean("stealth_mode", false)
        if (isStealth) {
            ivCore.setImageResource(android.R.drawable.ic_menu_today)
            ivCore.alpha = 0.5f
            ivCore.scaleX = 0.8f
            ivCore.scaleY = 0.8f
        } else {
            ivCore.setImageResource(R.drawable.ic_arc_reactor)
            ivCore.alpha = 1.0f
            ivCore.scaleX = 1.0f
            ivCore.scaleY = 1.0f
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        checkStealthMode()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayView.isInitialized) windowManager.removeView(overlayView)
        unregisterReceiver(listeningReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
