package com.gululu.aamediamate.lyrics

import android.content.Context
import android.content.ContextWrapper
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class LyricCacheTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var context: Context
    private lateinit var directory: File

    @Before fun setup() {
        directory = folder.newFolder("lyrics")
        val internal = folder.newFolder("internal")
        context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getExternalFilesDir(type: String?): File = directory
            override fun getFilesDir(): File = internal
        }
        LyricCache.clearAllMemoryCache()
        mockkObject(LyricsManager)
    }

    @After fun teardown() { unmockkObject(LyricsManager) }

    @Test fun legacyFileMigratesAndIdentityPreservesSpecialCharacters() = runTest {
        File(directory, "a_b_Artist.lrt").writeText("[00:00]hello")
        val lines = LyricCache.getOrFetchLyrics(context, "a/b", "Artist", "100")
        assertEquals(listOf(LyricLine(0f, "hello")), lines)
        val key = LyricsRepository.keyFor("a/b", "Artist")
        assertEquals("a/b" to "Artist", LyricsRepository.identity(context, key))
        assertTrue(File(directory, "$key.lrt").exists())
        assertFalse(File(directory, "a_b_Artist.lrt").exists())
    }

    @Test fun transientFailureDoesNotCreatePermanentMiss() = runTest {
        coEvery { LyricsManager.getLyricsLrt(any(), any(), any(), any()) } throws IOException("offline")
        try {
            LyricCache.getOrFetchLyrics(context, "Song", "Artist", "100")
            fail("Expected network failure")
        } catch (_: IOException) { }
        val key = LyricsRepository.keyFor("Song", "Artist")
        assertFalse(File(directory, "$key.lrt").exists())
        coEvery { LyricsManager.getLyricsLrt(any(), any(), any(), any()) } returns "[00:00]online"
        assertEquals("online", LyricCache.getOrFetchLyrics(context, "Song", "Artist", "100").single().text)
    }

    @Test fun inFlightFetchCannotOverwriteUserEdit() = runTest {
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<String>()
        coEvery { LyricsManager.getLyricsLrt(any(), any(), any(), any()) } coAnswers {
            started.complete(Unit)
            response.await()
        }
        val key = LyricsRepository.keyFor("Song", "Artist")
        val fetching = async { LyricCache.getOrFetchLyrics(context, "Song", "Artist", "100") }
        started.await()
        LyricsRepository.saveLyricsText(context, key, "[00:00]edited")
        response.complete("[00:00]network")
        assertEquals("edited", fetching.await().single().text)
        assertEquals("[00:00]edited", LyricsRepository.loadLyricsText(context, key))
    }

    @Test fun deletingDuringFetchDoesNotRecreateFile() = runTest {
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<String>()
        coEvery { LyricsManager.getLyricsLrt(any(), any(), any(), any()) } coAnswers {
            started.complete(Unit)
            response.await()
        }
        val key = LyricsRepository.keyFor("Song", "Artist")
        val fetching = async { LyricCache.getOrFetchLyrics(context, "Song", "Artist", "100") }
        started.await()
        LyricsRepository.deleteLyrics(context, listOf(key))
        response.complete("[00:00]network")
        assertTrue(fetching.await().isEmpty())
        assertFalse(File(directory, "$key.lrt").exists())
    }
}
