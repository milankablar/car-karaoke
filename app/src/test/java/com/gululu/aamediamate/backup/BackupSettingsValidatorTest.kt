package com.gululu.aamediamate.backup

import com.gululu.aamediamate.SettingsManager
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class BackupSettingsValidatorTest {
    @Test fun invalidImportDoesNotChangeAnySetting() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setLyricsEnabled(context, false)
        try {
            SettingsManager.importBackupSettings(context, JSONObject("""{"lyrics_enabled":true,"lyrics_timing_offset":999999}"""))
            fail("Expected validation failure")
        } catch (_: IllegalArgumentException) { }
        assertFalse(SettingsManager.getLyricsEnabled(context))
    }

    @Test fun malformedSavedArraysAreIgnored() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("media_bridge_settings", 0).edit()
            .putString("bridged_apps", "[{}]").putString("lyrics_providers", "{broken").commit()
        assertTrue(SettingsManager.getBridgedApps(context).isEmpty())
        assertEquals(3, SettingsManager.getLyricsProviders(context).size)
    }

    @Test fun malformedRecordIsRejected() {
        try {
            BackupSettingsValidator.validate(JSONObject("""{"bridged_apps":[{"packageName":"app"}]}"""))
            fail("Expected missing fields")
        } catch (_: Exception) { }
    }
}
