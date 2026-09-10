package io.github.milankablar.carkaraoke.lyrics.providers

import android.content.Context
import android.util.Log
import io.github.milankablar.carkaraoke.SettingsManager
import io.github.milankablar.carkaraoke.diagnostics.DiagnosticLogger
import io.github.milankablar.carkaraoke.diagnostics.DiagnosticModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

object LrcApiProvider : LyricsProvider {
    private val client = OkHttpClient()

    override suspend fun getLyricsLrc(context: Context, title: String, artist: String, duration: String): String? = withContext(Dispatchers.IO) {
        val baseUrl = SettingsManager.getLrcApiBaseUri(context);
        if (baseUrl.isEmpty())
        {
            DiagnosticLogger.warn(context, DiagnosticModule.LYRICS, "LRC API base URL is not configured")
            return@withContext null
        }

        try {
            val startedMs = System.currentTimeMillis()
            val t = URLEncoder.encode(title, "UTF-8")
            val a = URLEncoder.encode(artist, "UTF-8")

            val url = "$baseUrl?title=$t&artist=$a"

            val authToken = SettingsManager.getLrcApiAuthToken(context)
            val request = Request.Builder()
                .url(url)
                .get()
                .apply { if (authToken.isNotEmpty()) addHeader("Authorization", authToken) }
                .build()

            Log.d("MediaBridge", "Sending request to Lrc Api")
            client.fetchLyrics(request).let { response ->
                val body = response.body
                val details = mapOf(
                    "provider" to "lrc_api",
                    "url" to url,
                    "status" to response.code,
                    "durationMs" to (System.currentTimeMillis() - startedMs),
                    "bodyBytes" to (body?.toByteArray()?.size ?: 0)
                )
                Log.d("MediaBridge", "Lrc Api response code: ${response.code}")

                if (response.code != 200) {
                    if (response.code != 404) throw java.io.IOException("Lyrics HTTP error: ${response.code}")
                    DiagnosticLogger.warn(context, DiagnosticModule.NETWORK, "Lyrics request failed", details)
                    return@withContext null
                }

                if (body.isNullOrBlank()) {
                    DiagnosticLogger.info(context, DiagnosticModule.NETWORK, "Lyrics request returned empty body", details)
                    return@withContext null
                }

                DiagnosticLogger.info(
                    context,
                    DiagnosticModule.NETWORK,
                    "Lyrics request succeeded",
                    details + mapOf("lineCount" to body.lineSequence().count())
                )
                body
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagnosticLogger.error(
                context,
                DiagnosticModule.NETWORK,
                "Lyrics request threw exception",
                mapOf("provider" to "lrc_api", "url" to baseUrl),
                e
            )
            throw e
        }
    }
}
