package io.github.milankablar.carkaraoke

import android.content.Context
import android.content.SharedPreferences
import android.media.session.MediaController
import android.net.Uri
import android.os.SystemClock
import io.github.milankablar.carkaraoke.karaoke.CandidateProvider
import io.github.milankablar.carkaraoke.karaoke.ConfiguredCandidates
import io.github.milankablar.carkaraoke.karaoke.KaraokeCoordinator
import io.github.milankablar.carkaraoke.karaoke.LyricCandidate
import io.github.milankablar.carkaraoke.karaoke.RecordingLyricsRepository
import io.github.milankablar.carkaraoke.karaoke.TrackIdentity
import io.github.milankablar.carkaraoke.lyrics.LyricsRepository
import io.github.milankablar.carkaraoke.lyrics.LyricsStorage
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class TransportCommand { PLAY, PAUSE, NEXT, PREVIOUS }

/** Lightweight application-scoped dependency container owned by [CarKaraokeApplication]. */
class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val candidates: CandidateProvider = ConfiguredCandidates(appContext)
    val repository: RecordingLyricsRepository = RecordingLyricsRepository(appContext, candidates)
    val coordinator: KaraokeCoordinator = KaraokeCoordinator(scope, repository::resolve, SystemClock::elapsedRealtime)

    private var settingsSignature = SettingsManager.playbackSettingsSignature(appContext)
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        val signature = SettingsManager.playbackSettingsSignature(appContext)
        if (SettingsManager.affectsPlayback(key) && signature != settingsSignature) {
            settingsSignature = signature
            scope.launch { coordinator.refresh() }
        }
    }

    init {
        scope.launch { LyricsRepository.lyricsUpdatedFlow.collect { coordinator.refresh() } }
        SettingsManager.addChangeListener(appContext, settingsListener)
    }

    fun chooseCandidate(track: TrackIdentity, candidate: LyricCandidate) {
        scope.launch {
            try {
                repository.choose(track, candidate)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                coordinator.reportError("Could not save lyrics. Please retry.")
            }
        }
    }

    fun resetSelection() {
        val track = coordinator.state.value.track ?: return
        scope.launch {
            try {
                LyricsRepository.deleteLyrics(appContext, listOf(track.key))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                coordinator.reportError("Could not reset saved lyrics.")
            }
        }
    }

    fun adjustOffset(deltaMs: Long) {
        val track = coordinator.state.value.track ?: return
        scope.launch {
            try {
                repository.offset(track, deltaMs)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                coordinator.reportError("Could not save timing adjustment.")
            }
        }
    }

    fun refreshLyrics() {
        coordinator.refresh()
    }

    suspend fun searchCandidates(track: TrackIdentity): List<LyricCandidate> = candidates.search(track)

    suspend fun importLyrics(uri: Uri, expectedTrack: TrackIdentity): LyricCandidate {
        val content = withContext(Dispatchers.IO) {
            val stream = appContext.contentResolver.openInputStream(uri)
                ?: throw IOException("Could not open lyrics file")
            stream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= LyricsStorage.MAX_LYRICS_BYTES) {
                        "Lyrics must be smaller than 2 MB"
                    }
                    output.write(buffer, 0, count)
                }
                val bytes = output.toByteArray()
                require(bytes.size <= LyricsStorage.MAX_LYRICS_BYTES) { "Lyrics must be smaller than 2 MB" }
                bytes.toString(Charsets.UTF_8)
            }
        }
        check(expectedTrack.key == coordinator.state.value.track?.key) {
            "Song changed. Select the original song and import again."
        }
        return LyricCandidate(
            id = "import",
            provider = "Imported LRC",
            title = expectedTrack.title,
            artist = expectedTrack.artist,
            album = expectedTrack.album,
            durationMs = expectedTrack.durationMs,
            content = content,
            verifiedIdentity = false
        )
    }

    fun sendTransport(command: TransportCommand) {
        val controls = MediaControllerManager.getActiveController(appContext)?.transportControls ?: return
        when (command) {
            TransportCommand.PLAY -> controls.play()
            TransportCommand.PAUSE -> controls.pause()
            TransportCommand.NEXT -> controls.skipToNext()
            TransportCommand.PREVIOUS -> controls.skipToPrevious()
        }
        MediaBridgeSessionManager.requestRefresh("Phone transport", 250)
    }

    fun seekTo(positionMs: Long) {
        MediaControllerManager.getActiveController(appContext)?.transportControls?.seekTo(positionMs)
        MediaBridgeSessionManager.requestRefresh("Phone seek", 250)
    }

    fun activePlayers(): List<MediaController> = MediaControllerManager.getAllControllers(appContext)

    fun selectPlayer(controller: MediaController) {
        MediaControllerManager.select(controller)
        MediaBridgeSessionManager.refreshCurrentSession()
    }

    fun useAutomaticPlayer() {
        MediaControllerManager.useAutomaticSelection()
        MediaBridgeSessionManager.refreshCurrentSession()
    }

    fun refreshCarDisplay() {
        MediaBridgeSessionManager.refreshCurrentSession(true)
    }
}

fun Context.appContainer(): AppContainer {
    val app = applicationContext as? CarKaraokeApplication
        ?: error("Application must be CarKaraokeApplication")
    return app.container
}
