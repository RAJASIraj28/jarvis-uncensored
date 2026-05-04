# Default ProGuard rules from the Android Gradle plugin
-keepattributes *Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep accessibility service
-keep class com.jarvis.ai.assistant.services.JarvisAccessibilityService { *; }
-keep class com.jarvis.ai.assistant.services.JarvisVoiceService { *; }
-keep class com.jarvis.ai.assistant.receivers.BootReceiver { *; }
