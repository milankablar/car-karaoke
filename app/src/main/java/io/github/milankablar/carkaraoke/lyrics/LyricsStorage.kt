package io.github.milankablar.carkaraoke.lyrics

import android.content.Context
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** File operations are called on IO while holding [mutex]. */
internal object LyricsStorage {
    val mutex = Mutex()
    var revision = 0L
    const val MAX_LYRICS_BYTES = 2 * 1024 * 1024

    fun contentHash(content: String): String = MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun keyFor(title: String, artist: String): String {
        val identity = JSONObject().put("title", title).put("artist", artist).toString()
        val hash = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        return "v2_" + hash.joinToString("") { "%02x".format(it) }
    }

    fun file(context: Context, key: String, extension: String = "lrt"): File {
        require(isSafeKey(key)) { "Invalid lyric key" }
        return File(LyricCache.getLyricsDir(context), "$key.$extension")
    }

    fun isSafeKey(key: String): Boolean =
        key.isNotBlank() && key != "." && key != ".." && !key.contains('/') && !key.contains('\\') &&
            !key.contains('\u0000')

    fun read(file: File): String {
        if (file.length() > MAX_LYRICS_BYTES) throw IOException("Lyrics file too large")
        return file.inputStream().bufferedReader(Charsets.UTF_8).use { reader ->
            val buffer = CharArray(4096)
            val result = StringBuilder()
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                if (result.length + count > MAX_LYRICS_BYTES) throw IOException("Lyrics file too large")
                result.append(buffer, 0, count)
            }
            result.toString()
        }
    }

    fun write(file: File, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_LYRICS_BYTES) { "Lyrics file too large" }
        if (file.parentFile?.isDirectory != true && file.parentFile?.mkdirs() != true) {
            throw IOException("Could not create lyrics directory")
        }
        val temporary = File.createTempFile("lyrics-", ".tmp", file.parentFile)
        try {
            temporary.outputStream().use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
    }

    fun metadata(context: Context, key: String): JSONObject {
        val file = file(context, key, "json")
        return if (file.exists()) runCatching { JSONObject(read(file)) }.getOrDefault(JSONObject()) else JSONObject()
    }

    fun rememberIdentity(context: Context, key: String, title: String, artist: String) {
        val file = file(context, key, "json")
        val metadata = metadata(context, key)
        if (metadata.optString("title") == title && metadata.optString("artist") == artist) return
        write(file, metadata.put("title", title).put("artist", artist).toString())
    }
}
