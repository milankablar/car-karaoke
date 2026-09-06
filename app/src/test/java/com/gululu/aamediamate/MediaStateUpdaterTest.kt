package com.gululu.aamediamate

import android.content.Context
import android.content.SharedPreferences
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.gululu.aamediamate.models.MediaInfo
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

@RunWith(RobolectricTestRunner::class)
class MediaStateUpdaterTest {

    private val context = mockk<Context>(relaxed = true)
    private val mediaSession = mockk<MediaSessionCompat>(relaxed = true)
    private lateinit var prefs: SharedPreferences
    private lateinit var updater: MediaStateUpdater

    @Before
    fun setUp() {
        prefs = mockk(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { context.getString(any()) } returns "Action"
        every { prefs.getString("bridged_apps", "[]") } returns "[]"
        every { prefs.getBoolean("show_album_name", true) } returns true
        every { prefs.getBoolean("show_source_app", true) } returns true
        updater = MediaStateUpdater(context)
    }

    @Test
    fun `update includes source app by default`() {
        updater.update(mediaSession, mediaInfo())

        val metadata = capturedMetadata()
        assertEquals("From MusicApp", metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM))
    }

    @Test
    fun `update omits source app when disabled`() {
        every { prefs.getBoolean("show_source_app", true) } returns false

        updater.update(mediaSession, mediaInfo())

        val metadata = capturedMetadata()
        assertNull(metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM))
    }

    @Test
    fun `update writes zero playback speed when paused`() {
        updater.update(mediaSession, mediaInfo(isPlaying = false))

        val playbackState = slot<PlaybackStateCompat>()
        verify { mediaSession.setPlaybackState(capture(playbackState)) }

        assertEquals(PlaybackStateCompat.STATE_PAUSED, playbackState.captured.state)
        assertEquals(0.0f, playbackState.captured.playbackSpeed, 0.0f)
    }

    @Test
    fun `showError publishes a non-playing app error`() {
        updater.showError(mediaSession, "Use the phone only when safe")

        val playbackState = slot<PlaybackStateCompat>()
        verify { mediaSession.setPlaybackState(capture(playbackState)) }

        assertEquals(PlaybackStateCompat.STATE_ERROR, playbackState.captured.state)
        assertEquals(PlaybackStateCompat.ERROR_CODE_APP_ERROR, playbackState.captured.errorCode)
        assertEquals("Use the phone only when safe", playbackState.captured.errorMessage)
        assertEquals(0.0f, playbackState.captured.playbackSpeed, 0.0f)
        verify { mediaSession.setMetadata(null) }
    }

    private fun capturedMetadata(): MediaMetadataCompat {
        val metadata = slot<MediaMetadataCompat>()
        verify { mediaSession.setMetadata(capture(metadata)) }
        return metadata.captured
    }

    private fun mediaInfo(isPlaying: Boolean = true) = MediaInfo(
        title = "Song Title",
        artist = "Artist Name",
        album = "Album Name",
        appName = "MusicApp",
        appPackageName = "com.music.app",
        duration = 1000L,
        isPlaying = isPlaying,
        position = 0L,
        albumArt = null,
        appIcon = null
    )
}
