package com.gululu.aamediamate.lyrics

import android.content.Context
import com.gululu.aamediamate.models.LyricsEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

object LyricsRepository {
    const val ALL_LYRICS = "*"
    private val _lyricsUpdatedFlow = MutableSharedFlow<String>(
        extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val lyricsUpdatedFlow: SharedFlow<String> = _lyricsUpdatedFlow.asSharedFlow()

    fun keyFor(title: String, artist: String): String = LyricsStorage.keyFor(title, artist)

    suspend fun identity(context: Context, key: String): Pair<String, String> = withContext(Dispatchers.IO) {
        LyricsStorage.mutex.withLock { readIdentity(context, key) }
    }

    private fun readIdentity(context: Context, key: String): Pair<String, String> {
        val metadata = LyricsStorage.metadata(context, key)
        if (metadata.has("title") && metadata.has("artist")) return metadata.optString("title") to metadata.optString("artist")
        val parts = key.split("_", limit = 2)
        return parts[0] to parts.getOrElse(1) { "" }
    }

    suspend fun getAllLyrics(context: Context): List<LyricsEntry> = withContext(Dispatchers.IO) {
        LyricsStorage.mutex.withLock {
            LyricCache.getLyricsDir(context).listFiles().orEmpty()
                .filter { it.isFile && it.name.endsWith(".lrt") }
                .map { file ->
                    val key = file.name.removeSuffix(".lrt")
                    val (title, artist) = readIdentity(context, key)
                    LyricsEntry(key, title, artist, file.length() > 0, file.lastModified())
                }.sortedByDescending { it.lastModified }
        }
    }

    suspend fun deleteLyrics(context: Context, keys: List<String>) = withContext(Dispatchers.IO) {
        LyricsStorage.mutex.withLock {
            keys.forEach { key ->
                listOf("lrt", "json").forEach { extension ->
                    val file = LyricsStorage.file(context, key, extension)
                    if (file.exists() && !file.delete()) throw IOException("Could not delete lyrics")
                }
                LyricCache.clearMemoryCache(key)
            }
            LyricsStorage.revision++
        }
        _lyricsUpdatedFlow.tryEmit(ALL_LYRICS)
    }

    suspend fun loadLyricsText(context: Context, key: String): String = withContext(Dispatchers.IO) {
        LyricsStorage.mutex.withLock {
            val file = LyricsStorage.file(context, key)
            if (file.exists()) LyricsStorage.read(file) else ""
        }
    }

    suspend fun saveLyricsText(context: Context, key: String, content: String) = withContext(Dispatchers.IO) {
        LyricsStorage.mutex.withLock {
            val metadata = LyricsStorage.metadata(context, key).put("manual", true)
            LyricsStorage.write(LyricsStorage.file(context, key, "json"), metadata.toString())
            LyricsStorage.write(LyricsStorage.file(context, key), content)
            LyricCache.clearMemoryCache(key)
            LyricsStorage.revision++
        }
        _lyricsUpdatedFlow.tryEmit(ALL_LYRICS)
    }

    suspend fun notifyLyricsUpdated(key: String) {
        LyricsStorage.mutex.withLock {
            LyricCache.clearMemoryCache(key)
            LyricsStorage.revision++
        }
        _lyricsUpdatedFlow.tryEmit(ALL_LYRICS)
    }

    suspend fun shiftLyricsByMs(context: Context, keys: List<String>, deltaMs: Long) = withContext(Dispatchers.IO) {
        LyricsStorage.mutex.withLock {
            keys.forEach { key ->
                val file = LyricsStorage.file(context, key)
                if (file.exists()) {
                    LyricsStorage.write(file, LrcFormat.shift(LyricsStorage.read(file), deltaMs))
                    LyricCache.clearMemoryCache(key)
                }
            }
            LyricsStorage.revision++
        }
        _lyricsUpdatedFlow.tryEmit(ALL_LYRICS)
    }
}
