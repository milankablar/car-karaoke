package io.github.milankablar.carkaraoke.backup

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import io.github.milankablar.carkaraoke.SettingsManager
import io.github.milankablar.carkaraoke.lyrics.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class BackupManagerTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var context: Context
    private lateinit var directory: File
    private lateinit var internal: File

    @Before fun setup() {
        directory = folder.newFolder("lyrics")
        internal = folder.newFolder("internal")
        val cache = folder.newFolder("cache")
        context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getExternalFilesDir(type: String?): File = directory
            override fun getFilesDir(): File = internal
            override fun getCacheDir(): File = cache
        }
        LyricCache.clearAllMemoryCache()
        context.getSharedPreferences("media_bridge_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun teardown() { unmockkObject(LyricsStorage) }

    private fun zip(vararg entries: Pair<String, String>): Uri {
        val file = folder.newFile()
        ZipOutputStream(file.outputStream()).use { output ->
            entries.forEach { (name, text) ->
                output.putNextEntry(ZipEntry(name))
                output.write(text.toByteArray())
                output.closeEntry()
            }
        }
        return Uri.fromFile(file)
    }

    private fun manifest(count: Int = 1) = JSONObject().put("formatVersion", 1)
        .put("lyricsFileCount", count).put("includesLyrics", true).put("includesSettings", true).toString()

    @Test fun invalidSettingsDoNotPublishStagedLyrics() = runTest {
        SettingsManager.setLyricsEnabled(context, false)
        File(directory, "song_artist.lrt").writeText("original")
        val uri = zip("manifest.json" to manifest(), "lyrics/song_artist.lrt" to "replacement",
            "settings.json" to """{"lyrics_enabled":true,"bridged_apps":[{}]}""")
        try {
            BackupManager.restoreBackup(context, uri, true, true)
            fail("Expected invalid settings")
        } catch (_: Exception) { }
        assertEquals("original", File(directory, "song_artist.lrt").readText())
        assertFalse(SettingsManager.getLyricsEnabled(context))
    }

    @Test fun incompleteArchiveLeavesOriginalUntouched() = runTest {
        File(directory, "song_artist.lrt").writeText("original")
        val uri = zip("manifest.json" to manifest(2), "lyrics/song_artist.lrt" to "replacement")
        try {
            BackupManager.restoreBackup(context, uri, true, false)
            fail("Expected incomplete archive")
        } catch (_: IOException) { }
        assertEquals("original", File(directory, "song_artist.lrt").readText())
    }

    @Test fun oversizedEntryBeforeManifestIsRejectedByPreview() = runTest {
        val uri = zip("unexpected/" to "x".repeat(LyricsStorage.MAX_LYRICS_BYTES + 1),
            "manifest.json" to manifest(0))
        try {
            BackupManager.readBackupPreview(context, uri)
            fail("Expected bounded decompression")
        } catch (_: IOException) { }
    }

    @Test fun oversizedDirectoryEntryIsRejectedDuringRestore() = runTest {
        val uri = zip("manifest.json" to manifest(0),
            "lyrics/" to "x".repeat(LyricsStorage.MAX_LYRICS_BYTES + 1))
        try {
            BackupManager.restoreBackup(context, uri, true, false)
            fail("Expected bounded directory entry")
        } catch (_: IOException) { }
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun traversalIsRejected() = runTest {
        val uri = zip("manifest.json" to manifest(), "lyrics/../outside.lrt" to "bad")
        try {
            BackupManager.restoreBackup(context, uri, true, false)
            fail("Expected unsafe name")
        } catch (_: IOException) { }
        assertFalse(File(directory.parentFile, "outside.lrt").exists())
    }

    @Test fun backupRoundTripPreservesSongIdentityAndSettings() = runTest {
        val key = LyricsRepository.keyFor("A/B_C", "Artist: D")
        LyricsStorage.rememberIdentity(context, key, "A/B_C", "Artist: D")
        LyricsRepository.saveLyricsText(context, key, "[00:01]hello")
        SettingsManager.setLyricsEnabled(context, true)
        val uri = Uri.fromFile(folder.newFile("backup.zip"))
        BackupManager.createBackup(context, uri, true, true, false)
        LyricsRepository.deleteLyrics(context, listOf(key))
        SettingsManager.setLyricsEnabled(context, false)
        val result = BackupManager.restoreBackup(context, uri, true, true)
        assertEquals(1, result.restoredLyricsCount)
        assertEquals("A/B_C" to "Artist: D", LyricsRepository.identity(context, key))
        assertEquals("[00:01]hello", LyricsRepository.loadLyricsText(context, key))
        assertTrue(SettingsManager.getLyricsEnabled(context))
        assertFalse(File(internal, "pending-lyrics-restore").exists())
    }

    @Test fun publicationFailureRollsBackEarlierFiles() {
        File(directory, "a.lrt").writeText("old a")
        File(directory, "b.lrt").writeText("old b")
        val stage = folder.newFolder("stage")
        val first = File(stage, "a.lrt").apply { writeText("new a") }
        val second = File(stage, "b.lrt").apply { writeText("new b") }
        mockkObject(LyricsStorage)
        every { LyricsStorage.read(second) } throws IOException("simulated failure")
        try {
            BackupTransaction.commit(context, directory, listOf(first, second), null)
            fail("Expected publication failure")
        } catch (_: IOException) { }
        assertEquals("old a", File(directory, "a.lrt").readText())
        assertEquals("old b", File(directory, "b.lrt").readText())
        assertFalse(File(internal, "pending-lyrics-restore").exists())
    }

    @Test fun interruptedCommitIsRecoveredBeforeNextRead() {
        val transaction = File(internal, "pending-lyrics-restore").apply { mkdirs() }
        File(transaction, "original").mkdirs()
        File(transaction, "original/a.lrt").writeText("original")
        File(directory, "a.lrt").writeText("partial restore")
        val entries = JSONArray().put(JSONObject().put("name", "a.lrt").put("existed", true))
        File(transaction, "journal.json").writeText(
            JSONObject().put("directory", directory.canonicalPath).put("files", entries).toString()
        )
        LyricCache.getLyricsDir(context)
        assertEquals("original", File(directory, "a.lrt").readText())
        assertFalse(transaction.exists())
    }
}
