package com.gululu.aamediamate

import android.content.Context
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SettingsManagerDisplayTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("media_bridge_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `source app is shown by default`() {
        assertTrue(SettingsManager.getShowSourceApp(context))
    }

    @Test
    fun `source app preference is included in backup`() {
        SettingsManager.setShowSourceApp(context, false)

        val backup = SettingsManager.exportBackupSettings(context, includeSecrets = false)

        assertFalse(backup.getBoolean("show_source_app"))
    }

    @Test
    fun `source app preference is restored from backup`() {
        SettingsManager.importBackupSettings(
            context,
            JSONObject().put("show_source_app", false)
        )

        assertFalse(SettingsManager.getShowSourceApp(context))
    }
}
