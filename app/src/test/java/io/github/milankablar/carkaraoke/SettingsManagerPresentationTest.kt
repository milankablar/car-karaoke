package io.github.milankablar.carkaraoke

import android.content.Context
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SettingsManagerPresentationTest {
    private lateinit var context: Context

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("media_bridge_settings", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("presentation", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `presentation preferences have defaults and clamp out-of-range values`() {
        assertEquals("System", SettingsManager.getAppearanceTheme(context))
        assertEquals(28f, SettingsManager.getLyricTextSizeSp(context), 0.01f)
        assertFalse(SettingsManager.getReducedMotion(context))
        assertFalse(SettingsManager.getKeepScreenOn(context))
        assertEquals(0L, SettingsManager.getCarLyricsOffsetMs(context))
        assertFalse(SettingsManager.getCarLegacyTitle(context))

        SettingsManager.setAppearanceTheme(context, "Invalid")
        SettingsManager.setLyricTextSizeSp(context, 99f)
        SettingsManager.setCarLyricsOffsetMs(context, -99_000L)

        assertEquals("System", SettingsManager.getAppearanceTheme(context))
        assertEquals(40f, SettingsManager.getLyricTextSizeSp(context), 0.01f)
        assertEquals(-5000L, SettingsManager.getCarLyricsOffsetMs(context))
    }

    @Test
    fun `legacy presentation preferences migrate once into unified settings`() {
        context.getSharedPreferences("presentation", Context.MODE_PRIVATE).edit()
            .putString("theme", "Dark")
            .putFloat("lyricSize", 34f)
            .putBoolean("reducedMotion", true)
            .putBoolean("keepScreenOn", true)
            .putLong("carOffset", 400L)
            .putBoolean("carLegacyTitle", true)
            .commit()

        assertEquals("Dark", SettingsManager.getAppearanceTheme(context))
        assertEquals(34f, SettingsManager.getLyricTextSizeSp(context), 0.01f)
        assertTrue(SettingsManager.getReducedMotion(context))
        assertTrue(SettingsManager.getKeepScreenOn(context))
        assertEquals(400L, SettingsManager.getCarLyricsOffsetMs(context))
        assertTrue(SettingsManager.getCarLegacyTitle(context))
        assertTrue(context.getSharedPreferences("presentation", Context.MODE_PRIVATE).all.isEmpty())
    }

    @Test
    fun `presentation settings round-trip through backup and rollback snapshot`() {
        SettingsManager.setAppearanceTheme(context, "Black")
        SettingsManager.setLyricTextSizeSp(context, 32f)
        SettingsManager.setReducedMotion(context, true)
        SettingsManager.setKeepScreenOn(context, true)
        SettingsManager.setCarLyricsOffsetMs(context, -300L)
        SettingsManager.setCarLegacyTitle(context, true)

        val exported = SettingsManager.exportBackupSettings(context, includeSecrets = false)
        val snapshot = SettingsManager.backupSnapshot(context)

        context.getSharedPreferences("media_bridge_settings", Context.MODE_PRIVATE).edit().clear().commit()
        SettingsManager.importBackupSettings(context, exported)

        assertEquals("Black", SettingsManager.getAppearanceTheme(context))
        assertEquals(32f, SettingsManager.getLyricTextSizeSp(context), 0.01f)
        assertTrue(SettingsManager.getReducedMotion(context))
        assertTrue(SettingsManager.getKeepScreenOn(context))
        assertEquals(-300L, SettingsManager.getCarLyricsOffsetMs(context))
        assertTrue(SettingsManager.getCarLegacyTitle(context))

        SettingsManager.setAppearanceTheme(context, "Light")
        SettingsManager.restoreBackupSnapshot(context, snapshot)
        assertEquals("Black", SettingsManager.getAppearanceTheme(context))
        assertEquals(32f, SettingsManager.getLyricTextSizeSp(context), 0.01f)
    }

    @Test
    fun `backup validation rejects invalid presentation values`() {
        val badPayloads = listOf(
            """{"appearance_theme":"Neon"}""",
            """{"lyric_text_size_sp":120}""",
            """{"car_lyrics_offset_ms":10000}"""
        )
        badPayloads.forEach { raw ->
            try {
                SettingsManager.validateBackupSettings(JSONObject(raw))
                fail("Expected validation failure for $raw")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    @Test
    fun `ui-only appearance changes do not alter playback signature`() {
        val before = SettingsManager.playbackSettingsSignature(context)
        SettingsManager.setAppearanceTheme(context, "Dark")
        SettingsManager.setLyricTextSizeSp(context, 36f)
        SettingsManager.setReducedMotion(context, true)
        SettingsManager.setKeepScreenOn(context, true)

        assertEquals(before, SettingsManager.playbackSettingsSignature(context))
        assertFalse(SettingsManager.affectsPlayback("appearance_theme"))
        assertTrue(SettingsManager.affectsPlayback("car_lyrics_offset_ms"))
    }
}
