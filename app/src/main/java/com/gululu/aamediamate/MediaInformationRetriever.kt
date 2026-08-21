package com.gululu.aamediamate

import android.content.Context
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.os.SystemClock
import android.util.Log
import com.gululu.aamediamate.models.MediaInfo
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.session.MediaController
import androidx.core.graphics.withTranslation
import androidx.core.graphics.scale
import androidx.core.graphics.createBitmap
import com.gululu.aamediamate.diagnostics.DiagnosticLogger
import com.gululu.aamediamate.diagnostics.DiagnosticModule
import kotlin.math.roundToLong

object MediaInformationRetriever {
    private val iconMap = mutableMapOf<String, Bitmap?>()
    internal val labelMap = mutableMapOf<String, String>()

    fun refreshCurrentMediaInfo(context: Context): MediaInfo? {
        try {
            val controller = MediaControllerManager.getFirstController(context) ?: return null
            if (controller.packageName == context.packageName) return null

            val metadata = controller.metadata ?: return null
            val state = controller.playbackState ?: return null

            val appIcon = getAppIconBitmap(context, controller.packageName)
            var albumArt = getArtwork(metadata)
            if (appIcon != null && albumArt != null && SettingsManager.getCombineAppIconAndAlbumArt(context))
            {
                albumArt = composeAlbumArtWithAppIconFixed(albumArt, appIcon)
            }

            val mediaInfo = MediaInfo(
                appPackageName = controller.packageName,
                appIcon = appIcon,
                appName = getAppLabel(context, controller.packageName),
                title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: context.getString(R.string.unknown_title),
                artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() } ?: "",
                album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() } ?: "",
                duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION),
                position = getCurrentPositionMs(state),
                isPlaying = state.state == PlaybackState.STATE_PLAYING,
                albumArt = albumArt
            )

            Log.d("MediaBridge", "🔄 Updating media info：$mediaInfo")
            DiagnosticLogger.info(
                context,
                DiagnosticModule.MEDIA,
                "Media info refreshed",
                mapOf(
                    "package" to mediaInfo.appPackageName,
                    "app" to mediaInfo.appName,
                    "title" to mediaInfo.title,
                    "artist" to mediaInfo.artist,
                    "playing" to mediaInfo.isPlaying,
                    "durationMs" to mediaInfo.duration
                )
            )
            return mediaInfo
        } catch (e: Exception) {
            Log.e("MediaBridge", "⚠️ Updating media info failed")
            DiagnosticLogger.error(
                context,
                DiagnosticModule.MEDIA,
                "Media info refresh failed",
                throwable = e
            )
            return null
        }
    }

    fun buildMediaInfoFromController(context: Context, controller: MediaController): MediaInfo? {
        if (controller.packageName == context.packageName) return null

        val metadata = controller.metadata ?: return null
        val state = controller.playbackState ?: return null

        val appIcon = getAppIconBitmap(context, controller.packageName)
        var albumArt = getArtwork(metadata)

        if (appIcon != null && albumArt != null && SettingsManager.getCombineAppIconAndAlbumArt(context)) {
            albumArt = composeAlbumArtWithAppIconFixed(albumArt, appIcon)
        }

        return MediaInfo(
            appPackageName = controller.packageName,
            appIcon = appIcon,
            appName = getAppLabel(context, controller.packageName),
            title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: context.getString(R.string.unknown_title),
            artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() } ?: "",
            album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() } ?: "",
            duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION),
            position = getCurrentPositionMs(state),
            isPlaying = state.state == PlaybackState.STATE_PLAYING,
            albumArt = albumArt
        )
    }

    private fun getArtwork(metadata: MediaMetadata): Bitmap? =
        metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)

    internal fun getCurrentPositionMs(
        state: PlaybackState,
        nowElapsedRealtimeMs: Long = SystemClock.elapsedRealtime()
    ): Long {
        val basePosition = state.position
        if (basePosition == PlaybackState.PLAYBACK_POSITION_UNKNOWN) return 0L
        if (state.state != PlaybackState.STATE_PLAYING || state.playbackSpeed <= 0f) {
            return basePosition.coerceAtLeast(0L)
        }

        val lastUpdateTime = state.lastPositionUpdateTime
        if (lastUpdateTime <= 0L) return basePosition.coerceAtLeast(0L)

        val elapsedMs = (nowElapsedRealtimeMs - lastUpdateTime).coerceAtLeast(0L)
        return (basePosition + elapsedMs * state.playbackSpeed.toDouble())
            .roundToLong()
            .coerceAtLeast(0L)
    }

    private fun composeAlbumArtWithAppIconFixed(
        albumArt: Bitmap,
        appIcon: Bitmap,
        outputSize: Int = 512,      // album size 512x512
        appIconRatio: Float = 0.25f // app icon size
    ): Bitmap {
        val scaledAlbumArt = albumArt.scale(outputSize, outputSize)

        val resultBitmap = createBitmap(outputSize, outputSize)
        val canvas = Canvas(resultBitmap)

        canvas.drawBitmap(scaledAlbumArt, 0f, 0f, null)

        val appIconSize = (outputSize * appIconRatio).toInt()
        val scaledAppIcon = appIcon.scale(appIconSize, appIconSize)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setShadowLayer(8f, 0f, 0f, Color.BLACK)
        }

        val iconLeft = outputSize - appIconSize - 16
        val iconTop = outputSize - appIconSize - 16

        canvas.withTranslation(iconLeft.toFloat(), iconTop.toFloat()) {
            val rect = RectF(0f, 0f, appIconSize.toFloat(), appIconSize.toFloat())
            drawRoundRect(rect, 20f, 20f, paint)
        }

        canvas.drawBitmap(scaledAppIcon, iconLeft.toFloat(), iconTop.toFloat(), null)

        return resultBitmap
    }

    internal fun getAppLabel(context: Context, packageName: String): String {
        if (packageName == context.packageName) {
            return context.getString(R.string.unknown_app)
        }

        labelMap[packageName]?.let { return it }

        val resolvedLabel = resolveAppLabel(context, packageName)
        val storedLabel = SettingsManager.getStoredBridgedAppName(context, packageName)
        val finalLabel = when {
            !resolvedLabel.isNullOrBlank() -> resolvedLabel
            !storedLabel.isNullOrBlank() -> storedLabel
            else -> packageName
        }

        if (packageName != context.packageName && finalLabel.isNotBlank() && finalLabel != packageName) {
            labelMap[packageName] = finalLabel
        }

        return finalLabel
    }

    private fun resolveAppLabel(context: Context, packageName: String): String? {
        return runCatching {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val label = pm.getApplicationLabel(appInfo).toString().trim()

            if (label.isNotBlank() && label != packageName) {
                label
            } else {
                val installedLabel = pm.getInstalledPackages(0)
                    .firstOrNull { it.packageName == packageName }
                    ?.applicationInfo
                    ?.loadLabel(pm)
                    ?.toString()
                    ?.trim()
                    .orEmpty()

                installedLabel.takeIf { it.isNotBlank() && it != packageName }
            }
        }.getOrNull()
    }

    private fun getAppIconBitmap(context: Context, packageName: String): Bitmap? {
        return iconMap[packageName] ?: run {
            val bitmap = try {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                drawableToBitmap(drawable)
            } catch (e: Exception) {
                Log.w("MediaBridge", "⚠️ Failed to get app icon for $packageName: ${e.message}")
                DiagnosticLogger.warn(
                    context,
                    DiagnosticModule.MEDIA,
                    "Failed to load app icon",
                    mapOf("package" to packageName),
                    e
                )
                null
            }
            // Cache the result (even if null) to avoid repeated attempts
            iconMap[packageName] = bitmap
            bitmap
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            drawable.bitmap?.let { return it }
        }
        val bitmap = createBitmap(drawable.intrinsicWidth.takeIf { it > 0 } ?: 1,
            drawable.intrinsicHeight.takeIf { it > 0 } ?: 1)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
