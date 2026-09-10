package io.github.milankablar.carkaraoke

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import androidx.media.MediaSessionManager

/** Grants control to the app, platform-authorized controllers and official Android Auto. */
internal object MediaClientValidator {
    // Production certificates (including rotation) from Android's UAMP allowed_media_browser_callers.xml:
    // https://github.com/android/uamp/blob/main/common/src/main/res/xml/allowed_media_browser_callers.xml
    private val autoCertificates = listOf(
        "fdb00c43dbde8b51cb312aa81d3b5fa17713adb94b28f598d77f8eb89daceedf",
        "1ca8dcc0bed3cbd872d2cb791200c0292ca9975768a82d676b8b424fb65b5295"
    ).map { hex -> hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray() }

    fun isTrusted(context: Context, packageName: String, uid: Int): Boolean = runCatching {
        val packages = context.packageManager.getPackagesForUid(uid).orEmpty()
        if (packageName !in packages) return@runCatching false
        if (uid == Process.myUid()) return@runCatching true
        val user = MediaSessionManager.RemoteUserInfo(packageName, -1, uid)
        if (MediaSessionManager.getSessionManager(context).isTrustedForMediaControl(user)) return@runCatching true
        packageName == "com.google.android.projection.gearhead" && autoCertificates.any { certificate ->
            context.packageManager.hasSigningCertificate(packageName, certificate, PackageManager.CERT_INPUT_SHA256)
        }
    }.getOrDefault(false)
}
