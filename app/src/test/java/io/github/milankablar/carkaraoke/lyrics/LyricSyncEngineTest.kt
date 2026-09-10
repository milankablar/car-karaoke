package io.github.milankablar.carkaraoke.lyrics

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LyricSyncEngineTest {
    private val lyrics = listOf(LyricLine(0f, "First"), LyricLine(10f, "Second"), LyricLine(20f, "Third"))

    @Test fun `middle starts with current line and waits for next`() = runTest {
        val lines = mutableListOf<String>()
        backgroundScope.launch { LyricSyncEngine.sync(lyrics, 15_000, nowMs = { testScheduler.currentTime }) { line, _ -> lines += line } }
        runCurrent()
        assertEquals(listOf("Second"), lines)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf("Second", "Third"), lines)
    }

    @Test fun `past last line emits last line only`() = runTest {
        val lines = mutableListOf<String>()
        LyricSyncEngine.sync(lyrics, 30_000, nowMs = { testScheduler.currentTime }) { line, _ -> lines += line }
        assertEquals(listOf("Third"), lines)
    }

    @Test fun `cancellation prevents later metadata updates`() = runTest {
        val lines = mutableListOf<String>()
        val job = launch { LyricSyncEngine.sync(lyrics, 0, nowMs = { testScheduler.currentTime }) { line, _ -> lines += line } }
        runCurrent()
        job.cancel()
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(listOf("First"), lines)
        assertTrue(job.isCancelled)
    }

    @Test fun `speed and offset apply to initial selection and subsequent timing`() = runTest {
        val lines = mutableListOf<String>()
        backgroundScope.launch {
            LyricSyncEngine.sync(lyrics, 10_000, offsetMs = 2_000, playbackSpeed = 2f, nowMs = { testScheduler.currentTime }) { line, _ -> lines += line }
        }
        runCurrent()
        assertEquals(listOf("First"), lines)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf("First", "Second"), lines)
    }
}
