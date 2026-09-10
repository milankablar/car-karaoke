package io.github.milankablar.carkaraoke.lyrics.providers

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class LyricsHttpResponse(val code: Int, val body: String?)

/** Cancelling the lyric job also cancels its HTTP call. */
internal suspend fun OkHttpClient.fetchLyrics(request: Request): LyricsHttpResponse = suspendCancellableCoroutine { continuation ->
    val call = newCall(request)
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                val result = response.use {
                    val body = it.body()
                    if ((body?.contentLength() ?: 0) > 2 * 1024 * 1024) throw IOException("Lyrics response too large")
                    val text = body?.charStream()?.use { reader ->
                        val buffer = CharArray(4096)
                        val output = StringBuilder()
                        while (true) {
                            val count = reader.read(buffer)
                            if (count < 0) break
                            if (output.length + count > 2 * 1024 * 1024) throw IOException("Lyrics response too large")
                            output.append(buffer, 0, count)
                        }
                        output.toString()
                    }
                    LyricsHttpResponse(it.code(), text)
                }
                continuation.resume(result)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    })
}
