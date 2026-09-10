package io.github.milankablar.carkaraoke.karaoke

import android.content.Context
import android.os.SystemClock
import io.github.milankablar.carkaraoke.models.MediaInfo
import io.github.milankablar.carkaraoke.lyrics.LyricsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** One owner of song identity, resolution and playback time for every display. */
class KaraokeCoordinator(
    private val scope: CoroutineScope,
    private val resolve: suspend (TrackIdentity, Boolean) -> LyricResolution,
    private val now: () -> Long = { SystemClock.elapsedRealtime() }
) {
    private val mutable = MutableStateFlow(KaraokeState())
    val state: StateFlow<KaraokeState> = mutable.asStateFlow()
    private var fetch: Job? = null
    private var ticker: Job? = null
    private var generation = 0L
    fun update(info: MediaInfo?, force: Boolean = false) {
        val old = mutable.value
        if (info == null) {
            generation++; fetch?.cancel(); ticker?.cancel(); mutable.value = KaraokeState(revision = generation); return
        }
        val incoming = TrackIdentity.from(info)
        val changed = old.track?.sameRecording(incoming) != true || (old.track?.durationMs == 0L && incoming.durationMs > 0L) || (old.track?.mediaId.isNullOrBlank() && !incoming.mediaId.isNullOrBlank())
        val track = if (changed) incoming else old.track!!
        mutable.value = old.copy(info = info, track = track, resolution = if (changed) LyricResolution(LyricsStatus.LOADING) else old.resolution)
        tick()
        ticker?.cancel()
        if (info.isPlaying) ticker = scope.launch { while (isActive) { delay(100); tick() } }
        if (incoming.title.isBlank() || incoming.artist.isBlank()) {
            generation++; fetch?.cancel()
            mutable.value = mutable.value.copy(resolution = LyricResolution(LyricsStatus.LOADING, message = "Waiting for song details"))
            return
        }
        if (changed || force) {
            val request = ++generation
            fetch?.cancel()
            fetch = scope.launch {
                val result = resolve(track, force)
                if (request == generation && mutable.value.track?.key == track.key) {
                    mutable.value = mutable.value.copy(resolution = result, revision = request)
                    tick()
                }
            }
        }
    }
    fun reportError(message: String) { mutable.value = mutable.value.copy(resolution = mutable.value.resolution.copy(message = message)) }
    fun refresh() = update(mutable.value.info, true)
    private fun tick() {
        val current = mutable.value
        val info = current.info ?: return
        val position = PlaybackClock.position(info.position, info.retrievedAtElapsedRealtimeMs, now(), info.isPlaying, info.playbackSpeed, info.duration)
        mutable.value = current.copy(positionMs = position, currentIndex = current.resolution.document.indexAt(position - current.resolution.offsetMs))
    }
    fun close() { generation++; fetch?.cancel(); ticker?.cancel() }
}

object KaraokeRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var settingsSignature = ""
    private var settingsListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null
    lateinit var repository: RecordingLyricsRepository
        private set
    lateinit var coordinator: KaraokeCoordinator
        private set
    fun initialize(context: Context) {
        if (::coordinator.isInitialized) return
        contextForStorage = context.applicationContext
        repository = RecordingLyricsRepository(context.applicationContext)
        coordinator = KaraokeCoordinator(scope, repository::resolve)
        scope.launch { LyricsRepository.lyricsUpdatedFlow.collect { coordinator.refresh() } }
        settingsSignature = io.github.milankablar.carkaraoke.SettingsManager.playbackSettingsSignature(context)
        settingsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            val signature = io.github.milankablar.carkaraoke.SettingsManager.playbackSettingsSignature(context)
            if (io.github.milankablar.carkaraoke.SettingsManager.affectsPlayback(key) && signature != settingsSignature) {
                settingsSignature = signature
                scope.launch { coordinator.refresh() }
            }
        }.also { io.github.milankablar.carkaraoke.SettingsManager.addChangeListener(context, it) }
    }
    fun choose(track: TrackIdentity, candidate: LyricCandidate) {
        scope.launch {
            try { repository.choose(track, candidate) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { coordinator.reportError("Could not save lyrics. Please retry.") }
        }
    }
    fun resetSelection() {
        val track = coordinator.state.value.track ?: return
        scope.launch { try { LyricsRepository.deleteLyrics(contextForStorage!!, listOf(track.key)) } catch (e: CancellationException) { throw e } catch (e: Exception) { coordinator.reportError("Could not reset saved lyrics.") } }
    }
    private var contextForStorage: Context? = null
    fun offset(deltaMs: Long) { val track = coordinator.state.value.track ?: return; scope.launch { try { repository.offset(track, deltaMs) } catch (e: CancellationException) { throw e } catch (e: Exception) { coordinator.reportError("Could not save timing adjustment.") } } }
}
