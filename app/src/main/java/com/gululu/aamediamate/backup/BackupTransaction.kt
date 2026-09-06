package com.gululu.aamediamate.backup

import android.content.Context
import com.gululu.aamediamate.SettingsManager
import com.gululu.aamediamate.lyrics.LyricCache
import com.gululu.aamediamate.lyrics.LyricsStorage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Journaled restore: a failed or interrupted commit is rolled back before the next storage access. */
internal object BackupTransaction {
    private const val DIRECTORY = "pending-lyrics-restore"

    @Synchronized
    fun recover(context: Context, lyricsDir: File) {
        val transaction = File(context.filesDir, DIRECTORY)
        if (!transaction.exists()) return
        val journalFile = File(transaction, "journal.json")
        if (journalFile.exists() && !File(transaction, "committed").exists()) {
            val journal = JSONObject(LyricsStorage.read(journalFile))
            check(journal.getString("directory") == lyricsDir.canonicalPath) { "Original lyrics storage is unavailable" }
            val files = journal.getJSONArray("files")
            for (index in 0 until files.length()) {
                val entry = files.getJSONObject(index)
                val name = entry.getString("name")
                require(isSafeName(name))
                val target = File(lyricsDir, name)
                if (entry.getBoolean("existed")) {
                    LyricsStorage.write(target, LyricsStorage.read(File(transaction, "original/$name")))
                } else if (target.exists() && !target.delete()) {
                    throw IOException("Could not roll back lyrics")
                }
            }
            journal.optJSONObject("settings")?.let { SettingsManager.restoreBackupSnapshot(context, it) }
            LyricCache.clearAllMemoryCache()
            LyricsStorage.revision++
        }
        if (!transaction.deleteRecursively()) throw IOException("Could not remove restore journal")
    }

    @Synchronized
    fun commit(context: Context, lyricsDir: File, staged: List<File>, settings: JSONObject?) {
        recover(context, lyricsDir)
        val transaction = File(context.filesDir, DIRECTORY)
        val originals = File(transaction, "original")
        check(originals.mkdirs()) { "Could not prepare restore transaction" }
        val files = JSONArray()
        try {
            staged.forEach { source ->
                require(isSafeName(source.name))
                val target = File(lyricsDir, source.name)
                files.put(JSONObject().put("name", source.name).put("existed", target.exists()))
                if (target.exists()) LyricsStorage.write(File(originals, source.name), LyricsStorage.read(target))
            }
            val journal = JSONObject().put("directory", lyricsDir.canonicalPath).put("files", files)
            if (settings != null) journal.put("settings", SettingsManager.backupSnapshot(context))
            LyricsStorage.write(File(transaction, "journal.json"), journal.toString())
            staged.forEach { source -> LyricsStorage.write(File(lyricsDir, source.name), LyricsStorage.read(source)) }
            settings?.let { SettingsManager.importBackupSettings(context, it) }
            LyricsStorage.write(File(transaction, "committed"), "done")
            LyricCache.clearAllMemoryCache()
            LyricsStorage.revision++
        } catch (e: Exception) {
            try { recover(context, lyricsDir) } catch (rollback: Exception) { e.addSuppressed(rollback) }
            throw e
        }
        // Committed data is valid even if cleanup must be retried on the next access.
        transaction.deleteRecursively()
    }

    fun isSafeName(name: String): Boolean =
        LyricsStorage.isSafeKey(name) && (name.endsWith(".lrt") || name.endsWith(".json"))
}
