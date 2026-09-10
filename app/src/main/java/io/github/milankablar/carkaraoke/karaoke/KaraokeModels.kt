package io.github.milankablar.carkaraoke.karaoke

import io.github.milankablar.carkaraoke.models.MediaInfo
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/** A recording, separate from its current playback position or its artwork. */
data class TrackIdentity(val source: String, val mediaId: String?, val title: String, val artist: String, val album: String, val durationMs: Long) {
    val key: String get() {
        val identity = listOf(source, mediaId.orEmpty(), normalize(title), normalize(artist), normalize(album), durationMs.toString())
            .joinToString("") { "${it.length}:$it" }
        return "v3_" + MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
    }
    fun sameRecording(other: TrackIdentity): Boolean {
        if (source != other.source) return false
        if (!mediaId.isNullOrBlank() && !other.mediaId.isNullOrBlank() && mediaId != other.mediaId) return false
        if (normalize(title) != normalize(other.title) || normalize(artist) != normalize(other.artist)) return false
        if (album.isNotBlank() && other.album.isNotBlank() && normalize(album) != normalize(other.album)) return false
        return durationMs <= 0 || other.durationMs <= 0 || abs(durationMs - other.durationMs) <= 2_000
    }
    companion object {
        fun from(info: MediaInfo) = TrackIdentity(info.appPackageName, info.mediaId, info.title, info.artist, info.album, info.duration)
        fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
            .replace(Regex("[\\p{Punct}\\p{P}]+"), " ").replace(Regex("\\s+"), " ").trim()
    }
}

data class LyricWord(val timeMs: Long, val text: String)
data class KaraokeLine(val timeMs: Long, val text: String, val words: List<LyricWord> = emptyList())
data class LyricDocument(val lines: List<KaraokeLine>, val plainText: String = "") {
    val synced get() = lines.isNotEmpty()
    fun indexAt(positionMs: Long): Int {
        var low = 0; var high = lines.size
        while (low < high) { val mid = (low + high) / 2; if (lines[mid].timeMs <= positionMs) low = mid + 1 else high = mid }
        return low - 1
    }
}

data class LyricCandidate(val id: String, val provider: String, val title: String, val artist: String,
    val album: String, val durationMs: Long, val content: String, val instrumental: Boolean = false,
    val verifiedIdentity: Boolean = true)

enum class LyricsStatus { NO_MEDIA, LOADING, SYNCED, PLAIN, INSTRUMENTAL, NEEDS_SELECTION, NOT_FOUND, ERROR, DISABLED }
data class LyricResolution(val status: LyricsStatus, val document: LyricDocument = LyricDocument(emptyList()),
    val candidates: List<LyricCandidate> = emptyList(), val source: String = "", val message: String = "",
    val offsetMs: Long = 0, val manual: Boolean = false)
data class KaraokeState(val info: MediaInfo? = null, val track: TrackIdentity? = null,
    val resolution: LyricResolution = LyricResolution(LyricsStatus.NO_MEDIA), val positionMs: Long = 0,
    val currentIndex: Int = -1, val revision: Long = 0)

/** The same monotonic clock is used on both presentation surfaces. */
object PlaybackClock {
    fun position(anchorMs: Long, observedAtMs: Long, nowMs: Long, playing: Boolean, speed: Float, durationMs: Long = 0): Long {
        val elapsed = if (playing && observedAtMs > 0 && speed.isFinite() && speed > 0) (nowMs - observedAtMs).coerceAtLeast(0) * speed.toDouble() else 0.0
        val result = (anchorMs.coerceAtLeast(0) + elapsed).toLong().coerceAtLeast(0)
        return if (durationMs > 0) result.coerceAtMost(durationMs) else result
    }
}

/** Conservative automatic selection; ambiguous matches remain a user decision. */
object LyricMatcher {
    fun score(track: TrackIdentity, candidate: LyricCandidate): Int {
        if (!candidate.verifiedIdentity || track.title.isBlank() || track.artist.isBlank()) return -1
        if (TrackIdentity.normalize(track.title) != TrackIdentity.normalize(candidate.title)) return -1
        if (TrackIdentity.normalize(track.artist) != TrackIdentity.normalize(candidate.artist)) return -1
        if (track.durationMs > 0 && candidate.durationMs > 0 && abs(track.durationMs - candidate.durationMs) > 3_000) return -1
        // Missing duration cannot establish a safe automatic match.
        if (track.durationMs <= 0 || candidate.durationMs <= 0) return -1
        return 10 + if (track.album.isNotBlank() && TrackIdentity.normalize(track.album) == TrackIdentity.normalize(candidate.album)) 2 else 0
    }
    fun select(track: TrackIdentity, candidates: List<LyricCandidate>): LyricCandidate? {
        val ranked = candidates.map { it to score(track, it) }.filter { it.second >= 0 }.sortedByDescending { it.second }
        if (ranked.isEmpty()) return null
        val top = ranked.filter { it.second == ranked.first().second }
        // Identical content on duplicate album entries is not an ambiguous lyric choice.
        return if (top.map { it.first.content to it.first.instrumental }.distinct().size == 1) top.first().first else null
    }
}
