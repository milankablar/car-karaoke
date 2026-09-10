package io.github.milankablar.carkaraoke.karaoke

/** Car presentation derives from shared state; display latency never changes song timing. */
object CarLyricsPresenter {
    fun index(state: KaraokeState, displayOffsetMs: Long): Int = state.resolution.document.indexAt(state.positionMs - state.resolution.offsetMs - displayOffsetMs)
    fun window(state: KaraokeState, displayOffsetMs: Long): List<Pair<Boolean, String>> {
        val index = index(state, displayOffsetMs)
        val lines = state.resolution.document.lines
        if (lines.isEmpty()) return state.resolution.document.plainText.lines().filter { it.isNotBlank() }.take(200).map { false to it }
        val start = (index - 1).coerceAtLeast(0)
        return lines.drop(start).take(3).mapIndexed { offset, line -> (start + offset == index) to line.text.ifBlank { "♪" } }
    }
}
