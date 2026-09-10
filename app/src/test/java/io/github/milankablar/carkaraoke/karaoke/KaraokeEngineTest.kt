package io.github.milankablar.carkaraoke.karaoke

import io.github.milankablar.carkaraoke.models.MediaInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KaraokeEngineTest {
    private fun track(title: String = "Song", duration: Long = 180000) = TrackIdentity("player", "id-$title", title, "Artist", "Album", duration)
    private fun candidate(title: String = "Song", duration: Long = 180000, content: String = "[00:01]First") = LyricCandidate("1", "test", title, "Artist", "Album", duration, content)
    private fun info(title: String, position: Long = 0, playing: Boolean = false) = MediaInfo("player", "Music", title, "Artist", "Album", 180000, position, playing, null, null, retrievedAtElapsedRealtimeMs = 1, mediaId = "id-$title")
    @Test fun `recording identity separates versions sessions and media IDs`() {
        val original = track()
        assertNotEquals(original.key, original.copy(source = "other").key)
        assertNotEquals(original.key, original.copy(mediaId = "live").key)
        assertNotEquals(original.key, original.copy(durationMs = 210000).key)
        assertFalse(original.sameRecording(original.copy(album = "Live")))
        assertFalse(original.sameRecording(original.copy(mediaId = "live")))
        assertTrue(original.sameRecording(original.copy(title = " Song ")))
    }
    @Test fun `matching rejects wrong title artist duration and unverifiable text`() {
        assertNull(LyricMatcher.select(track(), listOf(candidate("Future song"))))
        assertNull(LyricMatcher.select(track(), listOf(candidate().copy(artist = "Other"))))
        assertNull(LyricMatcher.select(track(), listOf(candidate(duration = 210000))))
        assertNull(LyricMatcher.select(track(), listOf(candidate().copy(verifiedIdentity = false))))
        assertNull(LyricMatcher.select(track(), listOf(candidate(duration = 0))))
        assertEquals(candidate(), LyricMatcher.select(track(), listOf(candidate())))
    }
    @Test fun `ambiguous results need selection but identical duplicates do not`() {
        assertNull(LyricMatcher.select(track(), listOf(candidate(), candidate(content = "[00:04]Different"))))
        assertNotNull(LyricMatcher.select(track(), listOf(candidate(), candidate().copy(id = "2"))))
    }
    @Test fun `LRC supports offsets repeated timestamps blanks and actual word times`() {
        val doc = EnhancedLrcParser.parse("[offset:250]\n[00:01.00][00:05.00]<00:01.00>Hello <00:01.50>world\n[00:09.00]\n[00:10.00]End")
        assertEquals(listOf(1250L, 5250L, 9250L, 10250L), doc.lines.map { it.timeMs })
        assertEquals("Hello world", doc.lines[0].text)
        assertEquals(listOf(1250L, 1750L), doc.lines[0].words.map { it.timeMs })
        assertEquals(listOf(5250L, 5750L), doc.lines[1].words.map { it.timeMs })
        assertEquals(-1, doc.indexAt(0)); assertEquals(0, doc.indexAt(1250)); assertEquals(2, doc.indexAt(9500))
    }
    @Test fun `plain lyrics stay plain and malformed timestamps do not become timing`() {
        assertEquals("Hello", EnhancedLrcParser.parse("[ar:Artist]\nHello").plainText)
        assertFalse(EnhancedLrcParser.parse("[99:99.9]Bad").synced)
        assertTrue(EnhancedLrcParser.parse("[00:01]Hello").lines.single().words.isEmpty())
    }
    @Test fun `clock respects pause rate seek bounds and missing anchors`() {
        assertEquals(3000L, PlaybackClock.position(1000, 100, 1100, true, 2f))
        assertEquals(1000L, PlaybackClock.position(1000, 100, 1100, false, 2f))
        assertEquals(1500L, PlaybackClock.position(1000, 100, 1100, true, 2f, 1500))
        assertEquals(1000L, PlaybackClock.position(1000, 0, 1100, true, 2f))
        assertEquals(1000L, PlaybackClock.position(1000, 100, 1100, true, Float.NaN))
    }
    @Test fun `late A response cannot replace B or a later A request`() = runTest {
        val pending = mutableListOf<Pair<String, CompletableDeferred<LyricResolution>>>()
        val coordinator = KaraokeCoordinator(backgroundScope, { track, _ ->
            val deferred = CompletableDeferred<LyricResolution>()
            pending += track.title to deferred
            withContext(NonCancellable) { deferred.await() }
        }, { 1 })
        coordinator.update(info("A")); runCurrent()
        coordinator.update(info("B")); runCurrent()
        coordinator.update(info("A")); runCurrent()
        pending[0].second.complete(LyricResolution(LyricsStatus.PLAIN, LyricDocument(emptyList(), "obsolete")))
        pending[1].second.complete(LyricResolution(LyricsStatus.PLAIN, LyricDocument(emptyList(), "wrong song")))
        runCurrent()
        assertEquals(LyricsStatus.LOADING, coordinator.state.value.resolution.status)
        pending[2].second.complete(LyricResolution(LyricsStatus.PLAIN, LyricDocument(emptyList(), "latest A")))
        runCurrent()
        assertEquals("latest A", coordinator.state.value.resolution.document.plainText)
        coordinator.close()
    }
    @Test fun `pause and seek update both consumers without new lyric requests`() = runTest {
        var requests = 0
        val coordinator = KaraokeCoordinator(backgroundScope, { _, _ -> requests++; LyricResolution(LyricsStatus.SYNCED, EnhancedLrcParser.parse("[00:01]One\n[00:04]Two")) }, { 1 })
        coordinator.update(info("A", 1500)); runCurrent()
        val phone = coordinator.state; val car = coordinator.state
        assertEquals(0, phone.value.currentIndex)
        coordinator.update(info("A", 4500)); runCurrent()
        assertEquals(1, car.value.currentIndex); assertEquals(phone.value, car.value); assertEquals(1, requests)
        coordinator.update(null); assertNull(car.value.track)
        coordinator.close()
    }
    @Test fun `manual lyrics can resolve when the player omits artist metadata`() = runTest {
        val coordinator = KaraokeCoordinator(backgroundScope, { _, _ -> LyricResolution(LyricsStatus.SYNCED, EnhancedLrcParser.parse("[00:01]Manual"), manual = true) }, { 1 })
        coordinator.update(info("A", 1500).copy(artist = "")); runCurrent()
        assertEquals("Manual", coordinator.state.value.resolution.document.lines.single().text)
        assertTrue(coordinator.state.value.resolution.manual)
        coordinator.close()
    }
}
