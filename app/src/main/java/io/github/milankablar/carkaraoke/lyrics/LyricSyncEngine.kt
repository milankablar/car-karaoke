package io.github.milankablar.carkaraoke.lyrics

import android.os.SystemClock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.math.ceil

/** Synchronizes lyrics within the caller's job, using a monotonic playback clock. */
object LyricSyncEngine {
    suspend fun sync(
        lyrics: List<LyricLine>,
        startPositionMs: Long,
        offsetMs: Long = 0L,
        playbackSpeed: Float = 1f,
        nowMs: () -> Long = SystemClock::elapsedRealtime,
        onLineChanged: (String, String?) -> Unit
    ) {
        if (lyrics.isEmpty() || !playbackSpeed.isFinite() || playbackSpeed <= 0f) return
        val startedAt = nowMs()
        var lastIndex = Int.MIN_VALUE
        while (true) {
            currentCoroutineContext().ensureActive()
            val position = startPositionMs + (nowMs() - startedAt).coerceAtLeast(0) * playbackSpeed.toDouble() - offsetMs
            val index = lyrics.indexOfLast { it.timeSec * 1000.0 <= position }
            if (index != lastIndex) {
                onLineChanged(lyrics.getOrNull(index)?.text.orEmpty(), lyrics.getOrNull(index + 1)?.text)
                lastIndex = index
            }
            val next = lyrics.getOrNull(index + 1) ?: return
            delay(ceil((next.timeSec * 1000.0 - position) / playbackSpeed).toLong().coerceAtLeast(1))
        }
    }
}
