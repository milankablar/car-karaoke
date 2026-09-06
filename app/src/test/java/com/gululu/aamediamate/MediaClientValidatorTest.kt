package com.gululu.aamediamate

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import androidx.media.MediaSessionManager
import io.mockk.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaClientValidatorTest {
    private val context = mockk<Context>()
    private val packages = mockk<PackageManager>()
    private val sessions = mockk<MediaSessionManager>()
    private val uid = 123456
    private val auto = "com.google.android.projection.gearhead"

    @Before fun setup() {
        mockkStatic(MediaSessionManager::class)
        every { context.packageManager } returns packages
        every { MediaSessionManager.getSessionManager(context) } returns sessions
        every { sessions.isTrustedForMediaControl(any()) } returns false
        every { packages.getPackagesForUid(any()) } returns arrayOf(auto)
        every { packages.hasSigningCertificate(any<String>(), any<ByteArray>(), any<Int>()) } returns false
    }
    @After fun teardown() { unmockkStatic(MediaSessionManager::class) }

    @Test fun spoofedPackageIsRejected() {
        assertFalse(MediaClientValidator.isTrusted(context, "unrelated.app", uid))
    }
    @Test fun packageNameAloneDoesNotGrantAccess() {
        assertFalse(MediaClientValidator.isTrusted(context, auto, uid))
    }
    @Test fun productionAutoCertificateIsAccepted() {
        every { packages.hasSigningCertificate(auto, any<ByteArray>(), PackageManager.CERT_INPUT_SHA256) } returns true
        assertTrue(MediaClientValidator.isTrusted(context, auto, uid))
    }
    @Test fun platformAuthorizedControllerIsAccepted() {
        every { sessions.isTrustedForMediaControl(any()) } returns true
        assertTrue(MediaClientValidator.isTrusted(context, auto, uid))
    }
    @Test fun ownUidIsAcceptedOnlyWithMatchingPackage() {
        assertTrue(MediaClientValidator.isTrusted(context, auto, Process.myUid()))
        assertFalse(MediaClientValidator.isTrusted(context, "wrong", Process.myUid()))
    }
}
