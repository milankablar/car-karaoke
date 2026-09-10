package io.github.milankablar.carkaraoke.karaoke

import android.content.Context
import org.robolectric.RuntimeEnvironment
import io.github.milankablar.carkaraoke.lyrics.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.json.JSONObject
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class RecordingRepositoryTest {
    private lateinit var context: Context
    private val track = TrackIdentity("music", "id", "Song", "Artist", "Album", 180000)
    private val candidate = LyricCandidate("1", "LRCLIB", "Song", "Artist", "Album", 180000, "[00:01]First")
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        LyricCache.getLyricsDir(context).deleteRecursively()
    }
    @Test fun `manual selection remains pinned through forced refresh and keeps offset`() = runTest {
        var requests = 0
        val repo = RecordingLyricsRepository(context, object : CandidateProvider { override suspend fun search(track: TrackIdentity): List<LyricCandidate> { requests++; return listOf(candidate) } })
        repo.choose(track, candidate.copy(content = "[00:03]My correction"))
        repo.offset(track, 250)
        val result = repo.resolve(track, true)
        assertTrue(result.manual); assertEquals(250, result.offsetMs); assertEquals("My correction", result.document.lines.single().text); assertEquals(0, requests)
    }
    @Test fun `offline uses validated saved lyrics and failure is not negative cached`() = runTest {
        var fail = false; var requests = 0
        val repo = RecordingLyricsRepository(context, object : CandidateProvider { override suspend fun search(track: TrackIdentity): List<LyricCandidate> { requests++; if (fail) throw IOException("offline"); return listOf(candidate) } })
        assertEquals(LyricsStatus.SYNCED, repo.resolve(track).status)
        fail = true
        assertEquals(LyricsStatus.SYNCED, repo.resolve(track, true).status)
        val other = track.copy(mediaId = "other")
        assertEquals(LyricsStatus.ERROR, repo.resolve(other).status)
        assertEquals(LyricsStatus.ERROR, repo.resolve(other).status)
        assertEquals(4, requests)
    }
    @Test fun `late lookup cannot overwrite editor save`() = runTest {
        val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val repo = RecordingLyricsRepository(context, object : CandidateProvider { override suspend fun search(track: TrackIdentity): List<LyricCandidate> { started.complete(Unit); finish.await(); return listOf(candidate) } })
        repo.choose(track, candidate)
        // Turn the initial cached entry into an old automatic result.
        val meta = LyricsStorage.metadata(context, track.key).put("manual", false).put("fetchedAt", 0)
        LyricsStorage.write(LyricsStorage.file(context, track.key, "json"), meta.toString())
        val lookup = async { repo.resolve(track, true) }; started.await()
        LyricsRepository.saveLyricsText(context, track.key, "[00:01]Edited")
        finish.complete(Unit)
        assertEquals("Edited", lookup.await().document.lines.single().text)
        assertEquals("Edited", repo.resolve(track).document.lines.single().text)
    }
    @Test fun `legacy cache requires explicit confirmation`() = runTest {
        LyricsRepository.saveLyricsText(context, LyricsRepository.keyFor(track.title, track.artist), "[00:01]Legacy")
        val repo = RecordingLyricsRepository(context, object : CandidateProvider { override suspend fun search(track: TrackIdentity) = emptyList<LyricCandidate>() })
        val result = repo.resolve(track)
        assertEquals(LyricsStatus.NEEDS_SELECTION, result.status); assertFalse(result.candidates.single().verifiedIdentity)
    }
    @Test fun `LRCLIB parses null synced text plain and instrumental without string null`() {
        val provider = LrcLibCandidates()
        val json = JSONObject().put("id", 1).put("trackName", "Song").put("artistName", "Artist").put("duration", 180).put("syncedLyrics", JSONObject.NULL).put("plainLyrics", "Plain")
        assertEquals("Plain", provider.parse(json)!!.content)
        json.put("plainLyrics", JSONObject.NULL).put("instrumental", true)
        assertTrue(provider.parse(json)!!.instrumental); assertEquals("", provider.parse(json)!!.content)
    }
}
