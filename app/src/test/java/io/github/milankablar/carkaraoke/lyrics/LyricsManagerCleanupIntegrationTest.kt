package io.github.milankablar.carkaraoke.lyrics

import android.content.Context
import io.github.milankablar.carkaraoke.SettingsManager
import io.github.milankablar.carkaraoke.data.LyricsCleanupField
import io.github.milankablar.carkaraoke.data.LyricsCleanupRule
import io.github.milankablar.carkaraoke.data.LyricsProviderConfig
import io.github.milankablar.carkaraoke.lyrics.providers.LyricsProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test

class LyricsManagerCleanupIntegrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        mockkObject(SettingsManager)
    }

    @After
    fun tearDown() {
        unmockkObject(SettingsManager)
    }

    @Test
    fun `getLyricsLrt cleans artist only for automatic search`() = runTest {
        var capturedTitle = ""
        var capturedArtist = ""

        val provider = object : LyricsProvider {
            override suspend fun getLyricsLrc(
                context: Context,
                title: String,
                artist: String,
                duration: String
            ): String? {
                capturedTitle = title
                capturedArtist = artist
                return "[00:01.00]Line"
            }
        }

        every { SettingsManager.getLyricsCleanupRules(context) } returns listOf(
            LyricsCleanupRule(
                id = "default_lossless_artist",
                name = "Lossless",
                field = LyricsCleanupField.ARTIST,
                pattern = """(?i)\s*[•·-]?\s*Lossless\s*$"""
            )
        )
        every { SettingsManager.getEnabledProvidersInOrder(context) } returns listOf(
            LyricsProviderConfig(
                id = "test",
                name = "Test",
                descriptionRes = 0,
                isEnabled = true,
                priority = 1,
                provider = provider
            )
        )

        val result = LyricsManager.getLyricsLrt(
            context = context,
            title = "Upbeat",
            artist = "Green Day • Lossless",
            duration = "180"
        )

        assertEquals("[00:01.00]Line", result)
        assertEquals("Upbeat", capturedTitle)
        assertEquals("Green Day", capturedArtist)
    }
}
