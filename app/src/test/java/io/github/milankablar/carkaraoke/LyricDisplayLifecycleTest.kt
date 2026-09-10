package io.github.milankablar.carkaraoke

import android.content.Context
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import io.github.milankablar.carkaraoke.lyrics.LyricCache
import io.github.milankablar.carkaraoke.lyrics.LyricLine
import io.github.milankablar.carkaraoke.models.MediaInfo
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LyricDisplayLifecycleTest {
    private lateinit var context: Context
    private val session = mockk<MediaSessionCompat>(relaxed = true)
    private fun info(title: String) = MediaInfo("music.app", "Music", title, "Artist", "Album", 60_000, 0, true, null, null)

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        SettingsManager.setLyricsEnabled(context, true)
        mockkObject(LyricCache)
    }
    @After fun teardown() { unmockkObject(LyricCache) }

    @Test fun `stopping cancels pending lookup without waiting`() = runTest {
        val entered = CompletableDeferred<Unit>()
        var cancelled = false
        coEvery { LyricCache.getOrFetchLyrics(any(), any(), any(), any()) } coAnswers {
            entered.complete(Unit)
            try { awaitCancellation() } finally { cancelled = true }
        }
        val manager = LyricDisplayManager(context, backgroundScope)
        manager.start(session, info("Song"))
        runCurrent()
        assertTrue(entered.isCompleted)
        manager.stop()
        runCurrent()
        assertTrue(cancelled)
        verify(exactly = 0) { session.setMetadata(any()) }
        manager.close()
    }

    @Test fun `new song cancels previous sync before its fetch completes`() = runTest {
        coEvery { LyricCache.getOrFetchLyrics(any(), "Old", any(), any()) } returns
            listOf(LyricLine(0f, "old line"), LyricLine(1f, "stale line"))
        val newLyrics = CompletableDeferred<List<LyricLine>>()
        coEvery { LyricCache.getOrFetchLyrics(any(), "New", any(), any()) } coAnswers { newLyrics.await() }
        val manager = LyricDisplayManager(context, backgroundScope)
        manager.start(session, info("Old"))
        runCurrent()
        manager.start(session, info("New"))
        runCurrent()
        advanceTimeBy(5000)
        runCurrent()
        newLyrics.complete(listOf(LyricLine(0f, "new line")))
        runCurrent()
        val metadata = mutableListOf<MediaMetadataCompat>()
        verify { session.setMetadata(capture(metadata)) }
        assertEquals(listOf("old line", "new line"), metadata.map { it.getString(MediaMetadataCompat.METADATA_KEY_TITLE) })
        manager.close()
    }

    @Test fun `storage failure restores title instead of escaping coroutine`() = runTest {
        coEvery { LyricCache.getOrFetchLyrics(any(), any(), any(), any()) } throws java.io.IOException("unavailable")
        val manager = LyricDisplayManager(context, backgroundScope)
        manager.start(session, info("Song"))
        runCurrent()
        val metadata = slot<MediaMetadataCompat>()
        verify { session.setMetadata(capture(metadata)) }
        assertEquals("Song", metadata.captured.getString(MediaMetadataCompat.METADATA_KEY_TITLE))
        manager.close()
    }
}
