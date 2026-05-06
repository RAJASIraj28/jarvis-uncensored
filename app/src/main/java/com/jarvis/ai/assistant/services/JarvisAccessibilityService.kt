package com.jarvis.ai.assistant.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvis.ai.assistant.JarvisApplication
import com.jarvis.ai.assistant.utils.JarvisAIBrain

class JarvisAccessibilityService : AccessibilityService() {

    private val aiBrain = JarvisAIBrain()
    private var lastApp: String? = null

    companion object {
        /** Singleton reference — used by JarvisVoiceService & JarvisAIBrain */
        var instance: JarvisAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        aiBrain.initialize(this)
        JarvisApplication.instance.speak("JARVIS fully operational, sir!")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return

        // Auto-react to app changes (ignore own package + system UI)
        if (lastApp != packageName &&
            !packageName.contains("jarvis") &&
            !packageName.contains("systemui")) {
            reactToAppChange(packageName)
            lastApp = packageName
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> reactToWindow(event)
            AccessibilityEvent.TYPE_VIEW_CLICKED         -> reactToClick(event)
            AccessibilityEvent.TYPE_VIEW_FOCUSED         -> reactToFocus(event)
        }
    }

    // ─── Auto-reactive commentary ─────────────────────────────────────

    private fun reactToAppChange(packageName: String) {
        val response = when {
            packageName.contains("whatsapp", true)  -> "WhatsApp detected. Need to send a message, sir?"
            packageName.contains("youtube", true)   -> "YouTube opened. Want me to search something?"
            packageName.contains("camera", true)    -> "Camera ready. Smile or need a timer?"
            packageName.contains("chrome", true)    -> "Browsing? Need a quick search?"
            packageName.contains("maps", true)      -> "Maps ready. Where are we headed?"
            packageName.contains("spotify", true)   -> "Music app open. Shall I play something?"
            packageName.contains("phone", true) ||
            packageName.contains("dialer", true)    -> "Phone dialer open. Who should I call?"
            else -> ""
        }
        if (response.isNotEmpty()) {
            JarvisApplication.instance.speak(response, TextToSpeech.QUEUE_ADD)
        }
    }

    private fun reactToWindow(event: AccessibilityEvent) { /* reserved */ }

    private fun reactToClick(event: AccessibilityEvent) {
        val text = event.text?.joinToString(" ") ?: return
        if (text.contains("like", true) || text.contains("share", true)) {
            JarvisApplication.instance.speak("Need help sharing that?", TextToSpeech.QUEUE_ADD)
        }
    }

    private fun reactToFocus(event: AccessibilityEvent) {
        val text = event.text?.firstOrNull()?.toString() ?: return
        if (text.contains("search", true)) {
            JarvisApplication.instance.speak("Search field ready. What should I look for?",
                TextToSpeech.QUEUE_ADD)
        }
    }

    // ─── Public API used by JarvisAIBrain ────────────────────────────

    fun openApp(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        } else {
            JarvisApplication.instance.speak("Sorry sir, I couldn't find that app.")
        }
    }

    fun sendWhatsAppMessage(contact: String, message: String) {
        openApp("com.whatsapp")
        JarvisApplication.instance.speak("Opening WhatsApp to message $contact.")
    }

    fun makeCall(number: String) {
        val intent = Intent(Intent.ACTION_CALL, android.net.Uri.parse("tel:$number")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    fun goBack()        = performGlobalAction(GLOBAL_ACTION_BACK)
    fun goHome()        = performGlobalAction(GLOBAL_ACTION_HOME)
    fun showRecents()   = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun takeScreenshot()= performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)

    fun scrollDown() = dispatchSwipe(540f, 1200f, 540f, 400f)
    fun scrollUp()   = dispatchSwipe(540f, 400f, 540f, 1200f)

    private fun dispatchSwipe(x1: Float, y1: Float, x2: Float, y2: Float) {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun performClick(node: AccessibilityNodeInfo?) =
        node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)

    override fun onInterrupt() { instance = null }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
