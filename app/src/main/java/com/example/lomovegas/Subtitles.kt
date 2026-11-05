package com.example.lomovegas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlaySettings
import kotlin.math.ceil

/** Familias tipográficas incluidas en Android (funcionan sin internet). */
val FONTS = listOf(
    "sans-serif" to "Normal",
    "sans-serif-medium" to "Medium",
    "sans-serif-condensed" to "Condensada",
    "sans-serif-black" to "Negra",
    "serif" to "Serif",
    "monospace" to "Mono",
    "casual" to "Casual",
    "cursive" to "Cursiva"
)

/** Paleta: blanco, negro, amarillo, celeste, verde, rojo, MORADO, naranja. */
val PALETTE: List<Int> = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFFFFEB3B, 0xFF4FC3F7, 0xFF8BC34A, 0xFFFF5252, 0xFFAB47BC, 0xFFFF9800
).map { it.toInt() }

/** Dibuja un subtítulo (texto, fondo con opacidad, contorno y sombra) en un Bitmap. */
object SubtitleRenderer {
    private fun typefaceStyle(sub: Subtitle): Int = when {
        sub.bold && sub.italic -> Typeface.BOLD_ITALIC
        sub.bold -> Typeface.BOLD
        sub.italic -> Typeface.ITALIC
        else -> Typeface.NORMAL
    }

    private fun basePaint(sub: Subtitle, px: Float): TextPaint {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG)
        p.textSize = px
        p.typeface = Typeface.create(sub.font, typefaceStyle(sub))
        p.color = sub.color
        return p
    }

    private fun layoutOf(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()

    fun render(sub: Subtitle, frameW: Int, frameH: Int): Bitmap {
        val px = (frameH * sub.sizePct / 100f).coerceAtLeast(12f)
        val maxW = (frameW * 0.9f).toInt().coerceAtLeast(120)
        val pad = (px * 0.25f).toInt()
        val strokeHalf = if (sub.outlineOn) px * 0.08f else 0f
        val shadowR = if (sub.shadowOn) px * 0.06f else 0f
        val margin = ceil(strokeHalf + shadowR * 2f + 1f).toInt()
        val inner = (maxW - 2 * pad - 2 * margin).coerceAtLeast(60)
        val text = if (sub.text.isEmpty()) " " else sub.text

        val fill = basePaint(sub, px)
        val layout = layoutOf(text, fill, inner)
        var lw = 0f
        for (i in 0 until layout.lineCount) lw = maxOf(lw, layout.getLineWidth(i))
        val textW = ceil(lw).toInt().coerceAtLeast(1)
        val w = textW + 2 * pad + 2 * margin
        val h = layout.height + 2 * pad + 2 * margin

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)

        if (sub.bgOn && sub.bgOpacity > 0) {
            val alpha = sub.bgOpacity.coerceIn(0, 100) * 255 / 100
            val bg = Paint(Paint.ANTI_ALIAS_FLAG)
            bg.color = (sub.bgColor and 0x00FFFFFF) or (alpha shl 24)
            cv.drawRoundRect(
                RectF(margin.toFloat(), margin.toFloat(), (w - margin).toFloat(), (h - margin).toFloat()),
                px * 0.2f, px * 0.2f, bg
            )
        }

        cv.translate(
            (margin + pad).toFloat() - (layout.width - textW) / 2f,
            (margin + pad).toFloat()
        )

        if (sub.outlineOn) {
            val sp = basePaint(sub, px)
            sp.style = Paint.Style.STROKE
            sp.strokeWidth = strokeHalf * 2f
            sp.strokeJoin = Paint.Join.ROUND
            sp.color = sub.outlineColor
            if (sub.shadowOn) sp.setShadowLayer(shadowR, shadowR, shadowR, sub.shadowColor)
            layoutOf(text, sp, inner).draw(cv)
        } else if (sub.shadowOn) {
            fill.setShadowLayer(shadowR, shadowR, shadowR, sub.shadowColor)
        }
        layout.draw(cv)
        return bmp
    }
}

/** Superposición temporizada de un subtítulo para la exportación. */
class SubtitleOverlay(private val sub: Subtitle, frameW: Int, frameH: Int) : BitmapOverlay() {
    private val bitmap: Bitmap = SubtitleRenderer.render(sub, frameW, frameH)
    private val empty: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private val settings: OverlaySettings = OverlaySettings.Builder()
        .setBackgroundFrameAnchor(0f, when (sub.position) { 0 -> -0.78f; 1 -> 0f; else -> 0.78f })
        .build()

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val t = presentationTimeUs / 1000
        return if (t >= sub.startMs && t < sub.endMs) bitmap else empty
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = settings
}
