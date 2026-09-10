package io.github.milankablar.carkaraoke

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.milankablar.carkaraoke.karaoke.*
import io.github.milankablar.carkaraoke.models.MediaInfo
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class KaraokeUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val info = MediaInfo("fixture.music", "Test player", "Open road", "Car Karaoke", "Original test lyrics", 120000, 4500, false, null, null, retrievedAtElapsedRealtimeMs = 1, mediaId = "fixture-1")
    private fun seed() {
        compose.activity.getSharedPreferences("presentation", 0).edit().putString("theme", "Light").commit()
        runBlocking { io.github.milankablar.carkaraoke.lyrics.LyricsRepository.deleteLyrics(compose.activity, listOf(TrackIdentity.from(info).key)); KaraokeRuntime.repository.choose(TrackIdentity.from(info), LyricCandidate("fixture", "Test fixture", info.title, info.artist, info.album, info.duration, "[00:00]A little light on the horizon\n[00:04]Every mile becomes a melody\n[00:08]Let the open road sing along\n[00:12]We carry the music home")) }
        compose.runOnIdle { MediaBridgeSessionManager.updateFromMediaInfo(info) }
        compose.waitUntil(10000) { KaraokeRuntime.coordinator.state.value.resolution.status == LyricsStatus.SYNCED }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(350)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap -> File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
    }
    @Test fun sharedLyricsAndCleanSettings() {
        compose.activity.getSharedPreferences("presentation", 0).edit().putString("theme", "Light").commit()
        compose.activityRule.scenario.recreate()
        seed()
        compose.onNodeWithText("Every mile becomes a melody").assertIsDisplayed()
        val state = KaraokeRuntime.coordinator.state.value
        assertEquals("Every mile becomes a melody", CarLyricsPresenter.window(state, 0).single { it.first }.second)
        capture("karaoke-light")
        compose.onNodeWithText("Settings", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Make it yours").assertIsDisplayed()
        capture("settings-light")
        compose.onNodeWithText("Dark", useUnmergedTree = true).performClick()
        capture("settings-dark")
        compose.onNodeWithText("Karaoke", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Every mile becomes a melody").assertIsDisplayed()
        capture("karaoke-dark")
        compose.onNodeWithText("Correct lyrics").performClick()
        compose.onNodeWithText("Later +250").performClick()
        compose.waitUntil(5000) { KaraokeRuntime.coordinator.state.value.resolution.offsetMs == 250L }
        assertEquals(250L, KaraokeRuntime.coordinator.state.value.resolution.offsetMs)
        capture("timing")
    }
    @Test fun landscapeAndLifecycleOwnership() {
        seed()
        compose.runOnIdle { MediaBridgeSessionManager.acquire(compose.activity, "car-test") }
        compose.runOnIdle { MediaBridgeSessionManager.updateFromMediaInfo(info) }
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        assertNotNull(KaraokeRuntime.coordinator.state.value.track)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        seed()
        compose.runOnIdle { compose.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        seed()
        compose.onNodeWithText("Every mile becomes a melody").assertIsDisplayed()
        capture("karaoke-landscape")
        compose.runOnIdle { MediaBridgeSessionManager.relinquish("car-test") }
        assertNotNull(KaraokeRuntime.coordinator.state.value.track)
    }
    @Test fun browserServicePublishesSharedWindow() {
        val connected = java.util.concurrent.CountDownLatch(1)
        lateinit var browser: android.support.v4.media.MediaBrowserCompat
        compose.runOnIdle {
            browser = android.support.v4.media.MediaBrowserCompat(compose.activity,
                android.content.ComponentName(compose.activity, MediaBridgeService::class.java),
                object : android.support.v4.media.MediaBrowserCompat.ConnectionCallback() {
                    override fun onConnected() { connected.countDown() }
                }, null)
            browser.connect()
        }
        try {
            assertTrue("Media browser connection", connected.await(10, java.util.concurrent.TimeUnit.SECONDS))
            seed()
            val received = java.util.concurrent.CountDownLatch(1)
            val titles = java.util.concurrent.atomic.AtomicReference<List<String>>()
            compose.runOnIdle {
                browser.subscribe("lyrics", object : android.support.v4.media.MediaBrowserCompat.SubscriptionCallback() {
                    override fun onChildrenLoaded(parentId: String, children: MutableList<android.support.v4.media.MediaBrowserCompat.MediaItem>) {
                        titles.set(children.map { it.description.title.toString() }); received.countDown()
                    }
                })
            }
            assertTrue("Lyric browser rows", received.await(10, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(3, titles.get().size)
            assertTrue(titles.get().contains("▶ Every mile becomes a melody"))
        } finally { compose.runOnIdle { browser.disconnect() } }
    }
}
