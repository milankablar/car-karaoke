package io.github.milankablar.carkaraoke.karaoke

import android.content.Context
import io.github.milankablar.carkaraoke.SettingsManager
import io.github.milankablar.carkaraoke.lyrics.LyricsCleanupManager
import io.github.milankablar.carkaraoke.lyrics.providers.fetchLyrics
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

interface CandidateProvider { suspend fun search(track: TrackIdentity): List<LyricCandidate> }

/** LRCLIB retains response identity for validation instead of returning an anonymous string. */
class LrcLibCandidates(private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS).build(), private val baseUrl: String = "https://lrclib.net/api") : CandidateProvider {
    override suspend fun search(track: TrackIdentity): List<LyricCandidate> {
        val candidates = linkedMapOf<String, LyricCandidate>()
        var failure: IOException? = null
        if (track.durationMs > 0) {
            try {
                request("get", track)?.let { parse(JSONObject(it)) }?.let { candidates[it.id] = it }
            } catch (e: IOException) { failure = e }
            if (LyricMatcher.select(track, candidates.values.toList()) != null) return candidates.values.toList()
        }
        try {
            request("search", track)?.let { body ->
                val array = JSONArray(body)
                for (i in 0 until array.length()) parse(array.getJSONObject(i))?.let { candidates[it.id] = it }
            }
        } catch (e: IOException) { failure = e }
        if (candidates.isEmpty() && failure != null) throw failure
        return candidates.values.toList()
    }
    private suspend fun request(path: String, track: TrackIdentity): String? {
        val url = HttpUrl.parse("$baseUrl/$path")!!.newBuilder().addQueryParameter("track_name", track.title)
        if (track.artist.isNotBlank()) url.addQueryParameter("artist_name", track.artist)
        if (path == "get") {
            if (track.album.isNotBlank()) url.addQueryParameter("album_name", track.album)
            url.addQueryParameter("duration", (track.durationMs / 1000.0).toString())
        }
        val result = client.fetchLyrics(Request.Builder().url(url.build()).header("User-Agent", "CarKaraoke/0.1 (https://github.com/milankablar/car-karaoke)").build())
        if (result.code == 404) return null
        if (result.code !in 200..299) throw IOException("Lyrics provider returned HTTP ${result.code}")
        return result.body
    }
    internal fun parse(json: JSONObject): LyricCandidate? {
        fun string(name: String) = if (json.isNull(name)) "" else json.optString(name, "")
        val content = string("syncedLyrics").ifBlank { string("plainLyrics") }
        val instrumental = json.optBoolean("instrumental")
        if (content.isBlank() && !instrumental) return null
        return LyricCandidate(string("id"), "LRCLIB", string("trackName"), string("artistName"), string("albumName"),
            (json.optDouble("duration", 0.0).takeIf { it.isFinite() && it >= 0 }?.times(1000)?.toLong() ?: 0), content, instrumental)
    }
}

class ConfiguredCandidates(private val context: Context, private val lrclib: CandidateProvider = LrcLibCandidates()) : CandidateProvider {
    override suspend fun search(track: TrackIdentity): List<LyricCandidate> {
        val results = mutableListOf<LyricCandidate>(); var failure: Exception? = null
        val (title, artist) = LyricsCleanupManager.applyRules(track.title, track.artist, SettingsManager.getLyricsCleanupRules(context))
        val query = track.copy(title = title, artist = artist)
        for (config in SettingsManager.getEnabledProvidersInOrder(context)) {
            try {
                if (config.id == "lrclib") results += lrclib.search(query)
                else {
                    val text = config.provider.getLyricsLrc(context, title, artist, track.durationMs.toString())
                    if (!text.isNullOrBlank()) results += LyricCandidate(config.id, config.name, title, artist, "", 0, text, verifiedIdentity = false)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure = e }
            if (LyricMatcher.select(track, results) != null) break
        }
        if (results.isEmpty() && failure != null) throw IOException("Lyrics lookup unavailable. Check your connection or provider settings.", failure)
        return results
    }
}
