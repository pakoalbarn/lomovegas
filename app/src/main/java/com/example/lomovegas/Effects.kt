package com.example.lomovegas

import android.graphics.Matrix
import androidx.media3.effect.MatrixTransformation

/** Escala del zoom in / zoom out en el instante tMs (tiempo dentro del clip). */
fun zoomScale(c: Clip, tMs: Long): Float {
    if (c.zoom == 0 || c.lengthMs <= 0) return 1f
    val p = (tMs.toFloat() / c.lengthMs).coerceIn(0f, 1f)
    return if (c.zoom == 1) 1f + 0.5f * p else 1.5f - 0.5f * p
}

/** Efecto de zoom animado para la exportación. */
fun zoomEffect(c: Clip): MatrixTransformation = MatrixTransformation { us ->
    val s = zoomScale(c, us / 1000)
    Matrix().apply { postScale(s, s) }
}

/** Matriz para un TextureView (el contenido llega estirado al tamaño de la vista). */
fun fitMatrixTexture(
    viewW: Float, viewH: Float, vw: Float, vh: Float, rot: Int, cover: Boolean, zoom: Float
): Matrix {
    val odd = rot % 180 != 0
    val rw = if (odd) vh else vw
    val rh = if (odd) vw else vh
    val k = if (cover) maxOf(viewW / rw, viewH / rh) else minOf(viewW / rw, viewH / rh)
    val dw = rw * k
    val dh = rh * k
    val pw = if (odd) dh else dw
    val ph = if (odd) dw else dh
    val m = Matrix()
    m.postScale(pw / viewW, ph / viewH, viewW / 2f, viewH / 2f)
    m.postRotate(rot.toFloat(), viewW / 2f, viewH / 2f)
    m.postScale(zoom, zoom, viewW / 2f, viewH / 2f)
    return m
}

/** Matriz para un ImageView con ScaleType.MATRIX (fotos). */
fun fitMatrixImage(
    viewW: Float, viewH: Float, iw: Float, ih: Float, rot: Int, cover: Boolean, zoom: Float
): Matrix {
    val odd = rot % 180 != 0
    val rw = if (odd) ih else iw
    val rh = if (odd) iw else ih
    val k = if (cover) maxOf(viewW / rw, viewH / rh) else minOf(viewW / rw, viewH / rh)
    val m = Matrix()
    m.postTranslate(-iw / 2f, -ih / 2f)
    m.postRotate(rot.toFloat())
    m.postScale(k * zoom, k * zoom)
    m.postTranslate(viewW / 2f, viewH / 2f)
    return m
}
