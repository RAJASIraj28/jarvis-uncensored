package com.jarvis.ai.assistant.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import java.io.File
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.hardware.HardwareBuffer
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class FullControlService : AccessibilityService() {
    
    private val handler = Handler(Looper.getMainLooper())
    
    companion object {
        var instance: FullControlService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    // 🔥 FULL DEVICE CONTROL COMMANDS 🔥
    fun setBrightness(level: Int): String {
        return try {
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, level)
            "Brightness set to ${level}%"
        } catch (e: Exception) {
            "Needs WRITE_SETTINGS permission"
        }
    }
    
    fun setVolume(type: String, level: Int): String {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val streamType = when (type.lowercase()) {
            "media" -> AudioManager.STREAM_MUSIC
            "ring" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            else -> AudioManager.STREAM_MUSIC
        }
        val maxVolume = audioManager.getStreamMaxVolume(streamType)
        val targetVolume = (level * maxVolume) / 100
        audioManager.setStreamVolume(streamType, targetVolume, 0)
        return "$type volume: ${level}%"
    }
    
    fun typeText(text: String): String {
        val root = rootInActiveWindow ?: return "Could not find input field"
        val focusedNode = findFocusedNode(root)
        return if (focusedNode != null) {
            val bundle = android.os.Bundle()
            bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
            "Typed: $text"
        } else {
            "No focused input field found"
        }
    }

    private fun findFocusedNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFocusedNode(child)
            if (result != null) return result
        }
        return null
    }
    
    fun click(x: Float, y: Float): String {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        dispatchGesture(gesture, null, null)
        return "Clicked at ($x, $y)"
    }
    
    fun zoomIn(): String {
        // Accessibility services don't have a direct "zoom" API for the whole screen easily
        // but we can simulate a pinch or use GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN or something as placeholder
        // The user request said "Placeholder for zoom"
        performGlobalAction(GLOBAL_ACTION_BACK) 
        return "Zoom simulation (back) activated"
    }
    
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    fun takeScreenshot(): String {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            takeScreenshot(Display.DEFAULT_DISPLAY, executor, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    val bitmap = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                    if (bitmap != null) {
                        saveScreenshotToTemp(bitmap)
                    }
                }
                override fun onFailure(errorCode: Int) {
                    Log.e("JarvisControl", "Screenshot failed: $errorCode")
                }
            })
            return "Analyzing screen state, sir."
        } else {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            return "Captured via legacy buffer."
        }
    }

    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private fun saveScreenshotToTemp(bitmap: android.graphics.Bitmap) {
        try {
            val file = File(getExternalFilesDir(null), "jarvis_vision.jpg")
            val out = java.io.FileOutputStream(file)
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
            out.flush()
            out.close()
            Log.d("JarvisControl", "Vision frame saved to ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("JarvisControl", "Failed to save vision frame", e)
        }
    }
    
    fun takePhoto(): String {
        val cameraIntent = Intent("android.media.action.IMAGE_CAPTURE")
        cameraIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(cameraIntent)
        return "Camera opened - taking photo"
    }
    
    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float): String {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 500))
            .build()
        dispatchGesture(gesture, null, null)
        return "Swiped from ($startX,$startY) to ($endX,$endY)"
    }
    
    fun scrollDown(): String {
        swipe(500f, 1500f, 500f, 500f) 
        return "Scrolled down"
    }
    
    fun findAndClick(text: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow
        val nodes = root?.findAccessibilityNodeInfosByText(text)
        nodes?.firstOrNull()?.let { node ->
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return node
        }
        return null
    }
    
    fun liveCommand(command: String): String {
        val normalized = command.lowercase()
        return when {
            normalized.contains("brightness") -> {
                val level = extractNumber(normalized, 128)
                setBrightness(level.coerceIn(0, 255))
            }
            normalized.contains("volume") -> {
                val parts = normalized.split(" ")
                val type = parts.find { it in listOf("media", "ring", "alarm") } ?: "media"
                val level = extractNumber(normalized, 50)
                setVolume(type, level)
            }
            normalized.contains("type") -> {
                val text = normalized.substringAfter("type").trim().trim('"')
                typeText(text)
            }
            normalized.contains("click") -> {
                val coords = extractCoordinates(normalized)
                click(coords.first, coords.second)
            }
            normalized.contains("zoom") -> zoomIn()
            normalized.contains("screenshot") -> takeScreenshot()
            normalized.contains("photo") -> takePhoto()
            normalized.contains("scroll") -> scrollDown()
            normalized.contains("swipe") -> {
                val coords = extractSwipe(normalized)
                if (packageName != null && !packageName.contains("com.jarvis")) {
                    // Memory logging disabled for stability in this build
                }
                swipe(coords[0], coords[1], coords[2], coords[3])
            }
            else -> "Unknown live command"
        }
    }
    
    private fun extractNumber(command: String, default: Int): Int {
        return Regex("\\d+").find(command)?.value?.toIntOrNull() ?: default
    }
    
    private fun extractCoordinates(command: String): Pair<Float, Float> {
        val numbers = Regex("\\d+").findAll(command).map { it.value.toFloat() }.toList()
        return if (numbers.size >= 2) Pair(numbers[0], numbers[1]) else Pair(500f, 1000f)
    }
    
    private fun extractSwipe(command: String): List<Float> {
        val numbers = Regex("\\d+").findAll(command).map { it.value.toFloat() }.toList()
        return if (numbers.size >= 4) numbers.take(4) else listOf(500f, 1500f, 500f, 500f)
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
