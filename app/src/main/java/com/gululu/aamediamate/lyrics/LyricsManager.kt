package com.gululu.aamediamate.lyrics

import android.content.Context
import com.github.houbb.opencc4j.util.ZhConverterUtil
import com.gululu.aamediamate.SettingsManager
import com.gululu.aamediamate.diagnostics.DiagnosticLogger
import com.gululu.aamediamate.diagnostics.DiagnosticModule
import com.gululu.aamediamate.lyrics.providers.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

data class LyricLine(val timeSec: Float, val text: String)

object LyricsManager {
    suspend fun getLyricsLrt(context: Context, title: String, artist: String, duration: String): String? = withContext(Dispatchers.IO) {
        val cleanupRules = SettingsManager.getLyricsCleanupRules(context)
        val (cleanedTitle, cleanedArtist) = LyricsCleanupManager.applyRules(title, artist, cleanupRules)

        // Get enabled providers in order of priority
        val enabledProviders = SettingsManager.getEnabledProvidersInOrder(context)
        DiagnosticLogger.info(
            context,
            DiagnosticModule.LYRICS,
            "Lyric provider search started",
            mapOf(
                "title" to title,
                "artist" to artist,
                "cleanedTitle" to cleanedTitle,
                "cleanedArtist" to cleanedArtist,
                "providers" to enabledProviders.joinToString(",") { it.id }
            )
        )
        
        var lastFailure: Exception? = null
        for (providerConfig in enabledProviders) {
            try {
                val lrc = providerConfig.provider.getLyricsLrc(context, cleanedTitle, cleanedArtist, duration)
                if (!lrc.isNullOrBlank() && parseLrc(context, lrc).isNotEmpty()) {
                    DiagnosticLogger.info(
                        context,
                        DiagnosticModule.LYRICS,
                        "Lyric provider returned lyrics",
                        mapOf(
                            "provider" to providerConfig.id,
                            "bytes" to lrc.toByteArray().size,
                            "lineCount" to lrc.lineSequence().count()
                        )
                    )
                    return@withContext lrc
                }
                DiagnosticLogger.info(
                    context,
                    DiagnosticModule.LYRICS,
                    "Lyric provider returned no lyrics",
                    mapOf("provider" to providerConfig.id)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastFailure = e
                DiagnosticLogger.error(
                    context,
                    DiagnosticModule.LYRICS,
                    "Lyric provider failed",
                    mapOf("provider" to providerConfig.id),
                    e
                )
            }
        }
        DiagnosticLogger.warn(
            context,
            DiagnosticModule.LYRICS,
            "All lyric providers returned no lyrics",
            mapOf("title" to cleanedTitle, "artist" to cleanedArtist)
        )
        if (lastFailure != null) throw java.io.IOException("Lyrics providers unavailable", lastFailure)
        return@withContext null
    }

    fun parseLrc(context: Context, lrc: String): List<LyricLine> {
        val simplify = SettingsManager.getSimplifyEnabled(context)
        return lrc.lineSequence().flatMap { line ->
            val tags = LrcFormat.timestamp.findAll(line).toList()
            if (tags.isEmpty() || tags.first().range.first != 0) return@flatMap emptySequence()
            // Only consecutive leading timestamps belong to this line.
            val leading = tags.takeLeadingTimestamps()
            val text = line.substring(leading.last().range.last + 1).trim()
            val converted = if (simplify) ZhConverterUtil.toSimple(text) else ZhConverterUtil.toTraditional(text)
            leading.asSequence().mapNotNull { tag ->
                LrcFormat.timeMs(tag)?.let { LyricLine(it / 1000f, converted) }
            }
        }.sortedBy { it.timeSec }.toList()
    }

    private fun List<MatchResult>.takeLeadingTimestamps(): List<MatchResult> {
        var end = 0
        return takeWhile { match ->
            (match.range.first == end).also { if (it) end = match.range.last + 1 }
        }
    }
}
