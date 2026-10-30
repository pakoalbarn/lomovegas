package com.example.lomovegas

import android.content.Context
import android.graphics.Matrix
import android.net.Uri
import android.view.TextureView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.abs

/**
 * Vista previa multipista: un ExoPlayer por pista. Un reloj propio mueve el cursor
 * y sincroniza cada reproductor con el clip activo bajo el cursor.
 * En un Redmi 7 conviene no superar ~3 pistas simultáneas.
 */
class PreviewEngine(private val ctx: Context, private val s: EditorState) {
    private val players = mutableMapOf<Long, ExoPlayer>()
    private val loaded = mutableMapOf<Long, Uri?>()
    val alpha = mutableStateMapOf<Long, Float>()
    var activeSubs by mutableStateOf(listOf<Subtitle>())

    private fun player(id: Long): ExoPlayer = players.getOrPut(id) { ExoPlayer.Builder(ctx).build() }

    fun attach(trackId: Long, tv: TextureView) {
        val p = player(trackId)
        p.setVideoTextureView(tv)
        fun fit(v: VideoSize) {
            if (v.width == 0 || v.height == 0 || tv.width == 0 || tv.height == 0) return
            val va = v.width * v.pixelWidthHeightRatio / v.height
            val ra = tv.width.toFloat() / tv.height
            val m = Matrix()
            if (va > ra) m.setScale(1f, ra / va, tv.width / 2f, tv.height / 2f)
            else m.setScale(va / ra, 1f, tv.width / 2f, tv.height / 2f)
            tv.setTransform(m)
        }
        p.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) { fit(videoSize) }
        })
        tv.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fit(p.videoSize) }
    }

    fun sync(t: Long, playing: Boolean) {
        val alive = s.tracks.map { it.id }.toSet()
        players.keys.filter { it !in alive }.forEach {
            players.remove(it)?.release(); alpha.remove(it); loaded.remove(it)
        }
        for (tr in s.tracks) {
            val clip = if (tr.enabled) tr.clips.firstOrNull { t >= it.startMs && t < it.endMs } else null
            if (clip == null) {
                players[tr.id]?.playWhenReady = false
                alpha[tr.id] = 0f
                continue
            }
            val p = player(tr.id)
            val pos = clip.inMs + (t - clip.startMs)
            if (loaded[tr.id] != clip.uri) {
                p.setMediaItem(MediaItem.fromUri(clip.uri))
                p.prepare()
                p.seekTo(pos)
                loaded[tr.id] = clip.uri
            } else if (!playing || abs(p.currentPosition - pos) > 400) {
                p.seekTo(pos)
            }
            val g = fadeGain(t - clip.startMs, clip.lengthMs, clip.fadeInMs, clip.fadeOutMs)
            p.volume = g
            alpha[tr.id] = if (tr.kind == Kind.VIDEO && clip.hasVideo) g else 0f
            p.playWhenReady = playing
        }
        activeSubs = s.subtitles.filter { t >= it.startMs && t < it.endMs }
    }

    fun pauseAll() { players.values.forEach { it.playWhenReady = false } }

    fun release() { players.values.forEach { it.release() }; players.clear() }
}
