package com.example.lomovegas

import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Effect
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import java.io.File

object Exporter {

    /** Activa un ajuste experimental de Media3 solo si existe en la versión instalada. */
    private fun tryFlag(builder: Any, name: String) {
        try { builder.javaClass.getMethod(name, java.lang.Boolean.TYPE).invoke(builder, true) } catch (_: Exception) {}
    }

    private fun even(n: Int) = n - n % 2

    private fun needsConvert(tracks: List<Track>): Boolean =
        tracks.any { t -> t.kind == Kind.VIDEO && !t.hidden && t.clips.any { it.fitMode != 0 } }

    /** Tamaño de salida (ancho, alto). */
    fun outSize(tracks: List<Track>, height: Int): Pair<Int, Int> {
        var aspect = 16f / 9f
        if (!needsConvert(tracks)) {
            val c = tracks.filter { it.kind == Kind.VIDEO && !it.hidden }
                .flatMap { it.clips }.filter { it.hasVideo }.minByOrNull { it.startMs }
            if (c != null && c.vw > 0 && c.vh > 0) {
                val odd = c.rotation % 180 != 0
                aspect = if (odd) c.vh.toFloat() / c.vw else c.vw.toFloat() / c.vh
            }
        }
        return Pair(even((height * aspect).toInt()), height)
    }

    private fun item(c: Clip, t: Track, outW: Int, outH: Int): EditedMediaItem {
        val fade = c.fadeInMs > 0 || c.fadeOutMs > 0
        val removeVideo = t.kind == Kind.AUDIO || t.hidden
        val removeAudio = t.muted
        val audioFx = mutableListOf<AudioProcessor>()
        val videoFx = mutableListOf<Effect>()

        if (!removeAudio && c.hasAudio) {
            val pitch = pitchOf(c.afx)
            if (c.speed != 1f || pitch != 1f)
                audioFx.add(SonicAudioProcessor().also { it.setSpeed(c.speed); it.setPitch(pitch) })
            if (c.afx >= AFX_FIRST_CUSTOM) audioFx.add(FxAudio(c.afx))
            if (fade) audioFx.add(FadeAudio(c.fadeInMs, c.fadeOutMs, c.lengthMs))
        }
        if (!removeVideo && c.hasVideo) {
            if (c.speed != 1f && !c.isImage) videoFx.add(SpeedChangeEffect(c.speed))
            if (c.rotation != 0)
                videoFx.add(
                    ScaleAndRotateTransformation.Builder()
                        .setRotationDegrees(((360 - c.rotation) % 360).toFloat()).build()
                )
            if (c.fitMode != 0)
                videoFx.add(
                    Presentation.createForWidthAndHeight(
                        outW, outH,
                        if (c.fitMode == 2) Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                        else Presentation.LAYOUT_SCALE_TO_FIT
                    )
                )
            if (c.zoom != 0) videoFx.add(zoomEffect(c))
            if (c.vfx != 0) videoFx.add(VideoFxEffect(c.vfx))
            if (fade) videoFx.add(FadeEffect(c.fadeInMs, c.fadeOutMs, c.lengthMs))
        }

        val b = EditedMediaItem.Builder(c.toMediaItem()).setEffects(Effects(audioFx, videoFx))
        if (c.isImage) b.setDurationUs(c.lengthMs * 1000L).setFrameRate(30)
        if (removeVideo) b.setRemoveVideo(true)
        if (removeAudio) b.setRemoveAudio(true)
        return b.build()
    }

    /** Llamar desde el hilo principal. Exporta MP4 (H.264 + AAC). */
    fun export(
        ctx: Context, tracks: List<Track>, subs: List<Subtitle>, height: Int,
        onProgress: (Int) -> Unit, onDone: (String) -> Unit, onError: (String) -> Unit
    ) {
        val (outW, outH) = outSize(tracks, height)
        val sequences = mutableListOf<EditedMediaItemSequence>()
        // Orden: V1 (fondo) ... Vn (arriba), luego las pistas de audio.
        val ordered = tracks.filter { it.kind == Kind.VIDEO } + tracks.filter { it.kind == Kind.AUDIO }
        for (t in ordered) {
            val visibleVideo = t.kind == Kind.VIDEO && !t.hidden
            val usable = t.clips.sortedBy { it.startMs }
                .filter { (visibleVideo && it.hasVideo) || (!t.muted && it.hasAudio) }
            if (usable.isEmpty()) continue

            val b = EditedMediaItemSequence.Builder(emptyList<EditedMediaItem>())
            val leadingGap = usable.first().startMs > 0
            val anyAudio = !t.muted && usable.any { it.hasAudio }
            if (visibleVideo && leadingGap) tryFlag(b, "experimentalSetForceVideoTrack")
            if (anyAudio && (leadingGap || !usable.first().hasAudio)) tryFlag(b, "experimentalSetForceAudioTrack")
            var cursor = 0L
            for (c in usable) {
                if (c.startMs > cursor) b.addGap((c.startMs - cursor) * 1000)
                b.addItem(item(c, t, outW, outH))
                cursor = c.endMs
            }
            sequences += b.build()
        }
        if (sequences.isEmpty()) { onError("No hay clips activos en la línea de tiempo."); return }

        val hasVideo = tracks.any { t -> t.kind == Kind.VIDEO && !t.hidden && t.clips.any { it.hasVideo } }
        val videoFx = mutableListOf<Effect>()
        if (hasVideo) {
            videoFx.add(
                if (needsConvert(tracks)) Presentation.createForWidthAndHeight(outW, outH, Presentation.LAYOUT_SCALE_TO_FIT)
                else Presentation.createForHeight(outH)
            )
            if (subs.isNotEmpty())
                videoFx.add(OverlayEffect(ImmutableList.copyOf<TextureOverlay>(subs.map { SubtitleOverlay(it, outW, outH) })))
        }
        val composition = Composition.Builder(sequences)
            .setEffects(Effects(emptyList(), videoFx))
            .build()

        val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: ctx.filesDir
        val out = File(dir, "lomovegas_${height}p_${System.currentTimeMillis()}.mp4")
        val handler = Handler(Looper.getMainLooper())
        var finished = false

        val transformer = Transformer.Builder(ctx)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    finished = true; onDone(out.absolutePath)
                }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    finished = true; onError(exportException.message ?: "Error de exportación")
                }
            }).build()

        transformer.start(composition, out.absolutePath)

        val holder = ProgressHolder()
        handler.post(object : Runnable {
            override fun run() {
                if (finished) return
                if (transformer.getProgress(holder) != Transformer.PROGRESS_STATE_NOT_STARTED) onProgress(holder.progress)
                handler.postDelayed(this, 500)
            }
        })
    }
}
