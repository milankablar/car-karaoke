package io.github.milankablar.carkaraoke.karaoke

data class CarLyricRow(
    val active: Boolean,
    val text: String,
    val timeMs: Long? = null
)

data class CarLyricSnapshot(
    val headerTitle: String,
    val headerSubtitle: String,
    val previousLine: String,
    val currentLine: String,
    val nextLine: String,
    val afterNextLine: String = "",
    val statusMessage: String,
    val hasLiveLyric: Boolean = false,
    val activeTimeMs: Long? = null,
    val rows: List<CarLyricRow> = emptyList()
)

/** Car presentation derives from shared state; display latency never changes song timing. */
object CarLyricsPresenter {
    private const val BRIEF_GAP_MS = 3_000L

    fun index(state: KaraokeState, displayOffsetMs: Long): Int = state.resolution.document.indexAt(state.positionMs - state.resolution.offsetMs - displayOffsetMs)

    fun window(state: KaraokeState, displayOffsetMs: Long): List<Pair<Boolean, String>> {
        val snap = snapshot(state, displayOffsetMs)
        return snap.rows.map { it.active to it.text }
    }

    fun snapshot(state: KaraokeState, displayOffsetMs: Long): CarLyricSnapshot {
        val headerTitle = state.info?.title?.ifBlank { "Car Karaoke" } ?: "Car Karaoke"
        val headerSubtitle = state.info?.artist.orEmpty()
        val lines = state.resolution.document.lines
        val effectivePosMs = state.positionMs - state.resolution.offsetMs - displayOffsetMs

        if (lines.isEmpty()) {
            val plain = state.resolution.document.plainText.lines().map { it.trim() }.filter { it.isNotBlank() }
            if (plain.isNotEmpty()) {
                val rows = plain.take(200).map { CarLyricRow(false, it) }
                return CarLyricSnapshot(
                    headerTitle = headerTitle,
                    headerSubtitle = headerSubtitle,
                    previousLine = "",
                    currentLine = plain.first(),
                    nextLine = plain.getOrElse(1) { "" },
                    statusMessage = "",
                    rows = rows
                )
            }
            val status = statusText(state.resolution)
            return CarLyricSnapshot(
                headerTitle = headerTitle,
                headerSubtitle = headerSubtitle,
                previousLine = "",
                currentLine = status,
                nextLine = "",
                statusMessage = status
            )
        }

        val rawIndex = index(state, displayOffsetMs)
        val prevNonBlankIdx = findPreviousNonBlank(lines, rawIndex - 1)
        val nextNonBlankIdx = findNextNonBlank(lines, rawIndex + 1)

        if (rawIndex < 0) {
            val firstIdx = findNextNonBlank(lines, 0)
            val firstLine = firstIdx?.let { lines[it] }
            val secondLine = firstIdx?.let { findNextNonBlank(lines, it + 1) }?.let { lines[it] }
            val cue = instrumentalCue(firstLine?.timeMs, effectivePosMs)
            val rows = buildList {
                add(CarLyricRow(true, cue, 0L))
                if (firstLine != null) add(CarLyricRow(false, firstLine.text, firstLine.timeMs))
                if (secondLine != null) add(CarLyricRow(false, secondLine.text, secondLine.timeMs))
            }
            return CarLyricSnapshot(
                headerTitle = headerTitle,
                headerSubtitle = headerSubtitle,
                previousLine = "",
                currentLine = cue,
                nextLine = firstLine?.text.orEmpty(),
                afterNextLine = secondLine?.text.orEmpty(),
                statusMessage = "",
                hasLiveLyric = firstLine != null,
                activeTimeMs = 0L,
                rows = rows
            )
        }

        val activeRaw = lines[rawIndex]
        if (activeRaw.text.isBlank()) {
            val nextLine = nextNonBlankIdx?.let { lines[it] }
            val afterNextLine = nextNonBlankIdx?.let { findNextNonBlank(lines, it + 1) }?.let { lines[it] }
            val gapRemainingMs = nextLine?.let { it.timeMs - effectivePosMs }
            if (prevNonBlankIdx != null && gapRemainingMs != null && gapRemainingMs < BRIEF_GAP_MS) {
                return buildSyncedSnapshot(
                    headerTitle = headerTitle,
                    headerSubtitle = headerSubtitle,
                    prev = findPreviousNonBlank(lines, prevNonBlankIdx - 1)?.let { lines[it] },
                    current = lines[prevNonBlankIdx],
                    next = nextLine,
                    afterNext = afterNextLine
                )
            }
            val cue = instrumentalCue(nextLine?.timeMs, effectivePosMs)
            val prevLine = prevNonBlankIdx?.let { lines[it] }
            val rows = buildList {
                if (prevLine != null) add(CarLyricRow(false, prevLine.text, prevLine.timeMs))
                add(CarLyricRow(true, cue, activeRaw.timeMs))
                if (nextLine != null) add(CarLyricRow(false, nextLine.text, nextLine.timeMs))
            }
            return CarLyricSnapshot(
                headerTitle = headerTitle,
                headerSubtitle = headerSubtitle,
                previousLine = prevLine?.text.orEmpty(),
                currentLine = cue,
                nextLine = nextLine?.text.orEmpty(),
                afterNextLine = afterNextLine?.text.orEmpty(),
                statusMessage = "",
                hasLiveLyric = nextLine != null,
                activeTimeMs = activeRaw.timeMs,
                rows = rows
            )
        }

        return buildSyncedSnapshot(
            headerTitle = headerTitle,
            headerSubtitle = headerSubtitle,
            prev = prevNonBlankIdx?.let { lines[it] },
            current = activeRaw,
            next = nextNonBlankIdx?.let { lines[it] },
            afterNext = nextNonBlankIdx?.let { findNextNonBlank(lines, it + 1) }?.let { lines[it] }
        )
    }

