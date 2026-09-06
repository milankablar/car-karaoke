package com.gululu.aamediamate.lyrics.providers

import android.content.Context
import android.util.Log
import com.gululu.aamediamate.diagnostics.DiagnosticLogger
import com.gululu.aamediamate.diagnostics.DiagnosticModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

object LRCLibProvider : LyricsProvider {
    private const val BASE_URL = "https://lrclib.net/api/get"
    private val client = OkHttpClient()

    override suspend fun getLyricsLrc(context: Context, title: String, artist: String, duration: String): String? = withContext(Dispatchers.IO) {
        try {
            val startedMs = System.currentTimeMillis()
            val trackName = URLEncoder.encode(title, "UTF-8")
            val artistName = URLEncoder.encode(artist, "UTF-8")
            
            val url = "$BASE_URL?track_name=$trackName&artist_name=$artistName"

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("User-Agent", "AAMediaMate/1.0")
                .build()

            Log.d("MediaBridge", "Sending request to LRCLib")
            client.fetchLyrics(request).let { response ->
                val body = response.body
                val details = mapOf(
                    "provider" to "lrclib",
                    "url" to url,
                    "status" to response.code,
                    "durationMs" to (System.currentTimeMillis() - startedMs),
                    "bodyBytes" to (body?.toByteArray()?.size ?: 0)
                )

                Log.d("MediaBridge", "LRCLib response code: ${response.code}")
                if (response.code != 200) {
                    if (response.code != 404) throw java.io.IOException("Lyrics HTTP error: ${response.code}")
                    DiagnosticLogger.warn(context, DiagnosticModule.NETWORK, "Lyrics request failed", details)
                    return@withContext null
                }

                if (body.isNullOrBlank()) {
                    DiagnosticLogger.info(context, DiagnosticModule.NETWORK, "Lyrics request returned empty body", details)
                    return@withContext null
                }

                // Parse JSON response and extract syncedLyrics
                val jsonObject = JSONObject(body)
                val syncedLyrics = jsonObject.optString("syncedLyrics", "")

                // Return synced lyrics if available and not the literal string "null", otherwise null
                if (syncedLyrics.isNotBlank() && syncedLyrics != "null") {
                    DiagnosticLogger.info(
                        context,
                        DiagnosticModule.NETWORK,
                        "Lyrics request succeeded",
                        details + mapOf("lineCount" to syncedLyrics.lineSequence().count())
                    )
                    syncedLyrics
                } else {
                    DiagnosticLogger.info(context, DiagnosticModule.NETWORK, "Lyrics response had no synced lyrics", details)
                    null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("MediaBridge", "Error fetching lyrics from LRCLib", e)
            DiagnosticLogger.error(
                context,
                DiagnosticModule.NETWORK,
                "Lyrics request threw exception",
                mapOf("provider" to "lrclib", "url" to BASE_URL),
                e
            )
            throw e
        }
    }
}
