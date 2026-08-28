package com.gululu.aamediamate

import com.gululu.aamediamate.models.MediaInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaBridgeSessionManagerTest {

    @Test
    fun `calculateEndOfMediaRefreshDelay schedules after remaining duration plus grace`() {
        val delay = MediaBridgeSessionManager.calculateEndOfMediaRefreshDelay(
            positionMs = 90_000L,
            durationMs = 120_000L
        )

        assertEquals(31_000L, delay)
    }

    @Test
    fun `calculateEndOfMediaRefreshDelay uses minimum delay when position already exceeded duration`() {
        val delay = MediaBridgeSessionManager.calculateEndOfMediaRefreshDelay(
            positionMs = 121_000L,
            durationMs = 120_000L
        )

        assertEquals(1_000L, delay)
    }

    @Test
    fun `shouldTreatPositionAsStaleAfterMediaChange detects state older than metadata`() {
        val previousInfo = mediaInfo(
            title = "Old Song",
            position = 120_000L,
            retrievedAtElapsedRealtimeMs = 100_000L
        )
        val newInfo = mediaInfo(
            title = "New Song",
            position = 121_000L,
            playbackStateUpdateTimeMs = 100_000L,
            retrievedAtElapsedRealtimeMs = 130_000L
        )

        val stale = MediaBridgeSessionManager.shouldTreatPositionAsStaleAfterMediaChange(
            previousInfo = previousInfo,
            info = newInfo,
            lastMetadataChangeElapsedRealtimeMs = 130_000L,
            nowElapsedRealtimeMs = 130_000L
        )

        assertTrue(stale)
    }

    @Test
    fun `shouldTreatPositionAsStaleAfterMediaChange detects carried previous position`() {
        val previousInfo = mediaInfo(
            title = "Old Song",
            position = 110_000L,
            retrievedAtElapsedRealtimeMs = 100_000L
        )
        val newInfo = mediaInfo(
            title = "New Song",
            position = 139_000L,
            playbackStateUpdateTimeMs = 130_000L,
            retrievedAtElapsedRealtimeMs = 130_000L
        )

        val stale = MediaBridgeSessionManager.shouldTreatPositionAsStaleAfterMediaChange(
            previousInfo = previousInfo,
            info = newInfo,
            lastMetadataChangeElapsedRealtimeMs = 0L,
            nowElapsedRealtimeMs = 130_000L
        )

        assertTrue(stale)
    }

    @Test
    fun `shouldTreatPositionAsStaleAfterMediaChange allows fresh near-start media`() {
        val previousInfo = mediaInfo(title = "Old Song", position = 120_000L)
        val newInfo = mediaInfo(title = "New Song", position = 1_500L)

        val stale = MediaBridgeSessionManager.shouldTreatPositionAsStaleAfterMediaChange(
            previousInfo = previousInfo,
            info = newInfo,
            lastMetadataChangeElapsedRealtimeMs = 130_000L,
            nowElapsedRealtimeMs = 130_000L
        )

        assertFalse(stale)
    }

    private fun mediaInfo(
        title: String,
        position: Long,
        playbackStateUpdateTimeMs: Long = 0L,
        retrievedAtElapsedRealtimeMs: Long = 0L
    ) = MediaInfo(
        appPackageName = "com.music.app",
        appName = "Music App",
        title = title,
        artist = "Artist",
        album = "Album",
        duration = 180_000L,
        position = position,
        isPlaying = true,
        albumArt = null,
        appIcon = null,
        playbackStateUpdateTimeMs = playbackStateUpdateTimeMs,
        retrievedAtElapsedRealtimeMs = retrievedAtElapsedRealtimeMs
    )
}
