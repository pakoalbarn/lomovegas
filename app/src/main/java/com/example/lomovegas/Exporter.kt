package com.example.lomovegas

import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
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

    private fun item(c: Clip, kind: Kind): EditedMediaItem {
        val fade = c.fadeInMs > 0 || c.fadeOutMs > 0
        val audioFx: List<AudioProcessor> =
            if (fade && c.hasAudio) listOf(FadeAudio(c.fadeInMs, c.fadeOutMs, c.lengthMs)) else emptyList()
        val videoFx: List<Effect> =
            if (fade && kind == Kind.VIDEO) listOf<Effect>(FadeEffect(c.fadeInMs, c.fadeOutMs, c.lengthMs)) else emptyList()
        val b = EditedMediaItem.Builder(c.toMediaItem()).setEffects(Effects(audioFx, videoFx))
        if (kind == Kind.AUDIO) b.setRemoveVideo(true)
        return b.build()
    }

    /** Llamar desde el hilo principal. Exporta MP4 (H.264 + AAC). */
    fun export(
        ctx: Context, tracks: List<Track>, subs: List<Subtitle>, height: Int,
        onProgress: (Int) -> Unit, onDone: (String) -> Unit, onError: (String) -> Unit
    ) {
        val sequences = mutableListOf<EditedMediaItemSequence>()
        // Orden: V1 (fondo) ... Vn (arriba), luego las pistas de audio.
        val ordered = tracks.filter { it.kind == Kind.VIDEO } + tracks.filter { it.kind == Kind.AUDIO }
        for (t in ordered) {
            if (!t.enabled || t.clips.isEmpty()) continue
            val clips = t.clips.sortedBy { it.startMs }
            val b = EditedMediaItemSequence.Builder(emptyList<EditedMediaItem>())
            val leadingGap = clips.first().startMs > 0
            val anyAudio = clips.any { it.hasAudio }
            if (t.kind == Kind.VIDEO && leadingGap) tryFlag(b, "experimentalSetForceVideoTrack")
            if (anyAudio && (leadingGap || !clips.first().hasAudio)) tryFlag(b, "experimentalSetForceAudioTrack")
            var cursor = 0L
            for (c in clips) {
                if (c.startMs > cursor) b.addGap((c.startMs - cursor) * 1000)
                b.addItem(item(c, t.kind))
                cursor = c.endMs
            }
            sequences += b.build()
        }
        if (sequences.isEmpty()) { onError("No hay clips activos en la línea de tiempo."); return }

        val hasVideo = tracks.any { it.enabled && it.kind == Kind.VIDEO && it.clips.isNotEmpty() }
        val videoFx = mutableListOf<Effect>()
        if (hasVideo) {
            videoFx += Presentation.createForHeight(height)
            if (subs.isNotEmpty())
                videoFx += OverlayEffect(ImmutableList.of<TextureOverlay>(SubtitleOverlay(subs, height)))
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
