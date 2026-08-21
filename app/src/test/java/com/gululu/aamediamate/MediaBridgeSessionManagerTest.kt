package com.gululu.aamediamate

import org.junit.Assert.assertEquals
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
}
