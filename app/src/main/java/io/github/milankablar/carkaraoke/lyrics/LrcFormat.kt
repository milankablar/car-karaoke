package io.github.milankablar.carkaraoke.lyrics

import java.util.Locale
import kotlin.math.roundToLong

/** Shared timestamp parsing and editing for LRC files. */
object LrcFormat {
    val timestamp = Regex("""\[(\d+):(\d{1,2}(?:\.\d+)?)]""")

    fun timeMs(match: MatchResult): Long? {
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        val seconds = match.groupValues[2].toDoubleOrNull() ?: return null
        if (minutes > 1_000_000 || !seconds.isFinite() || seconds < 0 || seconds >= 60) return null
        return minutes * 60_000 + (seconds * 1000).roundToLong()
    }

    fun shift(content: String, deltaMs: Long): String = timestamp.replace(content) { match ->
        val original = timeMs(match) ?: return@replace match.value
        val shifted = (original + deltaMs.coerceIn(-86_400_000, 86_400_000)).coerceAtLeast(0)
        val centiseconds = (shifted + 5) / 10
        String.format(Locale.US, "[%02d:%02d.%02d]", centiseconds / 6000, centiseconds / 100 % 60, centiseconds % 100)
    }
}
