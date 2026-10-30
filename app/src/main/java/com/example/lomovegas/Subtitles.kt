package com.example.lomovegas

import android.graphics.Typeface
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.TextOverlay

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

fun styledText(subs: List<Subtitle>, outHeight: Int): SpannableString {
    val sb = SpannableStringBuilder()
    subs.forEachIndexed { i, sub ->
        if (i > 0) sb.append("\n")
        val a = sb.length
        sb.append(sub.text)
        val b = sb.length
        if (b > a) {
            val f = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            sb.setSpan(TypefaceSpan(sub.font), a, b, f)
            sb.setSpan(AbsoluteSizeSpan((outHeight * sub.sizePct / 100f).toInt().coerceAtLeast(12)), a, b, f)
            sb.setSpan(ForegroundColorSpan(sub.color), a, b, f)
            val style = when {
                sub.bold && sub.italic -> Typeface.BOLD_ITALIC
                sub.bold -> Typeface.BOLD
                sub.italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            if (style != Typeface.NORMAL) sb.setSpan(StyleSpan(style), a, b, f)
            if (sub.background) sb.setSpan(BackgroundColorSpan(0x99000000.toInt()), a, b, f)
        }
    }
    return SpannableString(sb)
}

/** Superposición de subtítulos con tiempos (se aplica a la composición completa). */
class SubtitleOverlay(private val subs: List<Subtitle>, private val outHeight: Int) : TextOverlay() {
    private fun active(us: Long): List<Subtitle> {
        val t = us / 1000
        return subs.filter { t >= it.startMs && t < it.endMs }
    }

    override fun getText(presentationTimeUs: Long): SpannableString {
        val a = active(presentationTimeUs)
        return if (a.isEmpty()) SpannableString(" ") else styledText(a, outHeight)
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val pos = active(presentationTimeUs).firstOrNull()?.position ?: 0
        val y = when (pos) { 0 -> -0.8f; 1 -> 0f; else -> 0.8f }
        return OverlaySettings.Builder().setBackgroundFrameAnchor(0f, y).build()
    }
}
