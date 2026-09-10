package io.github.milankablar.carkaraoke

import android.content.Context
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.annotation.MainThread
import io.github.milankablar.carkaraoke.diagnostics.DiagnosticLogger
import io.github.milankablar.carkaraoke.diagnostics.DiagnosticModule
import io.github.milankablar.carkaraoke.lyrics.LyricCache
import io.github.milankablar.carkaraoke.lyrics.LyricSyncEngine
import io.github.milankablar.carkaraoke.lyrics.LyricsRepository
import kotlinx.coroutines.*
import io.github.milankablar.carkaraoke.models.MediaInfo

/** Owns lyric work for one service; all state and metadata writes stay on the main thread. */
class LyricDisplayManager(
    private val context: Context,
    private val lyricsScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {
    private var currentLyricsJob: Job? = null
    private var lyricsUpdateJob: Job? = null
    private var generation = 0L
    private var wakeLock: PowerManager.WakeLock? = null

    @MainThread
    fun start(mediaSession: MediaSessionCompat, info: MediaInfo) {
        stop()
        if (!SettingsManager.getLyricsEnabled(context) ||
            !SettingsManager.isAppLyricsEnabled(context, info.appPackageName) ||
            !info.isPlaying || info.playbackSpeed <= 0f || info.title.isBlank() || info.artist.isBlank()
        ) return
        val requestGeneration = generation
        val key = LyricsRepository.keyFor(info.title, info.artist)
        lyricsUpdateJob = lyricsScope.launch {
            LyricsRepository.lyricsUpdatedFlow.collect { updatedKey ->
                if (updatedKey == key || updatedKey == LyricsRepository.ALL_LYRICS) {
                    MediaBridgeSessionManager.refreshCurrentSession(forceLyricsResync = true)
                }
            }
        }
        currentLyricsJob = lyricsScope.launch {
            try {
                val lyrics = LyricCache.getOrFetchLyrics(context, info.title, info.artist, info.duration.toString())
                ensureActive()
                if (requestGeneration != generation) return@launch
                if (lyrics.isEmpty()) {
                    updateLyricLine(mediaSession, info, "", null)
                    return@launch
                }
                val position = MediaInformationRetriever.getEstimatedPositionMs(info)
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AAMediaMate:LyricSync")?.apply {
                    setReferenceCounted(false)
                    val remaining = ((lyrics.last().timeSec * 1000 - position).coerceAtLeast(0f) / info.playbackSpeed).toLong()
                    acquire((remaining + 60_000).coerceIn(60_000, 6 * 60 * 60_000))
                }
                LyricSyncEngine.sync(
                    lyrics, position, SettingsManager.getLyricsTimingOffset(context).toLong(), info.playbackSpeed
                ) { line, nextLine ->
                    if (requestGeneration == generation) updateLyricLine(mediaSession, info, line, nextLine)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DiagnosticLogger.error(context, DiagnosticModule.LYRICS, "Lyric display failed", throwable = e)
                if (requestGeneration == generation) updateLyricLine(mediaSession, info, "", null)
            } finally {
                if (requestGeneration == generation) releaseWakeLock()
            }
        }
    }

    @MainThread
    fun stop() {
        generation++
        currentLyricsJob?.cancel()
        currentLyricsJob = null
        lyricsUpdateJob?.cancel()
        lyricsUpdateJob = null
        releaseWakeLock()
    }

    /** Releases all work owned by the destroyed service. */
    @MainThread
    fun close() {
        stop()
        lyricsScope.cancel()
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
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