    private fun buildSyncedSnapshot(
        headerTitle: String,
        headerSubtitle: String,
        prev: KaraokeLine?,
        current: KaraokeLine,
        next: KaraokeLine?,
        afterNext: KaraokeLine?
    ): CarLyricSnapshot {
        val rows = buildList {
            if (prev != null) add(CarLyricRow(false, prev.text, prev.timeMs))
            add(CarLyricRow(true, current.text, current.timeMs))
            if (next != null) add(CarLyricRow(false, next.text, next.timeMs))
        }
        return CarLyricSnapshot(
            headerTitle = headerTitle,
            headerSubtitle = headerSubtitle,
            previousLine = prev?.text.orEmpty(),
            currentLine = current.text,
            nextLine = next?.text.orEmpty(),
            afterNextLine = afterNext?.text.orEmpty(),
            statusMessage = "",
            hasLiveLyric = true,
            activeTimeMs = current.timeMs,
            rows = rows
        )
    }

    private fun findPreviousNonBlank(lines: List<KaraokeLine>, startIndex: Int): Int? {
        for (i in startIndex downTo 0) {
            if (lines[i].text.isNotBlank()) return i
        }
        return null
    }

    private fun findNextNonBlank(lines: List<KaraokeLine>, startIndex: Int): Int? {
        for (i in startIndex.coerceAtLeast(0) until lines.size) {
            if (lines[i].text.isNotBlank()) return i
        }
        return null
    }

    private fun instrumentalCue(nextTimeMs: Long?, effectivePosMs: Long): String {
        if (nextTimeMs == null) return "♪"
        val remainingSec = ((nextTimeMs - effectivePosMs + 999L) / 1000L).toInt()
        return if (remainingSec in 1..9) "♪  Next in ${remainingSec}s" else "♪"
    }

    private fun statusText(resolution: LyricResolution): String = when (resolution.status) {
        LyricsStatus.NO_MEDIA -> "Play a song to begin"
        LyricsStatus.LOADING -> "Finding lyrics…"
        LyricsStatus.INSTRUMENTAL -> "♪  Instrumental"
        LyricsStatus.NEEDS_SELECTION -> {
            val count = resolution.candidates.size
            if (count > 0) "$count matches — choose on phone" else "Choose lyrics on phone"
        }
        LyricsStatus.NOT_FOUND -> "No lyrics found"
        LyricsStatus.ERROR -> "Lyrics unavailable"
        LyricsStatus.DISABLED -> "Lyrics turned off"
        LyricsStatus.SYNCED, LyricsStatus.PLAIN -> ""
    }
}
