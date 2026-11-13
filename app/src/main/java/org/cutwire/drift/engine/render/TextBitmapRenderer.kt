package org.cutwire.drift.engine.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import org.cutwire.drift.core.model.TextAlignment
import org.cutwire.drift.core.model.TextOverlay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-performance Android Canvas renderer that translates [TextOverlay] domain specifications
 * into crisp ARGB_8888 Bitmaps for Media3 [androidx.media3.effect.BitmapOverlay] GPU compositing.
 */
@Singleton
class TextBitmapRenderer @Inject constructor() {

    fun renderTextToBitmap(
        overlay: TextOverlay,
        viewportWidth: Int = 1080,
        viewportHeight: Int = 1920
    ): Bitmap {
        val width = viewportWidth.coerceAtLeast(320)
        val height = viewportHeight.coerceAtLeast(320)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = overlay.textColor.toInt()
            textSize = overlay.fontSizeSp * 2.5f // scale to canvas resolution
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val alignment = when (overlay.alignment) {
            TextAlignment.LEFT -> Layout.Alignment.ALIGN_NORMAL
            TextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
            TextAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }

        val maxTextWidth = (width * 0.85f).toInt()
        val staticLayout = StaticLayout.Builder
            .obtain(overlay.text, 0, overlay.text.length, textPaint, maxTextWidth)
            .setAlignment(alignment)
            .setLineSpacing(0f, 1.15f)
            .setIncludePad(true)
            .build()

        val textX = (width - staticLayout.width) / 2f
        val textY = (height - staticLayout.height) / 2f

        // Optional background box
        if (overlay.backgroundColor != 0L) {
            val bgPaint = Paint().apply {
                color = overlay.backgroundColor.toInt()
                style = Paint.Style.FILL
            }
            val pad = 24f
            canvas.drawRoundRect(
                textX - pad,
                textY - pad,
                textX + staticLayout.width + pad,
                textY + staticLayout.height + pad,
                16f,
                16f,
                bgPaint
            )
        }

        // Draw text stroke if specified
        if (overlay.strokeWidthPx > 0f) {
            val strokePaint = TextPaint(textPaint).apply {
                style = Paint.Style.STROKE
                strokeWidth = overlay.strokeWidthPx * 3f
                color = overlay.strokeColor.toInt()
            }
            val strokeLayout = StaticLayout.Builder
                .obtain(overlay.text, 0, overlay.text.length, strokePaint, maxTextWidth)
                .setAlignment(alignment)
                .setLineSpacing(0f, 1.15f)
                .setIncludePad(true)
                .build()

            canvas.save()
            canvas.translate(textX, textY)
            strokeLayout.draw(canvas)
            canvas.restore()
        }

        // Draw primary text fill
        canvas.save()
        canvas.translate(textX, textY)
        staticLayout.draw(canvas)
        canvas.restore()

        return bitmap
    }
}
