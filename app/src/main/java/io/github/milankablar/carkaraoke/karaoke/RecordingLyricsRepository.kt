package io.github.milankablar.carkaraoke.karaoke

import android.content.Context
import io.github.milankablar.carkaraoke.lyrics.LyricsRepository
import io.github.milankablar.carkaraoke.lyrics.LyricsStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** Recording-scoped cache. Network answers never overwrite a concurrent user edit. */
class RecordingLyricsRepository(private val context: Context, private val provider: CandidateProvider = ConfiguredCandidates(context)) {
    private val misses = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private suspend fun cached(track: TrackIdentity): LyricResolution? = withContext(Dispatchers.IO) {
        LyricsStorage.mutex.withLock {
            val file = LyricsStorage.file(context, track.key)
            if (!file.exists()) return@withLock null
            val meta = LyricsStorage.metadata(context, track.key)
            val content = LyricsStorage.read(file)
            if (meta.optString("contentHash") != LyricsStorage.contentHash(content)) return@withLock null
            resolution(content, meta.optString("provider", "Saved"), meta.optBoolean("instrumental"), meta.optLong("offsetMs"), meta.optBoolean("manual"))
        }
    }
    suspend fun resolve(track: TrackIdentity, force: Boolean = false): LyricResolution {
        if (!io.github.milankablar.carkaraoke.SettingsManager.getLyricsEnabled(context) || !io.github.milankablar.carkaraoke.SettingsManager.isAppLyricsEnabled(context, track.source)) return LyricResolution(LyricsStatus.DISABLED)
        val stored = cached(track)
        io.github.milankablar.carkaraoke.diagnostics.DiagnosticLogger.debug(context, io.github.milankablar.carkaraoke.diagnostics.DiagnosticModule.LYRICS, "Recording lookup", mapOf("recordingKey" to track.key, "cached" to (stored != null), "manual" to (stored?.manual == true)))
        val age = withContext(Dispatchers.IO) { LyricsStorage.mutex.withLock { System.currentTimeMillis() - LyricsStorage.metadata(context, track.key).optLong("fetchedAt") } }
        if (stored != null && (stored.manual || (!force && age < 30L * 86400000))) return stored
        if (!force && (misses[track.key] ?: 0) > System.currentTimeMillis()) return LyricResolution(LyricsStatus.NOT_FOUND)
        val revision = LyricsStorage.mutex.withLock { LyricsStorage.revision }
        return try {
            val candidates = provider.search(track)
            val selected = LyricMatcher.select(track, candidates)
            io.github.milankablar.carkaraoke.diagnostics.DiagnosticLogger.debug(context, io.github.milankablar.carkaraoke.diagnostics.DiagnosticModule.LYRICS, "Candidate validation", mapOf("recordingKey" to track.key, "candidates" to candidates.size, "selectedProvider" to (selected?.provider ?: "none"), "selectedId" to (selected?.id ?: "none")))
            if (selected != null) {
                val saved = save(track, selected, false, revision)
                if (saved) resolution(selected.content, selected.provider, selected.instrumental) else cached(track) ?: LyricResolution(LyricsStatus.NOT_FOUND)
            } else if (stored != null) stored.copy(message = "Using saved lyrics")
            else {
                val legacy = LyricsRepository.loadLyricsText(context, LyricsRepository.keyFor(track.title, track.artist))
                val choices = candidates + if (legacy.isNotBlank()) listOf(LyricCandidate("legacy", "Previous library", track.title, track.artist, track.album, 0, legacy, verifiedIdentity = false)) else emptyList()
                if (choices.isEmpty()) misses[track.key] = System.currentTimeMillis() + 3600000
                LyricResolution(if (choices.isEmpty()) LyricsStatus.NOT_FOUND else LyricsStatus.NEEDS_SELECTION, candidates = choices)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { stored?.copy(message = "Offline • saved lyrics") ?: LyricResolution(LyricsStatus.ERROR, message = "Lyrics service unavailable. Check your connection and retry.") }
    }
    suspend fun choose(track: TrackIdentity, candidate: LyricCandidate) {
        save(track, candidate, true, null)
        LyricsRepository.notifyLyricsUpdated(track.key)
    }
    suspend fun offset(track: TrackIdentity, deltaMs: Long) = withContext(Dispatchers.IO) {
        LyricsStorage.mutex.withLock {
            val meta = LyricsStorage.metadata(context, track.key)
            LyricsStorage.write(LyricsStorage.file(context, track.key, "json"), meta.put("offsetMs", (meta.optLong("offsetMs") + deltaMs).coerceIn(-60000, 60000)).toString())
        }
        LyricsRepository.notifyLyricsUpdated(track.key)
    }
    private suspend fun save(track: TrackIdentity, candidate: LyricCandidate, manual: Boolean, expectedRevision: Long?): Boolean = withContext(Dispatchers.IO) {
        LyricsStorage.mutex.withLock {
            if (expectedRevision != null && expectedRevision != LyricsStorage.revision) return@withLock false
            val previous = LyricsStorage.metadata(context, track.key)
            val metadata = JSONObject().put("title", track.title).put("artist", track.artist).put("album", track.album)
                .put("source", track.source).put("mediaId", track.mediaId).put("durationMs", track.durationMs)
                .put("provider", candidate.provider).put("candidateId", candidate.id).put("manual", manual)
                .put("contentHash", LyricsStorage.contentHash(candidate.content)).put("instrumental", candidate.instrumental).put("offsetMs", previous.optLong("offsetMs")).put("fetchedAt", System.currentTimeMillis())
            // Persist metadata first; an interrupted write can only retain the previous lyrics for this same recording.
            LyricsStorage.write(LyricsStorage.file(context, track.key, "json"), metadata.toString())
            LyricsStorage.write(LyricsStorage.file(context, track.key), candidate.content)
            misses.remove(track.key)
            true
        }
    }
    private fun resolution(content: String, source: String, instrumental: Boolean, offset: Long = 0, manual: Boolean = false): LyricResolution {
        val document = EnhancedLrcParser.parse(content)
        return LyricResolution(when { instrumental -> LyricsStatus.INSTRUMENTAL; document.synced -> LyricsStatus.SYNCED; else -> LyricsStatus.PLAIN }, document, source = source, offsetMs = offset, manual = manual)
    }
}
