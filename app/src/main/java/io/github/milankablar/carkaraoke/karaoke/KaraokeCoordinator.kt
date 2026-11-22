package io.github.milankablar.carkaraoke.karaoke

import io.github.milankablar.carkaraoke.models.MediaInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** One owner of song identity, resolution and playback time for every display. */
class KaraokeCoordinator(
    private val scope: CoroutineScope,
    private val resolve: suspend (TrackIdentity, Boolean) -> LyricResolution,
    private val now: () -> Long
) {
    private val mutable = MutableStateFlow(KaraokeState())
    val state: StateFlow<KaraokeState> = mutable.asStateFlow()
    private var fetch: Job? = null
    private var ticker: Job? = null
    private var generation = 0L

    fun update(info: MediaInfo?, force: Boolean = false) {
        val old = mutable.value
        if (info == null) {
            generation++
            fetch?.cancel()
            ticker?.cancel()
            mutable.value = KaraokeState(revision = generation)
            return
        }
        val incoming = TrackIdentity.from(info)
        val changed = old.track?.sameRecording(incoming) != true ||
            (old.track?.durationMs == 0L && incoming.durationMs > 0L) ||
            (old.track?.mediaId.isNullOrBlank() && !incoming.mediaId.isNullOrBlank())
        val track = if (changed) incoming else old.track!!
        mutable.value = old.copy(
            info = info,
            track = track,
            resolution = if (changed) LyricResolution(LyricsStatus.LOADING) else old.resolution
        )
        tick()
        ticker?.cancel()
        if (info.isPlaying) {
            ticker = scope.launch {
                while (isActive) {
                    delay(100)
                    tick()
                }
            }
        }
        if (incoming.title.isBlank()) {
            generation++
            fetch?.cancel()
            mutable.value = mutable.value.copy(
                resolution = LyricResolution(LyricsStatus.LOADING, message = "Waiting for song details")
            )
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

    fun reportError(message: String) {
        mutable.value = mutable.value.copy(resolution = mutable.value.resolution.copy(message = message))
    }

    fun refresh() = update(mutable.value.info, true)

    private fun tick() {
        val current = mutable.value
        val info = current.info ?: return
        val position = PlaybackClock.position(
            info.position,
            info.retrievedAtElapsedRealtimeMs,
            now(),
            info.isPlaying,
            info.playbackSpeed,
            info.duration
        )
        mutable.value = current.copy(
            positionMs = position,
            currentIndex = current.resolution.document.indexAt(position - current.resolution.offsetMs)
        )
    }

    fun close() {
        generation++
        fetch?.cancel()
        ticker?.cancel()
    }
}
