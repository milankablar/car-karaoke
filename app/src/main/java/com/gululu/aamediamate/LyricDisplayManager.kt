package com.gululu.aamediamate

import android.content.Context
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import com.gululu.aamediamate.diagnostics.DiagnosticLogger
import com.gululu.aamediamate.diagnostics.DiagnosticModule
import com.gululu.aamediamate.lyrics.LyricCache
import com.gululu.aamediamate.lyrics.LyricSyncEngine
import com.gululu.aamediamate.lyrics.LyricsRepository
import com.gululu.aamediamate.models.MediaInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LyricDisplayManager(private val context: Context) {

    private val lyricsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentLyricsJob: Job? = null
    private val lyricsJobMutex = Mutex()
    private var lyricsUpdateJob: Job? = null
    private var currentMediaInfo: MediaInfo? = null

    private val wakeLock: PowerManager.WakeLock by lazy {
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AAMediaMate:LyricSync")
            .apply { setReferenceCounted(false) }
    }

    fun start(mediaSession: MediaSessionCompat, info: MediaInfo) {
        val globalLyricsEnabled = SettingsManager.getLyricsEnabled(context)
        val appLyricsEnabled = SettingsManager.isAppLyricsEnabled(context, info.appPackageName)
        
        if (!globalLyricsEnabled || !appLyricsEnabled || !info.isPlaying || info.title.isBlank() || info.artist.isBlank()) {
            if (!globalLyricsEnabled) {
                Log.d("MediaBridge", "🚫 Lyrics globally disabled")
                DiagnosticLogger.info(context, DiagnosticModule.LYRICS, "Lyrics globally disabled")
            } else if (!appLyricsEnabled) {
                Log.d("MediaBridge", "🚫 Lyrics disabled for app: ${info.appPackageName}")
                DiagnosticLogger.info(
                    context,
                    DiagnosticModule.LYRICS,
                    "Lyrics disabled for app",
                    mapOf("package" to info.appPackageName)
                )
            }
            stop()
            return
        }
        
        Log.d("MediaBridge", "🎵 Starting lyrics for: ${info.appPackageName} - ${info.title} by ${info.artist}")
        DiagnosticLogger.info(
            context,
            DiagnosticModule.LYRICS,
            "Starting lyric display",
            mapOf(
                "package" to info.appPackageName,
                "title" to info.title,
                "artist" to info.artist,
                "durationMs" to info.duration
            )
        )
        currentMediaInfo = info
        if (!wakeLock.isHeld) wakeLock.acquire()

        // Start observing lyric updates
        lyricsUpdateJob?.cancel() // Cancel any previous observation
        lyricsUpdateJob = lyricsScope.launch {
            val observedMediaInfo = info // Capture the media info that started this observation
            LyricsRepository.lyricsUpdatedFlow.collectLatest { updatedKey ->
                val currentKey = "${observedMediaInfo.title}_${observedMediaInfo.artist}"
                if (updatedKey == currentKey) {
                    Log.d("MediaBridge", "🎤 Lyrics for current song updated. Restarting lyric display.")
                    DiagnosticLogger.info(
                        context,
                        DiagnosticModule.LYRICS,
                        "Current song lyrics updated",
                        mapOf("title" to observedMediaInfo.title, "artist" to observedMediaInfo.artist)
                    )
                    // Stop internal components and restart to refresh with new lyrics
                    stopInternal()
                    start(mediaSession, observedMediaInfo)
                }
            }
        }

        lyricsScope.launch {
            lyricsJobMutex.withLock {
                currentLyricsJob?.cancelAndJoin()
                currentLyricsJob = launch {
                    val lyrics = LyricCache.getOrFetchLyrics(
                        context,
                        info.title,
                        info.artist,
                        info.duration.toString()
                    )

                    if (lyrics.isEmpty()) {
                        Log.d("MediaBridge", "🚫 Lyrics not found: ${info.title}")
                        DiagnosticLogger.warn(
                            context,
                            DiagnosticModule.LYRICS,
                            "Lyrics not found for display",
                            mapOf("title" to info.title, "artist" to info.artist)
                        )
                        updateLyricLine(mediaSession, info, "", null) // Clear the displayed lyric
                        return@launch
                    }

                    val currentPosition = MediaInformationRetriever.getEstimatedPositionMs(info)
                    val offsetMs = SettingsManager.getLyricsTimingOffset(context).toLong()
                    DiagnosticLogger.info(
                        context,
                        DiagnosticModule.LYRICS,
                        "Lyric sync starting",
                        mapOf("lineCount" to lyrics.size, "positionMs" to currentPosition, "offsetMs" to offsetMs)
                    )

                    LyricSyncEngine.start(lyrics, currentPosition, offsetMs) { line, nextLine ->
                        updateLyricLine(mediaSession, info, line, nextLine)
                    }
                }
            }
        }
    }

    fun stop() {
        stopInternal()
        lyricsUpdateJob?.cancel()
        currentMediaInfo = null
        if (wakeLock.isHeld) wakeLock.release()
    }

    private fun stopInternal() {
        LyricSyncEngine.stop()
        runBlocking {
            lyricsJobMutex.withLock {
                currentLyricsJob?.cancel()
                currentLyricsJob = null
            }
        }
    }

    private fun updateLyricLine(mediaSession: MediaSessionCompat, originalInfo: MediaInfo, lyricLine: String, nextLyricLine: String?) {
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, originalInfo.duration)

        if (SettingsManager.getShowSourceApp(context)) {
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "From ${originalInfo.appName}")
        }

        val showAlbumName = SettingsManager.getShowAlbumName(context)
        val showNextLyricLine = SettingsManager.getShowNextLyricLine(context)

        if (lyricLine.isNotBlank()) {
            // When a lyric is displayed, use the lyric as the title
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, lyricLine)

            val artistText = if (showNextLyricLine) {
                nextLyricLine?.takeIf { it.isNotBlank() } ?: buildSongInfoText(originalInfo, showAlbumName)
            } else {
                buildSongInfoText(originalInfo, showAlbumName)
            }

            if (artistText.isNotBlank()) {
                metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artistText)
            }

        } else {
            // When no lyric is displayed, restore the original media info formatted correctly
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, originalInfo.title)

            val artist = originalInfo.artist.takeIf { it.isNotBlank() }
            val album = originalInfo.album.takeIf { it.isNotBlank() }
            
            val artistText = if (showAlbumName) {
                listOfNotNull(artist, album).joinToString(" - ")
            } else {
                artist ?: ""
            }

            if (artistText.isNotBlank()) {
                metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artistText)
            }
        }

        originalInfo.albumArt?.let {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, it)
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
        }

        mediaSession.setMetadata(metadataBuilder.build())
    }

    private fun buildSongInfoText(originalInfo: MediaInfo, showAlbumName: Boolean): String {
        val songInfoParts = mutableListOf<String>()
        originalInfo.title.takeIf { it.isNotBlank() }?.let { songInfoParts.add(it) }
        originalInfo.artist.takeIf { it.isNotBlank() }?.let { songInfoParts.add(it) }
        if (showAlbumName) {
            originalInfo.album.takeIf { it.isNotBlank() }?.let { songInfoParts.add(it) }
        }
        return songInfoParts.joinToString(" - ")
    }
}
