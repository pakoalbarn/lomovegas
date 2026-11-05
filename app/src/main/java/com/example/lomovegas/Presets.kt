package com.example.lomovegas

import android.graphics.ColorMatrix

/** Presets de vídeo: id y nombre. */
val VIDEO_PRESETS = listOf(
    0 to "Sin efecto",
    1 to "Blanco y negro",
    2 to "Sepia",
    3 to "Negativo",
    4 to "Ondular",
    5 to "Distorsión (abombado)",
    6 to "Espejo",
    7 to "Colores vivos",
    8 to "Glitch RGB"
)

/** Presets de audio: id y nombre. */
val AUDIO_PRESETS = listOf(
    0 to "Sin efecto",
    1 to "Voz aguda",
    2 to "Voz grave",
    3 to "Voz de ardilla (muy aguda)",
    4 to "Voz de monstruo (muy grave)",
    5 to "Distorsión",
    6 to "Robot",
    7 to "Eco",
    8 to "Teléfono"
)

/** A partir de este id los presets de audio usan el procesador FxAudio (el resto solo cambian el tono). */
const val AFX_FIRST_CUSTOM = 5

fun pitchOf(afx: Int): Float = when (afx) {
    1 -> 1.35f
    2 -> 0.75f
    3 -> 1.8f
    4 -> 0.55f
    else -> 1f
}

/** Efectos de color que se pueden ver en la vista previa. */
fun vfxPreviewable(vfx: Int): Boolean = vfx == 0 || vfx in 1..3 || vfx == 7

/** Efectos de audio que se pueden oír en la vista previa (los de tono). */
fun afxPreviewable(afx: Int): Boolean = afx in 0..4

fun previewColorMatrix(vfx: Int): ColorMatrix? = when (vfx) {
    1 -> ColorMatrix().apply { setSaturation(0f) }
    2 -> ColorMatrix(floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f))
    3 -> ColorMatrix(floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f))
    7 -> ColorMatrix().apply { setSaturation(1.8f) }
    else -> null
}
