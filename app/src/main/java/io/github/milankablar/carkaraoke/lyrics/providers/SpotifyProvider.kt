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
import org.json.JSONObject
import java.net.URLEncoder

object SpotifyProvider : LyricsProvider {
    private const val BASE_URL = "https://spotify-web-api3.p.rapidapi.com/v1/social/spotify/musixmatchsearchlyrics"

    private val client = OkHttpClient()

    override suspend fun getLyricsLrc(context: Context, title: String, artist: String, duration: String): String? = withContext(Dispatchers.IO) {
        val apiKey = SettingsManager.getApiKey(context)
        if (apiKey.isEmpty())
        {
            DiagnosticLogger.warn(context, DiagnosticModule.LYRICS, "Spotify API key is not configured")
            return@withContext null
        }

        try {
            val startedMs = System.currentTimeMillis()
            val t = URLEncoder.encode(title, "UTF-8")
            val a = URLEncoder.encode(artist, "UTF-8")
            val url = "$BASE_URL?terms=$t&artist=$a"

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("x-rapidapi-host", "spotify-web-api3.p.rapidapi.com")
                .addHeader("x-rapidapi-key", apiKey)
                .build()
            Log.d("MediaBridge", "Sending request to spotify")

            client.fetchLyrics(request).let { response ->
                val body = response.body
                val details = mapOf(
                    "provider" to "Spotify",
                    "url" to url,
                    "status" to response.code,
                    "durationMs" to (System.currentTimeMillis() - startedMs),
                    "bodyBytes" to (body?.toByteArray()?.size ?: 0)
                )

                Log.d("MediaBridge", "Spotify response code: ${response.code}")
                if (response.code != 200) {
                    if (response.code != 404) throw java.io.IOException("Lyrics HTTP error: ${response.code}")
                    DiagnosticLogger.warn(context, DiagnosticModule.NETWORK, "Lyrics request failed", details)
                    return@withContext null
                }

                if (body.isNullOrBlank()) {
                    DiagnosticLogger.info(context, DiagnosticModule.NETWORK, "Lyrics request returned empty body", details)
                    return@withContext null
                }

                // Parse JSON and extract data array
                val jsonObject = JSONObject(body)
                val dataArray = jsonObject.optJSONArray("data")

                if (dataArray == null || dataArray.length() == 0) {
                    DiagnosticLogger.info(context, DiagnosticModule.NETWORK, "Lyrics response had no data", details)
                    return@withContext null
                }

                // Extract lyrics lines and join with newlines
                val lyricsLines = mutableListOf<String>()
                for (i in 0 until dataArray.length()) {
                    val line = dataArray.optString(i)
                    if (line.isNotBlank()) {
                        lyricsLines.add(line)
                    }
                }

                val lyrics = lyricsLines.joinToString("\n").takeIf { it.isNotBlank() }
                if (lyrics == null) {
                    DiagnosticLogger.info(context, DiagnosticModule.NETWORK, "Lyrics response had no usable lines", details)
                } else {
                    DiagnosticLogger.info(
                        context,
                        DiagnosticModule.NETWORK,
                        "Lyrics request succeeded",
                        details + mapOf("lineCount" to lyricsLines.size)
                    )
                }
                return@withContext lyrics
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagnosticLogger.error(
                context,
                DiagnosticModule.NETWORK,
                "Lyrics request threw exception",
                mapOf("provider" to "Spotify", "url" to BASE_URL),
                e
            )
            throw e
        }
    }
}
