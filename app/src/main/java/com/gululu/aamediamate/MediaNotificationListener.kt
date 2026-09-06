package com.gululu.aamediamate

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.gululu.aamediamate.diagnostics.DiagnosticLogger
import com.gululu.aamediamate.diagnostics.DiagnosticModule

class MediaNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        MediaBridgeSessionManager.requestRefresh("Notification listener connected")
    }

    override fun onListenerDisconnected() {
        MediaBridgeSessionManager.updateFromMediaInfo(null)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.notification.category != Notification.CATEGORY_TRANSPORT) return

        val packageName = sbn.packageName
        Log.d("MediaBridge", "📥 Media Notification from $packageName")
        DiagnosticLogger.info(
            this,
            DiagnosticModule.MEDIA,
            "Media notification posted",
            mapOf("package" to packageName)
        )

        sync()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.notification.category != Notification.CATEGORY_TRANSPORT) return
        DiagnosticLogger.debug(
            this,
            DiagnosticModule.MEDIA,
            "Media notification removed",
            mapOf("package" to sbn.packageName)
        )
        sync()
    }

    private fun sync()
    {
        MediaBridgeSessionManager.requestRefresh("Media notification changed", 250L)
    }
}
