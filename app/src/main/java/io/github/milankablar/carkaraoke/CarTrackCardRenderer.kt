package io.github.milankablar.carkaraoke

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils

/** Renders a clean, static Song Title + Artist card for Android Auto's artwork slot. */
object CarTrackCardRenderer {
    private const val CARD_SIZE = 512
    private const val PADDING = 44f

    fun render(
        title: String,
        artist: String,
        sourceColorBitmap: Bitmap? = null
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(CARD_SIZE, CARD_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val (topColor, bottomColor, accentColor) = derivePalette(sourceColorBitmap)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                CARD_SIZE * 0.4f,
                CARD_SIZE.toFloat(),
                topColor,
                bottomColor,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, CARD_SIZE.toFloat(), CARD_SIZE.toFloat(), bgPaint)

        val contentWidth = (CARD_SIZE - PADDING * 2).toInt()
        val displayTitle = title.trim().ifBlank { "Car Karaoke" }
        val displayArtist = artist.trim()

        var titleSize = 48f
        var titleLayout: StaticLayout
        while (true) {
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = titleSize
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            titleLayout = buildLayout(displayTitle, titlePaint, contentWidth, maxLines = 3)
            if (titleLayout.height <= 230 || titleSize <= 32f) break
            titleSize -= 4f
        }

        val artistLayout = if (displayArtist.isNotEmpty()) {
            val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(210, 224, 232, 242)
                textSize = 31f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            buildLayout(displayArtist, artistPaint, contentWidth, maxLines = 2)
        } else {
            null
        }

        val accentHeight = 6f
        val accentGap = 24f
        val titleArtistGap = if (artistLayout != null) 18f else 0f
        val blockHeight = accentHeight + accentGap + titleLayout.height + titleArtistGap + (artistLayout?.height ?: 0)
        var cursorY = ((CARD_SIZE - blockHeight) / 2f).coerceAtLeast(PADDING)

        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
        canvas.drawRoundRect(RectF(PADDING, cursorY, PADDING + 64f, cursorY + accentHeight), 3f, 3f, accentPaint)
        cursorY += accentHeight + accentGap

        canvas.save()
        canvas.translate(PADDING, cursorY)
        titleLayout.draw(canvas)
        canvas.restore()
        cursorY += titleLayout.height + titleArtistGap

        artistLayout?.let { layout ->
            canvas.save()
            canvas.translate(PADDING, cursorY)
            layout.draw(canvas)
            canvas.restore()
        }

        return bitmap
    }

    private fun derivePalette(source: Bitmap?): Triple<Int, Int, Int> {
        if (source == null || source.width <= 0 || source.height <= 0) {
            return Triple(
                Color.rgb(34, 41, 52),
                Color.rgb(16, 19, 25),
                Color.rgb(138, 180, 248)
            )
        }
        val sample = Bitmap.createScaledBitmap(source, 8, 8, true)
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0
        for (y in 0 until sample.height) {
            for (x in 0 until sample.width) {
                val pixel = sample.getPixel(x, y)
                rSum += Color.red(pixel)
                gSum += Color.green(pixel)
                bSum += Color.blue(pixel)
                count++
            }
        }
        if (sample !== source) sample.recycle()
        if (count == 0) {
            return Triple(Color.rgb(34, 41, 52), Color.rgb(16, 19, 25), Color.rgb(138, 180, 248))
        }
        val hsv = FloatArray(3)
        Color.RGBToHSV((rSum / count).toInt(), (gSum / count).toInt(), (bSum / count).toInt(), hsv)
        val hue = hsv[0]
        val sat = hsv[1].coerceIn(0.18f, 0.55f)
        val top = Color.HSVToColor(floatArrayOf(hue, sat, 0.28f))
        val bottom = Color.HSVToColor(floatArrayOf(hue, (sat * 0.9f).coerceAtMost(0.45f), 0.12f))
        val accent = Color.HSVToColor(floatArrayOf(hue, (sat + 0.15f).coerceIn(0.35f, 0.70f), 0.88f))
        return Triple(top, bottom, accent)
    }

    private fun buildLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        maxLines: Int
    ): StaticLayout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(0f, 1.12f)
        .setIncludePad(false)
        .setMaxLines(maxLines)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()
}
