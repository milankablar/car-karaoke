package io.github.milankablar.carkaraoke

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.media.session.PlaybackState
import io.github.milankablar.carkaraoke.R
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaInformationRetrieverTest {

    private val context = mockk<Context>()
    private val packageManager = mockk<PackageManager>()
    private val sharedPreferences = mockk<SharedPreferences>(relaxed = true)

    @Before
    fun setUp() {
        MediaInformationRetriever.labelMap.clear()
        every { context.packageManager } returns packageManager
        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
        every { sharedPreferences.getString(any(), any()) } answers { secondArg() }
    }

    @Test
    fun `getAppLabel uses alternate label when direct label matches package name`() {
        val packageName = "com.example.music"
        val appInfo = mockk<ApplicationInfo>()
        val fallbackAppInfo = mockk<ApplicationInfo>()
        val packageInfo = PackageInfo().apply {
            javaClass.getField("packageName").set(this, packageName)
            javaClass.getField("applicationInfo").set(this, fallbackAppInfo)
        }

        every { context.packageName } returns "io.github.milankablar.carkaraoke"
        every { packageManager.getApplicationInfo(packageName, 0) } returns appInfo
        every { packageManager.getApplicationLabel(appInfo) } returns packageName
        every { packageManager.getInstalledPackages(0) } returns listOf(packageInfo)
        every { fallbackAppInfo.loadLabel(packageManager) } returns "Music App"

        val label = MediaInformationRetriever.getAppLabel(context, packageName)

        assertEquals("Music App", label)
    }

    @Test
    fun `getAppLabel does not resolve self package as app name`() {
        val packageName = "io.github.milankablar.carkaraoke"
        val appInfo = mockk<ApplicationInfo>()

        every { context.packageName } returns packageName
        every { context.getString(R.string.unknown_app) } returns "Unknown App"
        every { packageManager.getApplicationInfo(packageName, 0) } returns appInfo
        every { packageManager.getApplicationLabel(appInfo) } returns "AAMediaMate"

        val label = MediaInformationRetriever.getAppLabel(context, packageName)

        assertEquals("Unknown App", label)
    }

    @Test
    fun `getAppLabel falls back to package name for unresolved external package`() {
        val packageName = "com.example.hiddenmusic"

        every { context.packageName } returns "io.github.milankablar.carkaraoke"
        every { packageManager.getApplicationInfo(packageName, 0) } throws PackageManager.NameNotFoundException()

        val label = MediaInformationRetriever.getAppLabel(context, packageName)

        assertEquals(packageName, label)
    }

    @Test
    fun `getCurrentPositionMs advances playing state using elapsed realtime and speed`() {
        val state = PlaybackState.Builder()
            .setState(PlaybackState.STATE_PLAYING, 10_000L, 1.5f, 100_000L)
            .build()

        val position = MediaInformationRetriever.getCurrentPositionMs(
            state,
            nowElapsedRealtimeMs = 104_000L
        )

        assertEquals(16_000L, position)
    }

    @Test
    fun `getCurrentPositionMs does not advance paused state`() {
        val state = PlaybackState.Builder()
            .setState(PlaybackState.STATE_PAUSED, 10_000L, 1.0f, 100_000L)
            .build()

        val position = MediaInformationRetriever.getCurrentPositionMs(
            state,
            nowElapsedRealtimeMs = 104_000L
        )

        assertEquals(10_000L, position)
    }

    @Test
    fun `getCurrentPositionMs returns zero for unknown position`() {
        val state = PlaybackState.Builder()
            .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f, 100_000L)
            .build()

        val position = MediaInformationRetriever.getCurrentPositionMs(
            state,
            nowElapsedRealtimeMs = 104_000L
        )

        assertEquals(0L, position)
    }

    @Test
    fun `getEstimatedPositionMs advances playing media info from retrieval time`() {
        val info = io.github.milankablar.carkaraoke.models.MediaInfo(
            appPackageName = "com.music.app",
            appName = "Music App",
            title = "Song",
            artist = "Artist",
            album = "Album",
            duration = 180_000L,
            position = 2_000L,
            isPlaying = true,
            albumArt = null,
            appIcon = null,
            retrievedAtElapsedRealtimeMs = 10_000L
        )

        val position = MediaInformationRetriever.getEstimatedPositionMs(
            info,
            nowElapsedRealtimeMs = 14_000L
        )

        assertEquals(6_000L, position)
    }
}
