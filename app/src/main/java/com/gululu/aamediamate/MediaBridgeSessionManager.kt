package com.gululu.aamediamate

import android.content.Context
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import com.gululu.aamediamate.diagnostics.DiagnosticLogger
import com.gululu.aamediamate.diagnostics.DiagnosticModule
import com.gululu.aamediamate.models.MediaInfo

object MediaBridgeSessionManager {
    private var mediaSession: MediaSessionCompat? = null
    private var mediaStateUpdater: MediaStateUpdater? = null
    private var lyricDisplayManager: LyricDisplayManager? = null
    private var currentMediaInfo: MediaInfo? = null
    private var mediaInfoListener: ((MediaInfo?) -> Unit)? = null
    private var context: Context? = null

    fun init(context: Context) {
        if (mediaSession != null) return

        val appContext = context.applicationContext
        this.context = appContext
        mediaStateUpdater = MediaStateUpdater(appContext)
        lyricDisplayManager = LyricDisplayManager(appContext)

        mediaSession = MediaSessionCompat(context, "MediaBridgeSession").apply {
            setCallback(MediaBridgeMediaCallback(context))
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            isActive = true
        }

        mediaStateUpdater?.clear(mediaSession!!)
        Log.d("MediaBridge", "✅ MediaSession initialized.")
        DiagnosticLogger.info(appContext, DiagnosticModule.MEDIA, "MediaSession initialized")
    }

    fun updateFromMediaInfo(info: MediaInfo?, forceLyricsResync: Boolean = false) {
        currentMediaInfo = info
        val session = mediaSession ?: return
        val ctx = context ?: return

        if (info == null || !Global.packageAllowed(ctx, info.appPackageName)) {
            if (info != null) {
                Log.d("MediaBridge", "🚫 Ignoring disallowed package: ${info.appPackageName}")
                DiagnosticLogger.info(
                    ctx,
                    DiagnosticModule.MEDIA,
                    "Ignoring disallowed media package",
                    mapOf("package" to info.appPackageName)
                )
            }
            mediaStateUpdater?.clear(session)
            lyricDisplayManager?.stop()
            mediaInfoListener?.invoke(null)
            currentMediaInfo = null // Ensure we don't hold onto disallowed info
            return
        }

        // Track this app as bridged
        SettingsManager.addOrUpdateBridgedApp(ctx, info.appPackageName, info.appName)
        DiagnosticLogger.info(
            ctx,
            DiagnosticModule.MEDIA,
            "Media session updated",
            mapOf(
                "package" to info.appPackageName,
                "app" to info.appName,
                "title" to info.title,
                "artist" to info.artist,
                "playing" to info.isPlaying
            )
        )

        // Restore original metadata before showing lyrics
        if (forceLyricsResync) {
            lyricDisplayManager?.stop()
        }
        mediaStateUpdater?.update(session, info)
        lyricDisplayManager?.start(session, info)

        mediaInfoListener?.invoke(info)
        MediaBridgeService.refreshBrowserData()
    }

    fun getSessionToken(): MediaSessionCompat.Token? = mediaSession?.sessionToken

    fun getCurrentMediaPackage(): String? = currentMediaInfo?.appPackageName

    /** Rebuilds the active bridged session after a display preference changes. */
    fun refreshCurrentSession(forceLyricsResync: Boolean = false) {
        val ctx = context ?: return
        val refreshedInfo = MediaInformationRetriever.refreshCurrentMediaInfo(ctx) ?: currentMediaInfo ?: return

        updateFromMediaInfo(refreshedInfo, forceLyricsResync)
    }

    fun setMediaInfoListener(listener: (MediaInfo?) -> Unit) {
        mediaInfoListener = listener
    }

    fun clearMediaInfoListener() {
        mediaInfoListener = null
    }

    fun getRewindActionId(): String = MediaStateUpdater.ACTION_REWIND_10S
    fun getFastForwardActionId(): String = MediaStateUpdater.ACTION_FAST_FORWARD_10S
}
