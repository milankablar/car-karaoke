package com.gululu.aamediamate.lyrics

import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class LyricsRepositoryTest {
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
    }

    @Test fun emptyDirectory() = runTest {
        assertTrue(LyricsRepository.getAllLyrics(context).isEmpty())
    }

    @Test fun saveLoadDelete() = runTest {
        val key = LyricsRepository.keyFor("Song", "Artist")
        LyricsRepository.saveLyricsText(context, key, "[00:01.00]New lyrics")
        assertEquals("[00:01.00]New lyrics", LyricsRepository.loadLyricsText(context, key))
        assertEquals(1, LyricsRepository.getAllLyrics(context).size)
        LyricsRepository.deleteLyrics(context, listOf(key))
        assertFalse(File(directory, "$key.lrt").exists())
        assertEquals("", LyricsRepository.loadLyricsText(context, key))
        assertTrue(LyricsRepository.getAllLyrics(context).isEmpty())
    }

    @Test fun batchShiftUsesLocaleIndependentTimestamps() = runTest {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            LyricsRepository.saveLyricsText(context, "song_artist", "[00:01]first\n[00:59.75]next")
            LyricsRepository.shiftLyricsByMs(context, listOf("song_artist"), 500)
            assertEquals("[00:01.50]first\n[01:00.25]next", LyricsRepository.loadLyricsText(context, "song_artist"))
        } finally { Locale.setDefault(previous) }
    }

    @Test fun keysAreUnambiguousAndSafe() {
        assertNotEquals(LyricsRepository.keyFor("a_b", "c"), LyricsRepository.keyFor("a", "b_c"))
        assertNotEquals(LyricsRepository.keyFor("a/b", "c"), LyricsRepository.keyFor("a:b", "c"))
        assertEquals(67, LyricsRepository.keyFor("x".repeat(2000), "/artist").length)
    }

    @Test fun pathTraversalIsRejected() = runTest {
        try {
            LyricsRepository.saveLyricsText(context, "../outside", "lyrics")
            fail("Expected invalid key")
        } catch (_: IllegalArgumentException) { }
        assertFalse(File(directory.parentFile, "outside.lrt").exists())
    }

    @Test fun listIsSortedByLastModified() = runTest {
        val old = File(directory, "Old_Artist.lrt").apply { writeText("old"); setLastModified(1000) }
        val newer = File(directory, "New_Artist.lrt").apply { writeText("new"); setLastModified(2000) }
        assertEquals(listOf("New", "Old"), LyricsRepository.getAllLyrics(context).map { it.title })
    }
}
