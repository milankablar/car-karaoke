package com.gululu.aamediamate

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import com.gululu.aamediamate.diagnostics.DiagnosticLogger
import com.gululu.aamediamate.diagnostics.DiagnosticModule
import com.gululu.aamediamate.models.MediaInfo
import kotlin.math.abs

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
    private var lastSourceMetadataChangeElapsedRealtimeMs: Long = 0L
    private var stalePositionOverrideMediaKey: String? = null
    private var stalePositionOverrideStartedAtMs: Long = 0L
    private var stalePositionRecheckCount: Int = 0

    private val sourceControllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            lastSourceMetadataChangeElapsedRealtimeMs = SystemClock.elapsedRealtime()
            scheduleSourceRefresh("Source metadata changed", SOURCE_CALLBACK_REFRESH_DELAY_MS)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            val info = currentMediaInfo ?: return
            if (state == null) {
                scheduleSourceRefresh("Source playback state cleared", SOURCE_CALLBACK_REFRESH_DELAY_MS)
                return
            }

            val isPlaying = state.state == PlaybackState.STATE_PLAYING
            if (
                isPlaying != info.isPlaying ||
                hasExceededMediaDuration(info, state) ||
                shouldRefreshPendingStalePosition(info, state)
            ) {
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
        val previousInfo = currentMediaInfo
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
            clearStalePositionOverride()
            mediaStateUpdater?.clear(session)
            lyricDisplayManager?.stop()
            mediaInfoListener?.invoke(null)
            currentMediaInfo = null // Ensure we don't hold onto disallowed info
            return
        }

        val normalizedInfo = normalizeMediaInfoPosition(previousInfo, info)
        currentMediaInfo = normalizedInfo
        observeSourceController(normalizedInfo)

        // Track this app as bridged
        SettingsManager.addOrUpdateBridgedApp(ctx, normalizedInfo.appPackageName, normalizedInfo.appName)
        DiagnosticLogger.info(
            ctx,
            DiagnosticModule.MEDIA,
            "Media session updated",
            mapOf(
                "package" to normalizedInfo.appPackageName,
                "app" to normalizedInfo.appName,
                "title" to normalizedInfo.title,
                "artist" to normalizedInfo.artist,
                "playing" to normalizedInfo.isPlaying,
                "positionMs" to normalizedInfo.position,
                "durationMs" to normalizedInfo.duration,
                "stateUpdateTimeMs" to normalizedInfo.playbackStateUpdateTimeMs,
                "positionOverride" to (stalePositionOverrideMediaKey == mediaIdentityKey(normalizedInfo))
            )
        )

        // Restore original metadata before showing lyrics
        if (forceLyricsResync) {
            lyricDisplayManager?.stop()
        }
        mediaStateUpdater?.update(session, normalizedInfo)
        lyricDisplayManager?.start(session, normalizedInfo)

        mediaInfoListener?.invoke(normalizedInfo)
        scheduleEndOfMediaRefresh(normalizedInfo)
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

        val positionMs = if (stalePositionOverrideMediaKey == mediaIdentityKey(info)) {
            getStalePositionOverrideMs()
        } else {
            MediaInformationRetriever.getCurrentPositionMs(state)
        }
        return positionMs > info.duration + END_OF_MEDIA_REFRESH_GRACE_MS
    }

    private fun getCurrentSourcePositionMs(ctx: Context, fallbackInfo: MediaInfo): Long {
        if (stalePositionOverrideMediaKey == mediaIdentityKey(fallbackInfo)) {
            return getStalePositionOverrideMs()
        }

        val state = MediaControllerManager.getActiveController(ctx)?.playbackState
        return state
            ?.let { MediaInformationRetriever.getCurrentPositionMs(it) }
            ?: MediaInformationRetriever.getEstimatedPositionMs(fallbackInfo)
    }

    private fun normalizeMediaInfoPosition(previousInfo: MediaInfo?, info: MediaInfo): MediaInfo {
        val mediaKey = mediaIdentityKey(info)
        val nowMs = SystemClock.elapsedRealtime()

        if (stalePositionOverrideMediaKey != null && stalePositionOverrideMediaKey != mediaKey) {
            clearStalePositionOverride()
        }

        val shouldStartOverride = shouldTreatPositionAsStaleAfterMediaChange(
            previousInfo = previousInfo,
            info = info,
            lastMetadataChangeElapsedRealtimeMs = lastSourceMetadataChangeElapsedRealtimeMs,
            nowElapsedRealtimeMs = nowMs
        )
        val shouldContinueOverride = stalePositionOverrideMediaKey == mediaKey &&
                info.isPlaying &&
                info.position > NEW_MEDIA_STALE_POSITION_THRESHOLD_MS &&
                info.position - getStalePositionOverrideMs(nowMs) > STALE_POSITION_CLEAR_TOLERANCE_MS

        if (!shouldStartOverride && !shouldContinueOverride) {
            if (stalePositionOverrideMediaKey == mediaKey) {
                clearStalePositionOverride()
            }
            return info
        }

        if (stalePositionOverrideMediaKey != mediaKey) {
            stalePositionOverrideMediaKey = mediaKey
            stalePositionOverrideStartedAtMs = nowMs
            stalePositionRecheckCount = 0
        }

        val correctedPositionMs = getStalePositionOverrideMs(nowMs)
        if (stalePositionRecheckCount < STALE_POSITION_RECHECK_LIMIT) {
            stalePositionRecheckCount++
            scheduleSourceRefresh(
                "Rechecking source playback state after media change",
                STALE_POSITION_RECHECK_DELAY_MS
            )
        }

        DiagnosticLogger.warn(
            context ?: return info.copy(position = correctedPositionMs, retrievedAtElapsedRealtimeMs = nowMs),
            DiagnosticModule.MEDIA,
            "Corrected stale playback position after media change",
            mapOf(
                "package" to info.appPackageName,
                "title" to info.title,
                "rawPositionMs" to info.position,
                "correctedPositionMs" to correctedPositionMs,
                "durationMs" to info.duration,
                "stateUpdateTimeMs" to info.playbackStateUpdateTimeMs,
                "metadataChangeTimeMs" to lastSourceMetadataChangeElapsedRealtimeMs
            )
        )

        return info.copy(position = correctedPositionMs, retrievedAtElapsedRealtimeMs = nowMs)
    }

    internal fun shouldTreatPositionAsStaleAfterMediaChange(
        previousInfo: MediaInfo?,
        info: MediaInfo,
        lastMetadataChangeElapsedRealtimeMs: Long,
        nowElapsedRealtimeMs: Long
    ): Boolean {
        if (previousInfo == null || isSameMedia(previousInfo, info)) return false
        if (!info.isPlaying || info.position <= NEW_MEDIA_STALE_POSITION_THRESHOLD_MS) return false

        if (isPlaybackStateOlderThanMetadata(info, lastMetadataChangeElapsedRealtimeMs)) {
            return true
        }

        if (!previousInfo.isPlaying) return false

        val previousProjectedPositionMs = MediaInformationRetriever.getEstimatedPositionMs(
            previousInfo,
            nowElapsedRealtimeMs
        )
        return abs(info.position - previousProjectedPositionMs) <= CARRIED_POSITION_TOLERANCE_MS
    }

    private fun shouldRefreshPendingStalePosition(info: MediaInfo, state: PlaybackState): Boolean {
        if (stalePositionOverrideMediaKey != mediaIdentityKey(info)) return false

        val sourcePositionMs = MediaInformationRetriever.getCurrentPositionMs(state)
        val correctedPositionMs = getStalePositionOverrideMs()
        return sourcePositionMs <= NEW_MEDIA_STALE_POSITION_THRESHOLD_MS ||
                abs(sourcePositionMs - correctedPositionMs) <= STALE_POSITION_CLEAR_TOLERANCE_MS ||
                !isPlaybackStateOlderThanMetadata(
                    info.copy(playbackStateUpdateTimeMs = state.lastPositionUpdateTime),
                    lastSourceMetadataChangeElapsedRealtimeMs
                )
    }

    private fun isPlaybackStateOlderThanMetadata(
        info: MediaInfo,
        lastMetadataChangeElapsedRealtimeMs: Long
    ): Boolean {
        return lastMetadataChangeElapsedRealtimeMs > 0L &&
                info.playbackStateUpdateTimeMs > 0L &&
                info.playbackStateUpdateTimeMs + STATE_METADATA_STALE_TOLERANCE_MS <
                lastMetadataChangeElapsedRealtimeMs
    }

    private fun getStalePositionOverrideMs(
        nowElapsedRealtimeMs: Long = SystemClock.elapsedRealtime()
    ): Long {
        if (stalePositionOverrideStartedAtMs <= 0L) return 0L
        return (nowElapsedRealtimeMs - stalePositionOverrideStartedAtMs).coerceAtLeast(0L)
    }

    private fun clearStalePositionOverride() {
        stalePositionOverrideMediaKey = null
        stalePositionOverrideStartedAtMs = 0L
        stalePositionRecheckCount = 0
    }

    private fun isSameMedia(first: MediaInfo, second: MediaInfo): Boolean {
        return mediaIdentityKey(first) == mediaIdentityKey(second)
    }

    private fun mediaIdentityKey(info: MediaInfo): String {
        return listOf(
            info.appPackageName,
            info.title.trim(),
            info.artist.trim(),
            info.album.trim(),
            info.duration.takeIf { it > 0L }?.toString().orEmpty()
        ).joinToString("|")
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
    private const val NEW_MEDIA_STALE_POSITION_THRESHOLD_MS = 10_000L
    private const val CARRIED_POSITION_TOLERANCE_MS = 15_000L
    private const val STALE_POSITION_CLEAR_TOLERANCE_MS = 5_000L
    private const val STATE_METADATA_STALE_TOLERANCE_MS = 500L
    private const val STALE_POSITION_RECHECK_DELAY_MS = 750L
    private const val STALE_POSITION_RECHECK_LIMIT = 3
}
