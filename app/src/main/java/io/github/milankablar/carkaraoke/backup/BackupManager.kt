package io.github.milankablar.carkaraoke.backup

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import io.github.milankablar.carkaraoke.SettingsManager
import io.github.milankablar.carkaraoke.lyrics.LyricCache
import io.github.milankablar.carkaraoke.lyrics.LyricsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import io.github.milankablar.carkaraoke.lyrics.LyricsStorage
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupExportResult(
    val lyricsFileCount: Int,
    val includesSettings: Boolean
)

data class BackupPreview(
    val createdAt: Long,
    val appVersionName: String,
    val includesLyrics: Boolean,
    val includesSettings: Boolean,
    val includesSecrets: Boolean,
    val lyricsFileCount: Int
)

data class BackupRestoreResult(
    val restoredLyricsCount: Int,
    val restoredSettings: Boolean
)

object BackupManager {
    private const val FORMAT_VERSION = 1
    private const val MANIFEST_ENTRY = "manifest.json"
    private const val SETTINGS_ENTRY = "settings.json"
    private const val LYRICS_PREFIX = "lyrics/"

    fun createDefaultBackupFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        return "AAMediaMate-backup-$timestamp.zip"
    }

    suspend fun createBackup(
        context: Context,
        outputUri: Uri,
        includeLyrics: Boolean,
        includeSettings: Boolean,
        includeSecrets: Boolean
    ): BackupExportResult = withContext(Dispatchers.IO) {
        LyricsStorage.mutex.withLock {
            val lyricsFiles = if (includeLyrics) getNonEmptyLyricsFiles(context) else emptyList()
            val settingsJson = if (includeSettings) {
                SettingsManager.exportBackupSettings(context, includeSecrets)
            } else {
                null
            }
            val manifest = JSONObject().apply {
                put("formatVersion", FORMAT_VERSION)
                put("createdAt", System.currentTimeMillis())
                put("appVersionName", getAppVersionName(context))
                put("includesLyrics", includeLyrics)
                put("includesSettings", includeSettings)
                put("includesSecrets", includeSettings && includeSecrets)
                put("lyricsFileCount", lyricsFiles.size)
            }

            val outputStream = context.contentResolver.openOutputStream(outputUri)
                ?: throw IOException("Could not open backup output file")

            ZipOutputStream(BufferedOutputStream(outputStream)).use { zip ->
                zip.writeJsonEntry(MANIFEST_ENTRY, manifest)

                if (settingsJson != null) {
                    zip.writeJsonEntry(SETTINGS_ENTRY, settingsJson)
                }

                lyricsFiles.flatMap { file ->
                    val metadata = File(file.parentFile, file.name.removeSuffix(".lrt") + ".json")
                    if (metadata.exists()) listOf(file, metadata) else listOf(file)
                }.forEach { file ->
                    zip.putNextEntry(ZipEntry("$LYRICS_PREFIX${file.name}"))
                    file.inputStream().use { input ->
                        input.copyTo(zip)
                    }
                    zip.closeEntry()
                }
            }

            BackupExportResult(
                lyricsFileCount = lyricsFiles.size,
                includesSettings = includeSettings
            )
        }
    }

    suspend fun readBackupPreview(context: Context, inputUri: Uri): BackupPreview = withContext(Dispatchers.IO) {
        val manifest = readManifest(context, inputUri)
        val formatVersion = manifest.optInt("formatVersion", -1)
        if (formatVersion != FORMAT_VERSION) {
            throw IOException("Unsupported backup format")
        }

        BackupPreview(
            createdAt = manifest.optLong("createdAt", 0L),
            appVersionName = manifest.optString("appVersionName", ""),
            includesLyrics = manifest.optBoolean("includesLyrics", false),
            includesSettings = manifest.optBoolean("includesSettings", false),
            includesSecrets = manifest.optBoolean("includesSecrets", false),
            lyricsFileCount = manifest.optInt("lyricsFileCount", 0)
        )
    }

    suspend fun restoreBackup(
        context: Context,
        inputUri: Uri,
        restoreLyrics: Boolean,
        restoreSettings: Boolean
    ): BackupRestoreResult = withContext(Dispatchers.IO) {
        require(restoreLyrics || restoreSettings) { "No restore content selected" }
        val staging = File.createTempFile("lyrics-restore-", "", context.cacheDir)
        check(staging.delete() && staging.mkdirs())
        try {
            var manifest: JSONObject? = null
            var settings: JSONObject? = null
            val names = mutableSetOf<String>()
            var totalBytes = 0L
            var entryCount = 0
            val input = context.contentResolver.openInputStream(inputUri)
                ?: throw IOException("Could not open backup input file")
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    currentCoroutineContext().ensureActive()
                    if (++entryCount > 10_000) throw IOException("Too many backup entries")
                    val bytes = zip.readCurrentEntryBytes()
                    totalBytes += bytes.size
                    if (totalBytes > 100L * 1024 * 1024) throw IOException("Backup is too large")
                    if (!entry.isDirectory) {
                        if (!names.add(entry.name)) throw IOException("Duplicate backup entry")
                        when {
                            entry.name == MANIFEST_ENTRY -> manifest = JSONObject(bytes.toString(Charsets.UTF_8))
                            entry.name == SETTINGS_ENTRY -> settings = SettingsManager.validateBackupSettings(JSONObject(bytes.toString(Charsets.UTF_8)))
                            entry.name.startsWith(LYRICS_PREFIX) -> {
                                val name = entry.name.removePrefix(LYRICS_PREFIX)
                                if (!BackupTransaction.isSafeName(name)) throw IOException("Unsafe lyrics file name")
                                if (name.endsWith(".json")) {
                                    val metadata = JSONObject(bytes.toString(Charsets.UTF_8))
                                    if (name.startsWith("v3_")) {
                                        val track = io.github.milankablar.carkaraoke.karaoke.TrackIdentity(
                                            metadata.getString("source"), if (metadata.isNull("mediaId")) null else metadata.optString("mediaId"),
                                            metadata.getString("title"), metadata.getString("artist"), metadata.getString("album"), metadata.getLong("durationMs"))
                                        require(name == "${track.key}.json") { "Invalid recording identity" }
                                        require(metadata.optLong("offsetMs") in -60000L..60000L) { "Invalid lyric offset" }
                                    }
                                    if (metadata.has("title") || metadata.has("artist")) {
                                        val key = LyricsRepository.keyFor(metadata.getString("title"), metadata.getString("artist"))
                                        if (name.startsWith("v2_")) require(name == "$key.json") { "Invalid lyric identity" }
                                    }
                                }
                                LyricsStorage.write(File(staging, name), bytes.toString(Charsets.UTF_8))
                            }
                            else -> throw IOException("Unexpected backup entry")
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            val descriptor = manifest ?: throw IOException("Missing backup manifest")
            if (descriptor.optInt("formatVersion", -1) != FORMAT_VERSION) throw IOException("Unsupported backup format")
            val stagedFiles = staging.listFiles().orEmpty().toList()
            val lyricCount = stagedFiles.count { it.name.endsWith(".lrt") }
            if (descriptor.optInt("lyricsFileCount", -1) != lyricCount) throw IOException("Incomplete backup")
            if (restoreSettings && settings == null) throw IOException("Missing backup settings")
            stagedFiles.filter { it.name.endsWith(".json") }.forEach {
                if (!File(staging, it.name.removeSuffix(".json") + ".lrt").exists()) throw IOException("Orphan lyric identity")
            }
            currentCoroutineContext().ensureActive()
            // Once publication begins, complete it or roll it back even if the screen is closed.
            withContext(NonCancellable) {
                LyricsStorage.mutex.withLock {
                    BackupTransaction.commit(
                        context, LyricCache.getLyricsDir(context),
                        if (restoreLyrics) stagedFiles else emptyList(), if (restoreSettings) settings else null
                    )
                }
                LyricsRepository.notifyLyricsUpdated(LyricsRepository.ALL_LYRICS)
            }
            BackupRestoreResult(if (restoreLyrics) lyricCount else 0, restoreSettings)
        } finally {
            staging.deleteRecursively()
        }
    }

    private suspend fun readManifest(context: Context, inputUri: Uri): JSONObject {
        val inputStream = context.contentResolver.openInputStream(inputUri)
            ?: throw IOException("Could not open backup input file")

        ZipInputStream(BufferedInputStream(inputStream)).use { zip ->
            var entry = zip.nextEntry
            var entryCount = 0
            var totalBytes = 0L
            while (entry != null) {
                currentCoroutineContext().ensureActive()
                if (++entryCount > 10_000) throw IOException("Too many backup entries")
                val bytes = zip.readCurrentEntryBytes()
                totalBytes += bytes.size
                if (totalBytes > 100L * 1024 * 1024) throw IOException("Backup is too large")
                if (!entry.isDirectory && entry.name == MANIFEST_ENTRY) {
                    return JSONObject(bytes.toString(Charsets.UTF_8))
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        throw IOException("Missing backup manifest")
    }

    private fun getNonEmptyLyricsFiles(context: Context): List<File> {
        val lyricsDir = LyricCache.getLyricsDir(context)
        return lyricsDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".lrt") && it.length() > 0L }
            ?.sortedBy { it.name.lowercase(Locale.US) }
            ?: emptyList()
    }

    private fun getAppVersionName(context: Context): String {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        return packageInfo.versionName ?: ""
    }

    private fun ZipOutputStream.writeJsonEntry(name: String, json: JSONObject) {
        putNextEntry(ZipEntry(name))
        write(json.toString(2).toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun ZipInputStream.readCurrentEntryBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            if (output.size() + count > LyricsStorage.MAX_LYRICS_BYTES) throw IOException("Backup entry is too large")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
