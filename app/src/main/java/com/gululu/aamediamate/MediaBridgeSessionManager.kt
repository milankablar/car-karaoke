package com.gululu.aamediamate

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
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
    private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }
    private var observedSourceController: MediaController? = null
    private var pendingSourceRefreshReason: String = "source controller callback"

    private val sourceControllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            scheduleSourceRefresh("Source metadata changed", SOURCE_CALLBACK_REFRESH_DELAY_MS)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            val info = currentMediaInfo ?: return
            if (state == null) {
                scheduleSourceRefresh("Source playback state cleared", SOURCE_CALLBACK_REFRESH_DELAY_MS)
                return
            }

            val isPlaying = state.state == PlaybackState.STATE_PLAYING
            if (isPlaying != info.isPlaying || hasExceededMediaDuration(info, state)) {
                scheduleSourceRefresh("Source playback state changed", SOURCE_CALLBACK_REFRESH_DELAY_MS)
            } else {
                scheduleEndOfMediaRefresh(info)
            }
        }

        override fun onSessionDestroyed() {
            scheduleSourceRefresh("Source session destroyed", 0L)
        }
    }

    private val sourceRefreshRunnable = Runnable {
        val ctx = context ?: return@Runnable
        val reason = pendingSourceRefreshReason
        DiagnosticLogger.debug(
            ctx,
            DiagnosticModule.MEDIA,
            "Refreshing media info from source",
            mapOf("reason" to reason)
        )
        updateFromMediaInfo(MediaInformationRetriever.refreshCurrentMediaInfo(ctx))
    }

    private val endOfMediaRefreshRunnable = Runnable {
        val ctx = context ?: return@Runnable
        val info = currentMediaInfo ?: return@Runnable
        val positionMs = getCurrentSourcePositionMs(ctx, info)
        DiagnosticLogger.debug(
            ctx,
            DiagnosticModule.MEDIA,
            "Refreshing media info after expected media end",
            mapOf(
                "package" to info.appPackageName,
                "title" to info.title,
                "positionMs" to positionMs,
                "durationMs" to info.duration
            )
        )
        updateFromMediaInfo(MediaInformationRetriever.refreshCurrentMediaInfo(ctx))
    }

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
        mainHandler.removeCallbacks(sourceRefreshRunnable)

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
            cancelPendingMediaRefreshes()
            stopObservingSourceController()
            mediaStateUpdater?.clear(session)
            lyricDisplayManager?.stop()
            mediaInfoListener?.invoke(null)
            currentMediaInfo = null // Ensure we don't hold onto disallowed info
            return
        }

        observeSourceController(info)

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
        scheduleEndOfMediaRefresh(info)
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

    private fun observeSourceController(info: MediaInfo) {
        val ctx = context ?: return
        val controller = MediaControllerManager.getActiveController(ctx)
        if (controller == null) {
            stopObservingSourceController()
            return
        }

        if (observedSourceController?.sessionToken == controller.sessionToken) return

        stopObservingSourceController()
        runCatching {
            controller.registerCallback(sourceControllerCallback, mainHandler)
        }.onSuccess {
            observedSourceController = controller
            DiagnosticLogger.debug(
                ctx,
                DiagnosticModule.MEDIA,
                "Source media controller callback registered",
                mapOf("package" to info.appPackageName)
            )
        }.onFailure { throwable ->
            DiagnosticLogger.warn(
                ctx,
                DiagnosticModule.MEDIA,
                "Failed to register source media controller callback",
                mapOf("package" to info.appPackageName),
                throwable
            )
        }
    }

    private fun stopObservingSourceController() {
        val controller = observedSourceController ?: return
        runCatching {
            controller.unregisterCallback(sourceControllerCallback)
        }.onFailure { throwable ->
            context?.let { ctx ->
                DiagnosticLogger.warn(
                    ctx,
                    DiagnosticModule.MEDIA,
                    "Failed to unregister source media controller callback",
                    throwable = throwable
                )
            }
        }
        observedSourceController = null
    }

    private fun scheduleSourceRefresh(reason: String, delayMs: Long) {
        pendingSourceRefreshReason = reason
        mainHandler.removeCallbacks(sourceRefreshRunnable)
        mainHandler.postDelayed(sourceRefreshRunnable, delayMs)
    }

    private fun scheduleEndOfMediaRefresh(info: MediaInfo) {
        mainHandler.removeCallbacks(endOfMediaRefreshRunnable)
        if (!info.isPlaying || info.duration <= 0L) return

        val ctx = context ?: return
        val positionMs = getCurrentSourcePositionMs(ctx, info)
        val delayMs = calculateEndOfMediaRefreshDelay(positionMs, info.duration)
        DiagnosticLogger.debug(
            ctx,
            DiagnosticModule.MEDIA,
            "Scheduled end-of-media refresh",
            mapOf(
                "package" to info.appPackageName,
                "title" to info.title,
                "positionMs" to positionMs,
                "durationMs" to info.duration,
                "delayMs" to delayMs
            )
        )
        mainHandler.postDelayed(endOfMediaRefreshRunnable, delayMs)
    }

    private fun cancelPendingMediaRefreshes() {
        mainHandler.removeCallbacks(sourceRefreshRunnable)
        mainHandler.removeCallbacks(endOfMediaRefreshRunnable)
    }

    private fun hasExceededMediaDuration(info: MediaInfo, state: PlaybackState): Boolean {
        if (!info.isPlaying || info.duration <= 0L) return false

        val positionMs = MediaInformationRetriever.getCurrentPositionMs(state)
        return positionMs > info.duration + END_OF_MEDIA_REFRESH_GRACE_MS
    }

    private fun getCurrentSourcePositionMs(ctx: Context, fallbackInfo: MediaInfo): Long {
        val state = MediaControllerManager.getActiveController(ctx)?.playbackState
        return state
            ?.let { MediaInformationRetriever.getCurrentPositionMs(it) }
            ?: fallbackInfo.position.coerceAtLeast(0L)
    }

    internal fun calculateEndOfMediaRefreshDelay(positionMs: Long, durationMs: Long): Long {
        if (durationMs <= 0L) return MIN_END_OF_MEDIA_REFRESH_DELAY_MS

        val remainingMs = durationMs - positionMs.coerceAtLeast(0L)
        return (remainingMs + END_OF_MEDIA_REFRESH_GRACE_MS)
            .coerceAtLeast(MIN_END_OF_MEDIA_REFRESH_DELAY_MS)
    }

    private const val SOURCE_CALLBACK_REFRESH_DELAY_MS = 250L
    private const val END_OF_MEDIA_REFRESH_GRACE_MS = 1_000L
    private const val MIN_END_OF_MEDIA_REFRESH_DELAY_MS = 1_000L
}
