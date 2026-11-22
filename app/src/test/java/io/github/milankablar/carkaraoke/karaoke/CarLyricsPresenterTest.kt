package io.github.milankablar.carkaraoke.karaoke

import io.github.milankablar.carkaraoke.models.MediaInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarLyricsPresenterTest {
    private val info = MediaInfo(
        appPackageName = "com.spotify.music",
        appName = "Spotify",
        title = "Swallowed By The World",
        artist = "Artist",
        album = "Album",
        duration = 180_000L,
        position = 0L,
        isPlaying = true,
        albumArt = null,
        appIcon = null
    )

    @Test
    fun `snapshot exposes previous current and next lines with timestamps`() {
        val doc = EnhancedLrcParser.parse("[00:02]First line\n[00:06]Second line\n[00:10]Third line\n[00:14]Fourth line")
        val state = KaraokeState(
            info = info,
            resolution = LyricResolution(LyricsStatus.SYNCED, doc),
            positionMs = 7_000L,
            currentIndex = 1
        )

        val snap = CarLyricsPresenter.snapshot(state, 0L)
        assertEquals("Swallowed By The World", snap.headerTitle)
        assertEquals("Artist", snap.headerSubtitle)
        assertEquals("First line", snap.previousLine)
        assertEquals("Second line", snap.currentLine)
        assertEquals("Third line", snap.nextLine)
        assertEquals("Fourth line", snap.afterNextLine)
        assertEquals(listOf(false, true, false), snap.rows.map { it.active })
        assertEquals(listOf(2_000L, 6_000L, 10_000L), snap.rows.map { it.timeMs })
    }

    @Test
    fun `brief blank gap holds previous lyric instead of flashing music note`() {
        val doc = EnhancedLrcParser.parse("[00:02]First line\n[00:05]\n[00:07]Second line")
        val state = KaraokeState(
            info = info,
            resolution = LyricResolution(LyricsStatus.SYNCED, doc),
            positionMs = 5_500L,
            currentIndex = 1
        )

        val snap = CarLyricsPresenter.snapshot(state, 0L)
        assertEquals("First line", snap.currentLine)
        assertEquals("Second line", snap.nextLine)
    }

    @Test
    fun `long instrumental break shows countdown cue and upcoming line`() {
        val doc = EnhancedLrcParser.parse("[00:02]First line\n[00:05]\n[00:12]Second line")
        val state = KaraokeState(
            info = info,
            resolution = LyricResolution(LyricsStatus.SYNCED, doc),
            positionMs = 7_500L,
            currentIndex = 1
        )

        val snap = CarLyricsPresenter.snapshot(state, 0L)
        assertEquals("First line", snap.previousLine)
        assertEquals("♪  Next in 5s", snap.currentLine)
        assertEquals("Second line", snap.nextLine)
    }

    @Test
    fun `ambiguous status surfaces actionable candidate count`() {
        val candidate = LyricCandidate("1", "LRCLIB", "Song", "Artist", "Album", 180_000L, "[00:01]Hi")
        val state = KaraokeState(
            info = info,
            resolution = LyricResolution(LyricsStatus.NEEDS_SELECTION, candidates = listOf(candidate, candidate.copy(id = "2")))
        )

        val snap = CarLyricsPresenter.snapshot(state, 0L)
        assertTrue(snap.statusMessage.contains("2 matches"))
        assertEquals(snap.statusMessage, snap.currentLine)
    }
}
