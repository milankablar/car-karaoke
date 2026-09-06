package com.gululu.aamediamate

import android.content.Context
import android.media.session.MediaController
import com.gululu.aamediamate.diagnostics.DiagnosticLogger
import com.gululu.aamediamate.models.MediaInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaBridgeMediaCallbackTest {
    private val context = mockk<Context>(relaxed = true)
    private val controller = mockk<MediaController>(relaxed = true)
    private val mediaInfo = MediaInfo(
        appPackageName = SOURCE_PACKAGE,
        appName = "Test Music",
        title = "Test Song",
        artist = "Test Artist",
        album = "Test Album",
        duration = 60_000L,
        position = 0L,
        isPlaying = false,
        albumArt = null,
        appIcon = null
    )

    @Before
    fun setUp() {
        mockkObject(MediaControllerManager)
        mockkObject(MediaInformationRetriever)
        mockkObject(MediaBridgeSessionManager)
        mockkObject(DiagnosticLogger)

        every { MediaBridgeSessionManager.isControllerTrusted() } returns true
        every { controller.packageName } returns SOURCE_PACKAGE
        every { MediaControllerManager.getAllControllers(context) } returns listOf(controller)
        every {
            MediaInformationRetriever.buildMediaInfoFromController(context, controller)
        } returns mediaInfo
        every { MediaBridgeSessionManager.updateFromMediaInfo(mediaInfo) } returns Unit
        every { DiagnosticLogger.info(context, any(), any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkObject(DiagnosticLogger)
        unmockkObject(MediaBridgeSessionManager)
        unmockkObject(MediaInformationRetriever)
        unmockkObject(MediaControllerManager)
    }

    @Test
    fun `playFromMediaId selects source and starts playback`() {
        var playedController: MediaController? = null
        val callback = MediaBridgeMediaCallback(context) { selectedController ->
            playedController = selectedController
        }

        callback.onPlayFromMediaId(SOURCE_PACKAGE, null)

        verify { MediaBridgeSessionManager.updateFromMediaInfo(mediaInfo) }
        assertSame(controller, playedController)
    }

    @Test
    fun `untrusted controller cannot request playback`() {
        every { MediaBridgeSessionManager.isControllerTrusted() } returns false
        var called = false
        val callback = MediaBridgeMediaCallback(context) { called = true }
        callback.onPlayFromMediaId(SOURCE_PACKAGE, null)
        verify(exactly = 0) { MediaBridgeSessionManager.updateFromMediaInfo(any()) }
        org.junit.Assert.assertFalse(called)
    }

    private companion object {
        const val SOURCE_PACKAGE = "com.example.music"
    }
}
