package io.github.milankablar.carkaraoke

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import io.github.milankablar.carkaraoke.models.MediaInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.lang.reflect.Method
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaStateUpdaterArtworkTest {

    private val context = mockk<Context>(relaxed = true)
    private val mediaSession = mockk<MediaSessionCompat>(relaxed = true)
    private lateinit var updater: MediaStateUpdater

    @Before
    fun setUp() {
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.getBoolean("show_album_name", true) } returns true
        every { prefs.getBoolean("car_show_artwork", false) } returns true
        updater = MediaStateUpdater(context)
    }

    @Test
    fun `updateMetadata renders song title card by default instead of raw album art`() {
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.getBoolean("show_album_name", true) } returns true
        every { prefs.getBoolean("car_show_artwork", false) } returns false

        val albumArt = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val mediaInfo = MediaInfo(
            title = "Song Title",
            artist = "Artist Name",
            album = "Album Name",
            appName = "Spotify",
            appPackageName = "com.spotify.music",
            duration = 1000L,
            isPlaying = true,
            position = 0L,
            albumArt = albumArt,
            appIcon = null
        )

        invokeUpdateMetadata(mediaInfo)

        val slot = slot<MediaMetadataCompat>()
        verify { mediaSession.setMetadata(capture(slot)) }
        val rendered = slot.captured.getBitmap(MediaMetadataCompat.METADATA_KEY_ART)
        org.junit.Assert.assertNotNull(rendered)
        org.junit.Assert.assertNotEquals(albumArt, rendered)
        assertEquals(512, rendered!!.width)
        assertEquals(512, rendered.height)
    }

    @Test
    fun `updateMetadata writes artwork to art and album art metadata keys`() {
        val albumArt = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val mediaInfo = MediaInfo(
            title = "Song Title",
            artist = "Artist Name",
            album = "Album Name",
            appName = "Spotify",
            appPackageName = "com.spotify.music",
            duration = 1000L,
            isPlaying = true,
            position = 0L,
            albumArt = albumArt,
            appIcon = null
        )

        invokeUpdateMetadata(mediaInfo)

        val slot = slot<MediaMetadataCompat>()
        verify { mediaSession.setMetadata(capture(slot)) }

        val metadata = slot.captured
        assertEquals(albumArt, metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ART))
        assertEquals(albumArt, metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART))
    }

    private fun invokeUpdateMetadata(info: MediaInfo) {
        val method: Method = MediaStateUpdater::class.java.getDeclaredMethod(
            "updateMetadata",
            MediaSessionCompat::class.java,
            MediaInfo::class.java
        )
        method.isAccessible = true
        method.invoke(updater, mediaSession, info)
    }
}
