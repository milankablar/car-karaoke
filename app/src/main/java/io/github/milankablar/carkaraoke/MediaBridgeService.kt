package io.github.milankablar.carkaraoke

import android.os.Bundle
import android.util.Log
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import androidx.media.MediaBrowserServiceCompat
import io.github.milankablar.carkaraoke.diagnostics.DiagnosticLogger
import io.github.milankablar.carkaraoke.diagnostics.DiagnosticModule

class MediaBridgeService : MediaBrowserServiceCompat() {
    
    private var lastSettingsSignature = ""
    private val settingsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        val signature = SettingsManager.playbackSettingsSignature(this)
        if (SettingsManager.affectsPlayback(key) && signature != lastSettingsSignature) {
            lastSettingsSignature = signature
            io.github.milankablar.carkaraoke.lyrics.LyricCache.clearAllMemoryCache()
            MediaBridgeSessionManager.refreshCurrentSession()
            refreshBrowserData()
        }
    }

    companion object {
        private const val ROOT_ID = "root"
        private var instance: MediaBridgeService? = null
        
        fun refreshLyrics() { instance?.notifyChildrenChanged("lyrics") }

        fun refreshBrowserData() {
            instance?.let { service ->
                Log.d("MediaBridge", "🔄 Refreshing browser data")
                service.notifyChildrenChanged("root")
                service.notifyChildrenChanged("players")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        DiagnosticLogger.info(this, DiagnosticModule.MEDIA, "Media browser service started")

        MediaBridgeSessionManager.acquire(this, "car")
        lastSettingsSignature = SettingsManager.playbackSettingsSignature(this)
        SettingsManager.addChangeListener(this, settingsListener)

        sessionToken = MediaBridgeSessionManager.getSessionToken()!!

        val mediaInfo = MediaInformationRetriever.refreshCurrentMediaInfo(this)
        if (mediaInfo != null)
        {
            MediaBridgeSessionManager.updateFromMediaInfo(mediaInfo)
        }

        Log.d("MediaBridge", "MediaBrowserServiceCompat started")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        MediaBridgeSessionManager.relinquish("car")
        SettingsManager.removeChangeListener(this, settingsListener)
        instance = null
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        if (!MediaClientValidator.isTrusted(this, clientPackageName, clientUid)) return null
        return BrowserRoot(ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        Log.d("MediaBridge", "🔄 onLoadChildren called for parentId: $parentId")

        val context = this.applicationContext

        if (parentId == "lyrics") {
            val state = io.github.milankablar.carkaraoke.karaoke.KaraokeRuntime.coordinator.state.value
            val rows = io.github.milankablar.carkaraoke.karaoke.CarLyricsPresenter.window(state, getSharedPreferences("presentation", 0).getLong("carOffset", 0))
            val items = rows.mapIndexed { index, (active, text) ->
                MediaBrowserCompat.MediaItem(MediaDescriptionCompat.Builder()
                    .setMediaId("lyric:${state.track?.key}:$index")
                    .setTitle(if (active) "▶ $text" else text)
                    .setSubtitle(state.info?.title).build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
            }.toMutableList()
            if (items.isEmpty()) items.add(MediaBrowserCompat.MediaItem(MediaDescriptionCompat.Builder()
                .setMediaId("lyric-status").setTitle(state.resolution.status.name.lowercase().replace('_', ' '))
                .setSubtitle("Open Car Karaoke on your phone for lyric corrections").build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
            result.sendResult(items)
            return
        }
        if (parentId == ROOT_ID) {
            result.sendResult(mutableListOf(
                MediaBrowserCompat.MediaItem(MediaDescriptionCompat.Builder().setMediaId("lyrics").setTitle("Live lyrics").setSubtitle("Follow the current song").build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE),
                MediaBrowserCompat.MediaItem(MediaDescriptionCompat.Builder().setMediaId("players").setTitle("Music apps").setSubtitle("Choose your player").build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
            ))
            return
        }
        if (parentId != "players") {
            result.sendResult(mutableListOf())
            return
        }
        
        if (!hasNotificationAccess(context)) {
            MediaBridgeSessionManager.showBrowserError(
                getString(R.string.notification_access_required_car)
            )
            result.sendResult(mutableListOf())
            return
        }

        val controllers = MediaControllerManager.getAllControllers(context)

        val items = controllers.mapNotNull { controller ->
            val mediaInfo = runCatching { MediaInformationRetriever.buildMediaInfoFromController(context, controller) }.getOrNull()
            
            // Only show apps that have active media info
            mediaInfo?.let {
                val description = MediaDescriptionCompat.Builder()
                    .setMediaId(controller.packageName)
                    .setTitle(it.appName)
                    .setSubtitle(it.title)
                    .setIconBitmap(it.appIcon)
                    .build()

                MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
            }
        }.toMutableList()

        if (items.isEmpty()) {
            MediaBridgeSessionManager.showBrowserError(getString(R.string.no_active_media_car))
        } else {
            MediaBridgeSessionManager.clearBrowserError()
        }

        Log.d("MediaBridge", "📋 Loaded ${items.size} media items")
        DiagnosticLogger.debug(
            this,
            DiagnosticModule.MEDIA,
            "Media browser children loaded",
            mapOf("parentId" to parentId, "itemCount" to items.size)
        )
        result.sendResult(items)
    }

}
