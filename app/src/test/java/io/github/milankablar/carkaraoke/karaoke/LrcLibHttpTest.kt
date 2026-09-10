package io.github.milankablar.carkaraoke.karaoke

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class LrcLibHttpTest {
    private val server = MockWebServer()
    private val client = OkHttpClient()
    private val track = TrackIdentity("player", "id", "Song", "Artist", "Album", 180000)
    @Before fun start() = server.start()
    @After fun stop() = server.shutdown()
    @Test fun `exact lookup keeps identity and structured query`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":1,"trackName":"Song","artistName":"Artist","albumName":"Album","duration":180,"syncedLyrics":"[00:01]Hello"}"""))
        val result = LrcLibCandidates(client, server.url("/api").toString()).search(track)
        assertEquals("Song", result.single().title)
        assertNotNull(LyricMatcher.select(track, result))
        val request = server.takeRequest()
        assertEquals("Album", request.requestUrl!!.queryParameter("album_name"))
        assertEquals("180.0", request.requestUrl!!.queryParameter("duration"))
    }
    @Test fun `wrong exact result is not accepted and search retains alternatives`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":1,"trackName":"Other","artistName":"Artist","duration":180,"syncedLyrics":"[00:01]Wrong"}"""))
        server.enqueue(MockResponse().setBody("[]"))
        val result = LrcLibCandidates(client, server.url("/api").toString()).search(track)
        assertNull(LyricMatcher.select(track, result)); assertEquals(2, server.requestCount)
    }
    @Test fun `cancellation stops the underlying HTTP request`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}").setBodyDelay(1, TimeUnit.SECONDS))
        val job = launch(Dispatchers.Default) { LrcLibCandidates(client, server.url("/api").toString()).search(track) }
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) })
        job.cancelAndJoin()
        withTimeout(3000) { while (client.dispatcher().runningCallsCount() > 0) delay(25) }
        assertTrue(job.isCancelled)
    }
}
