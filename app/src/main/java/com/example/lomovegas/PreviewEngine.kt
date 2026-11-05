package com.example.lomovegas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.TextureView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.abs

/** Estado visible de una pista en el monitor: clip activo, tiempo dentro del clip y opacidad. */
data class TrackPreview(val clip: Clip?, val localMs: Long, val alpha: Float)

/**
 * Vista previa multipista: un ExoPlayer por pista. Un reloj propio mueve el cursor
 * y sincroniza cada reproductor con el clip activo bajo el cursor.
 * En un Redmi 7 conviene no superar ~3 pistas simultáneas.
 */
class PreviewEngine(private val ctx: Context, private val s: EditorState) {
    private val players = mutableMapOf<Long, ExoPlayer>()
    private val loaded = mutableMapOf<Long, Uri?>()
    private val bitmaps = mutableMapOf<Uri, Bitmap?>()
    val view = mutableStateMapOf<Long, TrackPreview>()
    val vsize = mutableStateMapOf<Long, Pair<Float, Float>>()
    var activeSubs by mutableStateOf(listOf<Subtitle>())

    private fun player(id: Long): ExoPlayer = players.getOrPut(id) { ExoPlayer.Builder(ctx).build() }

    fun attach(trackId: Long, tv: TextureView) {
        val p = player(trackId)
        p.setVideoTextureView(tv)
        p.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0)
                    vsize[trackId] = Pair(videoSize.width * videoSize.pixelWidthHeightRatio, videoSize.height.toFloat())
            }
        })
    }

    fun bitmapFor(uri: Uri): Bitmap? = bitmaps.getOrPut(uri) { decode(uri) }

    private fun decode(uri: Uri): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1280) sample *= 2
        val o = BitmapFactory.Options().apply { inSampleSize = sample }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
    } catch (_: Exception) { null }

    fun sync(t: Long, playing: Boolean) {
        val alive = s.tracks.map { it.id }.toSet()
        players.keys.filter { it !in alive }.forEach {
            players.remove(it)?.release(); view.remove(it); loaded.remove(it); vsize.remove(it)
        }
        for (tr in s.tracks) {
            val clip = tr.clips.firstOrNull { t >= it.startMs && t < it.endMs }
            if (clip == null) {
                players[tr.id]?.playWhenReady = false
                view[tr.id] = TrackPreview(null, 0L, 0f)
                continue
            }
            val local = t - clip.startMs
            val g = fadeGain(local, clip.lengthMs, clip.fadeInMs, clip.fadeOutMs)
            val visible = tr.kind == Kind.VIDEO && !tr.hidden && clip.hasVideo
            view[tr.id] = TrackPreview(clip, local, if (visible) g else 0f)
            if (clip.isImage) { players[tr.id]?.playWhenReady = false; continue }

            val p = player(tr.id)
            val pos = clip.inMs + (local * clip.speed).toLong()
            if (loaded[tr.id] != clip.uri) {
                p.setMediaItem(MediaItem.fromUri(clip.uri))
                p.prepare()
                p.seekTo(pos)
                loaded[tr.id] = clip.uri
            } else if (!playing || abs(p.currentPosition - pos) > 400) {
                p.seekTo(pos)
            }
            val pp = PlaybackParameters(clip.speed, pitchOf(clip.afx))
            if (p.playbackParameters != pp) p.playbackParameters = pp
            p.volume = if (tr.muted) 0f else g
            p.playWhenReady = playing
        }
        activeSubs = s.subtitles.filter { t >= it.startMs && t < it.endMs }
    }

    fun pauseAll() { players.values.forEach { it.playWhenReady = false } }

    fun release() { players.values.forEach { it.release() }; players.clear() }
}
