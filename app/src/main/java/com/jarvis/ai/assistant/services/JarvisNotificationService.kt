package com.jarvis.ai.assistant.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class JarvisNotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val packageName = sbn?.packageName
        val tickerText = sbn?.notification?.tickerText
        val extras = sbn?.notification?.extras
        val title = extras?.getString("android.title")
        val text = extras?.getCharSequence("android.text")
        
        Log.d("JarvisNotification", "Notification from $packageName: $title - $text")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
