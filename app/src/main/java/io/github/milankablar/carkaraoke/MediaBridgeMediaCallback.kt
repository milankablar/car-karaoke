package io.github.milankablar.carkaraoke

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import android.view.KeyEvent
import io.github.milankablar.carkaraoke.diagnostics.DiagnosticLogger
import io.github.milankablar.carkaraoke.diagnostics.DiagnosticModule

class MediaBridgeMediaCallback internal constructor(
    private val context: Context,
    private val requestPlayback: (MediaController) -> Unit
) : MediaSessionCompat.Callback() {
    constructor(context: Context) : this(
        context,
        requestPlayback = { controller -> controller.transportControls.play() }
    )

    override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
        if (!MediaBridgeSessionManager.isControllerTrusted()) return false
        val keyEvent = mediaButtonEvent?.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)

        if (isSwapEnabled() && keyEvent?.action == KeyEvent.ACTION_DOWN) {
            when (keyEvent.keyCode) {
                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    Log.d("MediaBridge", "🔘 Intercepted KEYCODE_MEDIA_NEXT -> FastForward")
                    DiagnosticLogger.debug(context, DiagnosticModule.MEDIA, "Intercepted next as fast forward")
                    onFastForward()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    Log.d("MediaBridge", "🔘 Intercepted KEYCODE_MEDIA_PREVIOUS -> Rewind")
                    DiagnosticLogger.debug(context, DiagnosticModule.MEDIA, "Intercepted previous as rewind")
                    onRewind()
                    return true
                }
            }
        }
        return super.onMediaButtonEvent(mediaButtonEvent)
    }

    override fun onPlay() {
        if (!MediaBridgeSessionManager.isControllerTrusted()) return
        Log.d("MediaBridge", "▶️ onPlay triggered")
        DiagnosticLogger.info(context, DiagnosticModule.MEDIA, "Play requested")
        MediaControllerManager.getActiveController(context)?.transportControls?.play()
        sync()
    }

    override fun onPlayFromSearch(query: String?, extras: Bundle?) {
        Log.d("MediaBridge", "onPlayFromSearch: query=$query")
        // A simple implementation: just delegate to onPlay() to start playback of the current track
        // or whatever the default play action is. A real implementation would use the query.
        onPlay()
    }

    override fun onPause() {
        if (!MediaBridgeSessionManager.isControllerTrusted()) return
        Log.d("MediaBridge", "⏸️ onPause triggered")
        DiagnosticLogger.info(context, DiagnosticModule.MEDIA, "Pause requested")
        MediaControllerManager.getActiveController(context)?.transportControls?.pause()
        sync()
    }

    override fun onSkipToNext() {
        if (!MediaBridgeSessionManager.isControllerTrusted()) return
        Log.d("MediaBridge", "⏭️ onSkipToNext triggered")
        DiagnosticLogger.info(context, DiagnosticModule.MEDIA, "Skip next requested")
        if (isSwapEnabled()) {
            onFastForward()
        } else {
            MediaControllerManager.getActiveController(context)?.transportControls?.skipToNext()
            sync()
        }
    }

    override fun onSkipToPrevious() {
        if (!MediaBridgeSessionManager.isControllerTrusted()) return
        Log.d("MediaBridge", "⏮️ onSkipToPrevious triggered")
        DiagnosticLogger.info(context, DiagnosticModule.MEDIA, "Skip previous requested")
        if (isSwapEnabled()) {
            onRewind()
        } else {
            MediaControllerManager.getActiveController(context)?.transportControls?.skipToPrevious()
            sync()
        }
    }

    override fun onSeekTo(pos: Long) {
        if (!MediaBridgeSessionManager.isControllerTrusted()) return
        Log.d("MediaBridge", "🎯 onSeekTo triggered: $pos ms")
        DiagnosticLogger.info(context, DiagnosticModule.MEDIA, "Seek requested", mapOf("positionMs" to pos))
        MediaControllerManager.getActiveController(context)?.transportControls?.seekTo(pos)

        sync()
    }

    override fun onRewind() {
        if (!MediaBridgeSessionManager.isControllerTrusted()) return
        val controller = MediaControllerManager.getActiveController(context) ?: return
        val pos = controller.playbackState?.let { MediaInformationRetriever.getCurrentPositionMs(it) } ?: 0L
        val newPos = (pos - 10_000).coerceAtLeast(0L)
        Log.d("MediaBridge", "⏪ Rewind triggered: $newPos ms")
        DiagnosticLogger.info(context, DiagnosticModule.MEDIA, "Rewind requested", mapOf("positionMs" to newPos))
        controller.transportControls.seekTo(newPos)
        sync()
    }

    override fun onFastForward() {
        if (!MediaBridgeSessionManager.isControllerTrusted()) return
        val controller = MediaControllerManager.getActiveController(context) ?: return
        val pos = controller.playbackState?.let { MediaInformationRetriever.getCurrentPositionMs(it) } ?: 0L
        val duration = controller.metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val newPos = (pos + 10_000).let { if (duration > 0) it.coerceAtMost(duration) else it }
        Log.d("MediaBridge", "⏩ FastForward triggered: $newPos ms")
        DiagnosticLogger.info(context, DiagnosticModule.MEDIA, "Fast forward requested", mapOf("positionMs" to newPos))
        controller.transportControls.seekTo(newPos)
        sync()
    }

    override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
        if (!MediaBridgeSessionManager.isControllerTrusted()) return
        Log.d("MediaBridge", "onPlayFromMediaId: $mediaId")
        DiagnosticLogger.info(context, DiagnosticModule.MEDIA, "Play from media id requested", mapOf("mediaId" to mediaId))

        if (mediaId == null) return

        val controller = MediaControllerManager.getAllControllers(context)
            .firstOrNull { it.packageName == mediaId }

        if (controller != null) {
            MediaControllerManager.select(controller)
            val info = MediaInformationRetriever.buildMediaInfoFromController(context, controller)
            if (info != null) {
                MediaBridgeSessionManager.updateFromMediaInfo(info)
            }
            requestPlayback(controller)
            sync()
        }
    }

    override fun onCustomAction(action: String?, extras: Bundle?) {
        if (!MediaBridgeSessionManager.isControllerTrusted()) return
        Log.d("MediaBridge", "🎯 Custom action triggered: $action")
        
        when (action) {
            MediaStateUpdater.ACTION_REWIND_10S -> {
                onRewind()
            }
            MediaStateUpdater.ACTION_FAST_FORWARD_10S -> {
                onFastForward()
            }
            MediaStateUpdater.ACTION_SKIP_NEXT -> {
                MediaControllerManager.getActiveController(context)?.transportControls?.skipToNext()
                sync()
            }
            MediaStateUpdater.ACTION_SKIP_PREVIOUS -> {
                MediaControllerManager.getActiveController(context)?.transportControls?.skipToPrevious()
                sync()
            }
            else -> {
                Log.w("MediaBridge", "Unknown custom action: $action")
                DiagnosticLogger.warn(
                    context,
                    DiagnosticModule.MEDIA,
                    "Unknown custom media action",
                    mapOf("action" to action)
                )
            }
        }
    }

    private fun isSwapEnabled(): Boolean {
        val controller = MediaControllerManager.getActiveController(context) ?: return false
        return SettingsManager.isAppSwapRewindFastForward(context, controller.packageName)
    }

    private fun sync()
    {
        MediaBridgeSessionManager.requestRefresh("Transport control", 500L)
    }
}
