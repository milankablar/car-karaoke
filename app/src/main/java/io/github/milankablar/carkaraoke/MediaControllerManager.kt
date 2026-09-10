package io.github.milankablar.carkaraoke

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.PlaybackState
import io.github.milankablar.carkaraoke.diagnostics.DiagnosticLogger
import io.github.milankablar.carkaraoke.diagnostics.DiagnosticModule

object MediaControllerManager {
    fun getAllControllers(context: Context): List<MediaController> {
        return try {
            val sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as android.media.session.MediaSessionManager
            val component = ComponentName(context, MediaNotificationListener::class.java)
            val controllers = sessionManager.getActiveSessions(component)
                .filter { it.packageName != context.packageName && Global.packageAllowed(context, it.packageName) }
            DiagnosticLogger.debug(
                context,
                DiagnosticModule.MEDIA,
                "Active media controllers queried",
                mapOf("count" to controllers.size, "packages" to controllers.joinToString(",") { it.packageName })
            )
            controllers
        } catch (e: Exception) {
            // Likely SecurityException due to missing notification access
            DiagnosticLogger.error(
                context,
                DiagnosticModule.MEDIA,
                "Failed to query active media controllers",
                throwable = e
            )
            emptyList()
        }
    }

    private var selectedToken: android.media.session.MediaSession.Token? = null
    private var explicitPackage: String? = null
    fun select(controller: MediaController) { selectedToken = controller.sessionToken; explicitPackage = controller.packageName }
    fun useAutomaticSelection() { selectedToken = null; explicitPackage = null }
    fun getFirstController(context: Context): MediaController? {
        val controllers = getAllControllers(context)
        val pinned = controllers.firstOrNull { it.sessionToken == selectedToken }
        val selected = if (explicitPackage != null) pinned ?: controllers.firstOrNull { it.packageName == explicitPackage }
            else pinned?.takeIf { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: pinned ?: controllers.firstOrNull()
        selectedToken = selected?.sessionToken
        return selected
    }
    fun getActiveController(context: Context): MediaController? = getFirstController(context)
}
