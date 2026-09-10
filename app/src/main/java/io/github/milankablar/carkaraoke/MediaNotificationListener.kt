package io.github.milankablar.carkaraoke

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import io.github.milankablar.carkaraoke.diagnostics.DiagnosticLogger
import io.github.milankablar.carkaraoke.diagnostics.DiagnosticModule

class MediaNotificationListener : NotificationListenerService() {
    private val sessionListener = android.media.session.MediaSessionManager.OnActiveSessionsChangedListener {
        MediaBridgeSessionManager.requestRefresh("Active sessions changed", 0)
    }
    private var manager: android.media.session.MediaSessionManager? = null
    override fun onListenerConnected() {
        super.onListenerConnected()
        manager = getSystemService(android.content.Context.MEDIA_SESSION_SERVICE) as android.media.session.MediaSessionManager
        runCatching { manager?.addOnActiveSessionsChangedListener(sessionListener, android.content.ComponentName(this, MediaNotificationListener::class.java), android.os.Handler(mainLooper)) }
        MediaBridgeSessionManager.requestRefresh("Notification listener connected")
    }

    override fun onListenerDisconnected() {
        manager?.removeOnActiveSessionsChangedListener(sessionListener)
        manager = null
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
