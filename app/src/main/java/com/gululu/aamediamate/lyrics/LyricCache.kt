package com.gululu.aamediamate.lyrics

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

object LyricCache {
    // Raw text keeps simplified/traditional conversion independent of the cache.
    private val memoryCache = object : LinkedHashMap<String, String>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 64
    }
    private const val MISS_TTL_MS = 60 * 60_000L

    fun getLyricsDir(context: Context): File {
        val dir = context.getExternalFilesDir("lyrics") ?: File(context.filesDir, "lyrics")
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Lyrics storage is unavailable")
        com.gululu.aamediamate.backup.BackupTransaction.recover(context, dir)
        return dir
    }

    /** Loads lyrics without holding the storage lock during network IO. */
    suspend fun getOrFetchLyrics(context: Context, title: String, artist: String, duration: String): List<LyricLine> =
        withContext(Dispatchers.IO) {
            val key = LyricsStorage.keyFor(title, artist)
            var revision = 0L
            val cached = LyricsStorage.mutex.withLock {
                val file = LyricsStorage.file(context, key)
                LyricsStorage.rememberIdentity(context, key, title, artist)
                val legacyKey = "${title}_${artist}".replace(Regex("""[\\/:*?"<>|]"""), "_")
                val legacy = LyricsStorage.file(context, legacyKey)
                if (!file.exists() && legacy.exists()) {
                    LyricsStorage.write(file, LyricsStorage.read(legacy))
                    legacy.delete()
                }
                revision = LyricsStorage.revision
                readCached(context, key, file)
            }
            if (cached != null) return@withContext LyricsManager.parseLrc(context, cached)

            val fetched = LyricsManager.getLyricsLrt(context, title, artist, duration)
            currentCoroutineContext().ensureActive()
            val content = LyricsStorage.mutex.withLock {
                currentCoroutineContext().ensureActive()
                val file = LyricsStorage.file(context, key)
                // An edit/delete/restore wins over an in-flight fetch.
                if (revision != LyricsStorage.revision) {
                    return@withLock if (file.exists()) LyricsStorage.read(file) else ""
                }
                val text = fetched.orEmpty()
                LyricsStorage.write(file, text)
                val metadata = LyricsStorage.metadata(context, key)
                metadata.remove("manual")
                metadata.put("retryAfter", if (text.isEmpty()) System.currentTimeMillis() + MISS_TTL_MS else 0L)
                LyricsStorage.write(LyricsStorage.file(context, key, "json"), metadata.toString())
                synchronized(memoryCache) { memoryCache[file.absolutePath] = text }
                text
            }
            LyricsManager.parseLrc(context, content)
        }

    private fun readCached(context: Context, key: String, file: File): String? {
        if (!file.exists()) return null
        val content = synchronized(memoryCache) { memoryCache[file.absolutePath] } ?: LyricsStorage.read(file)
        if (content.isEmpty()) {
            val metadata = LyricsStorage.metadata(context, key)
            if (!metadata.optBoolean("manual") && metadata.optLong("retryAfter") <= System.currentTimeMillis()) return null
        }
        synchronized(memoryCache) { memoryCache[file.absolutePath] = content }
        file.setLastModified(System.currentTimeMillis())
        return content
    }

    fun clearMemoryCache(key: String) {
        synchronized(memoryCache) { memoryCache.keys.removeAll { File(it).name == "$key.lrt" } }
    }

    fun clearAllMemoryCache() {
        synchronized(memoryCache) { memoryCache.clear() }
    }
}
