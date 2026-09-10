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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.lang.reflect.Method

@RunWith(RobolectricTestRunner::class)
class LyricDisplayManagerTest {

    private val context = mockk<Context>(relaxed = true)
    private val mediaSession = mockk<MediaSessionCompat>(relaxed = true)
    private lateinit var prefs: SharedPreferences
    private lateinit var manager: LyricDisplayManager

    @Before
    fun setUp() {
        prefs = mockk(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.getBoolean("show_album_name", true) } returns true
        every { prefs.getBoolean("show_source_app", true) } returns true
        every { prefs.getBoolean("show_next_lyric_line", false) } returns false
        manager = LyricDisplayManager(context)
    }

    private fun invokeUpdateLyricLine(info: MediaInfo, lyricLine: String, nextLyricLine: String? = null) {
        val method: Method = LyricDisplayManager::class.java.getDeclaredMethod(
            "updateLyricLine",
            MediaSessionCompat::class.java,
            MediaInfo::class.java,
            String::class.java,
            String::class.java
        )
        method.isAccessible = true
        method.invoke(manager, mediaSession, info, lyricLine, nextLyricLine)
    }

    @Test
    fun `updateLyricLine with lyrics sets correct metadata`() {
        val mediaInfo = MediaInfo(
            title = "Song Title",
            artist = "Artist Name",
            album = "Album Name",
            appName = "MusicApp",
            appPackageName = "com.music.app",
            duration = 1000L,
            isPlaying = true,
            position = 0L,
            albumArt = null,
            appIcon = null
        )

        invokeUpdateLyricLine(mediaInfo, "Singing lyrics...")

        val slot = slot<MediaMetadataCompat>()
        verify { mediaSession.setMetadata(capture(slot)) }

        val metadata = slot.captured
        
        // Verify Album is always "From [App Name]"
        assertEquals("From MusicApp", metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM))
        
        // Verify Title is the lyric line
        assertEquals("Singing lyrics...", metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE))
        
        // Verify Artist is "Title - Artist - Album"
        assertEquals("Song Title - Artist Name - Album Name", metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST))
    }

    @Test
    fun `updateLyricLine without lyrics sets correct metadata (Restoration)`() {
        val mediaInfo = MediaInfo(
            title = "Song Title",
            artist = "Artist Name",
            album = "Album Name",
            appName = "MusicApp",
            appPackageName = "com.music.app",
            duration = 1000L,
            isPlaying = true,
            position = 0L,
            albumArt = null,
            appIcon = null
        )

        invokeUpdateLyricLine(mediaInfo, "")

        val slot = slot<MediaMetadataCompat>()
        verify { mediaSession.setMetadata(capture(slot)) }

        val metadata = slot.captured

        // Verify Album is always "From [App Name]"
        assertEquals("From MusicApp", metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM))

        // Verify Title is restored to original title
        assertEquals("Song Title", metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE))

        // Verify Artist is restored to "Artist - Album"
        assertEquals("Artist Name - Album Name", metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST))
    }

    @Test
    fun `updateLyricLine without lyrics and empty album sets correct metadata`() {
        val mediaInfo = MediaInfo(
            title = "Song Title",
            artist = "Artist Name",
            album = "", // Empty album
            appName = "MusicApp",
            appPackageName = "com.music.app",
            duration = 1000L,
            isPlaying = true,
            position = 0L,
            albumArt = null,
            appIcon = null
        )

        invokeUpdateLyricLine(mediaInfo, "")

        val slot = slot<MediaMetadataCompat>()
        verify { mediaSession.setMetadata(capture(slot)) }

        val metadata = slot.captured

        // Verify Album is always "From [App Name]"
        assertEquals("From MusicApp", metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM))

        // Verify Title is restored to original title
        assertEquals("Song Title", metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE))

        // Verify Artist is just "Artist Name" (no trailing dash)
        assertEquals("Artist Name", metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST))
    }

    @Test
    fun `updateLyricLine writes artwork to art and album art metadata keys`() {
        val albumArt = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val mediaInfo = MediaInfo(
            title = "Song Title",
            artist = "Artist Name",
            album = "Album Name",
            appName = "MusicApp",
            appPackageName = "com.music.app",
            duration = 1000L,
            isPlaying = true,
            position = 0L,
            albumArt = albumArt,
            appIcon = null
        )

        invokeUpdateLyricLine(mediaInfo, "Singing lyrics...")

        val slot = slot<MediaMetadataCompat>()
        verify { mediaSession.setMetadata(capture(slot)) }

        val metadata = slot.captured
        assertEquals(albumArt, metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ART))
        assertEquals(albumArt, metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART))
    }

    @Test
    fun `updateLyricLine omits source app when disabled`() {
        every { prefs.getBoolean("show_source_app", true) } returns false
        val mediaInfo = MediaInfo(
            title = "Song Title",
            artist = "Artist Name",
            album = "Album Name",
            appName = "MusicApp",
            appPackageName = "com.music.app",
            duration = 1000L,
            isPlaying = true,
            position = 0L,
            albumArt = null,
            appIcon = null
        )

        invokeUpdateLyricLine(mediaInfo, "Singing lyrics...")

        val slot = slot<MediaMetadataCompat>()
        verify { mediaSession.setMetadata(capture(slot)) }
        assertNull(slot.captured.getString(MediaMetadataCompat.METADATA_KEY_ALBUM))
    }
}
