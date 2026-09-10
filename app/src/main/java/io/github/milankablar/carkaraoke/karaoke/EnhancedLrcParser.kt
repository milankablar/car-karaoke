package io.github.milankablar.carkaraoke.karaoke

/** Parses ordinary/enhanced LRC without inventing word timestamps. */
object EnhancedLrcParser {
    private val stamp = Regex("\\[(\\d+):(\\d{1,2}(?:\\.\\d+)?)\\]")
    private val wordStamp = Regex("<(\\d+):(\\d{1,2}(?:\\.\\d+)?)>")
    private val offset = Regex("(?im)^\\[offset:([+-]?\\d+)\\]\\s*$")
    fun parse(content: String): LyricDocument {
        val fileOffset = offset.find(content)?.groupValues?.get(1)?.toLongOrNull()?.coerceIn(-86_400_000, 86_400_000) ?: 0
        val lines = content.lineSequence().flatMap { raw ->
            val text = raw.trim().removePrefix("\uFEFF")
            val timestamps = mutableListOf<Long>(); var end = 0
            stamp.findAll(text).takeWhile { it.range.first == end }.forEach { tag ->
                end = tag.range.last + 1
                time(tag)?.let { timestamps += it }
            }
            val body = text.drop(end)
            val all = wordStamp.findAll(body).toList()
            val words = all.mapIndexedNotNull { index, match ->
                val start = time(match) ?: return@mapIndexedNotNull null
                val chunk = body.substring(match.range.last + 1, all.getOrNull(index + 1)?.range?.first ?: body.length)
                if (chunk.isEmpty()) null else LyricWord((start + fileOffset).coerceAtLeast(0), chunk)
            }
            val firstTime = timestamps.firstOrNull() ?: return@flatMap emptySequence()
            val visible = wordStamp.replace(body, "").trim()
            timestamps.asSequence().map { start ->
                val delta = start - firstTime
                KaraokeLine((start + fileOffset).coerceAtLeast(0), visible, words.map { it.copy(timeMs = (it.timeMs + delta).coerceAtLeast(0)) }.sortedBy { it.timeMs })
            }
        }.sortedBy { it.timeMs }.toList()
        return if (lines.isEmpty()) LyricDocument(emptyList(), content.lineSequence().filterNot { it.trim().matches(Regex("\\[[a-zA-Z]+:.*]")) }.joinToString("\n").trim()) else LyricDocument(lines)
    }
    private fun time(match: MatchResult): Long? {
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        val seconds = match.groupValues[2].toDoubleOrNull() ?: return null
        if (minutes !in 0..1_000_000 || !seconds.isFinite() || seconds < 0 || seconds >= 60) return null
        return minutes * 60_000 + kotlin.math.round(seconds * 1_000).toLong()
    }
}
