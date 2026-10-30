package com.example.lomovegas

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem

enum class Kind { VIDEO, AUDIO }

data class Clip(
    val id: Long,
    val uri: Uri,
    val name: String,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val srcMs: Long,      // duración del archivo original
    val startMs: Long,    // posición en la línea de tiempo
    val inMs: Long,       // punto de entrada dentro del archivo
    val outMs: Long,      // punto de salida dentro del archivo
    val fadeInMs: Long = 0,
    val fadeOutMs: Long = 0
) {
    val lengthMs: Long get() = outMs - inMs
    val endMs: Long get() = startMs + lengthMs

    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setUri(uri)
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(inMs)
                .setEndPositionMs(outMs)
                .build()
        ).build()
}

data class Track(
    val id: Long,
    val kind: Kind,
    val name: String,
    val clips: List<Clip> = emptyList(),   // siempre ordenados por startMs, sin solaparse
    val enabled: Boolean = true
)

data class Subtitle(
    val id: Long,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val font: String = "sans-serif",
    val bold: Boolean = false,
    val italic: Boolean = false,
    val color: Int = 0xFFFFFFFF.toInt(),
    val sizePct: Int = 6,          // tamaño como % de la altura del vídeo
    val position: Int = 0,         // 0 abajo, 1 centro, 2 arriba
    val background: Boolean = true
)

class EditorState {
    var tracks by mutableStateOf(listOf(Track(1, Kind.VIDEO, "V1"), Track(2, Kind.AUDIO, "A1")))
    var subtitles by mutableStateOf(listOf<Subtitle>())
    var selectedClipId by mutableStateOf<Long?>(null)
    var selectedTrackId by mutableStateOf<Long?>(1L)
    var selectedSubId by mutableStateOf<Long?>(null)
    var playheadMs by mutableStateOf(0L)
    var playing by mutableStateOf(false)
    var zoom by mutableStateOf(0.04f)        // dp por milisegundo

    private var nextId = 100L
    private var vCount = 1
    private var aCount = 1
    private fun newId() = nextId++

    // ---------- consultas ----------
    fun totalMs(): Long = maxOf(
        tracks.maxOfOrNull { t -> t.clips.maxOfOrNull { it.endMs } ?: 0L } ?: 0L,
        subtitles.maxOfOrNull { it.endMs } ?: 0L
    )
    fun selectedClip(): Clip? = tracks.flatMap { it.clips }.firstOrNull { it.id == selectedClipId }
    fun selectedSub(): Subtitle? = subtitles.firstOrNull { it.id == selectedSubId }

    // ---------- selección ----------
    fun selectClip(id: Long) {
        selectedClipId = id; selectedSubId = null
        tracks.firstOrNull { t -> t.clips.any { it.id == id } }?.let { selectedTrackId = it.id }
    }
    fun selectSub(id: Long) { selectedSubId = id; selectedClipId = null }

    // ---------- pistas ----------
    fun addTrack(kind: Kind) {
        val n = if (kind == Kind.VIDEO) ++vCount else ++aCount
        val t = Track(newId(), kind, (if (kind == Kind.VIDEO) "V" else "A") + n)
        tracks = tracks + t
        selectedTrackId = t.id
    }

    fun removeTrack() {
        val t = tracks.firstOrNull { it.id == selectedTrackId } ?: return
        if (tracks.count { it.kind == t.kind } <= 1) return   // siempre queda una de cada tipo
        tracks = tracks.filter { it.id != t.id }
        if (t.clips.any { it.id == selectedClipId }) selectedClipId = null
        selectedTrackId = tracks.firstOrNull { it.kind == t.kind }?.id
    }

    fun toggleTrack() {
        val id = selectedTrackId ?: return
        tracks = tracks.map { if (it.id == id) it.copy(enabled = !it.enabled) else it }
    }

    // ---------- importar ----------
    fun importMedia(ctx: Context, uri: Uri) {
        val mmr = MediaMetadataRetriever()
        var dur = 0L; var hasVideo = false; var hasAudio = false
        try {
            mmr.setDataSource(ctx, uri)
            dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            hasVideo = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            hasAudio = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
        } catch (_: Exception) {
        } finally { mmr.release() }
        if (dur <= 0) return
        var name = "clip"
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) name = c.getString(i)
        }
        val kind = if (hasVideo) Kind.VIDEO else Kind.AUDIO
        val target = tracks.firstOrNull { it.id == selectedTrackId && it.kind == kind }
            ?: tracks.first { it.kind == kind }
        val start = target.clips.maxOfOrNull { it.endMs } ?: 0L
        val clip = Clip(newId(), uri, name, hasVideo, hasAudio, dur, start, 0, dur)
        tracks = tracks.map { if (it.id == target.id) it.copy(clips = it.clips + clip) else it }
        selectClip(clip.id)
    }

    // ---------- edición de clips ----------
    private fun mutateClip(id: Long, f: (c: Clip, minStart: Long, maxEnd: Long) -> Clip) {
        tracks = tracks.map { t ->
            val i = t.clips.indexOfFirst { it.id == id }
            if (i < 0) t else {
                val prevEnd = t.clips.getOrNull(i - 1)?.endMs ?: 0L
                val nextStart = t.clips.getOrNull(i + 1)?.startMs ?: (Long.MAX_VALUE / 4)
                val list = t.clips.toMutableList()
                list[i] = f(list[i], prevEnd, nextStart)
                t.copy(clips = list)
            }
        }
    }

    fun dragClip(id: Long, deltaMs: Long) = mutateClip(id) { c, minS, maxE ->
        val ns = (c.startMs + deltaMs).coerceIn(minS, maxOf(minS, maxE - c.lengthMs))
        c.copy(startMs = ns)
    }

    fun trimStart(d: Long) {
        val id = selectedClipId ?: return
        mutateClip(id) { c, minS, _ ->
            val lo = maxOf(-c.inMs, minS - c.startMs)
            val hi = maxOf(0L, c.lengthMs - 500)
            val dd = d.coerceIn(lo, hi)
            c.copy(inMs = c.inMs + dd, startMs = c.startMs + dd)
        }
    }

    fun trimEnd(d: Long) {
        val id = selectedClipId ?: return
        mutateClip(id) { c, _, maxE ->
            val lo = -maxOf(0L, c.lengthMs - 500)
            val hi = minOf(c.srcMs - c.outMs, maxE - c.endMs)
            val dd = d.coerceIn(lo, maxOf(lo, hi))
            c.copy(outMs = c.outMs + dd)
        }
    }

    /** Divide el clip seleccionado en el cursor (como la tecla S de Vegas). */
    fun split() {
        val id = selectedClipId ?: return
        tracks = tracks.map { t ->
            val i = t.clips.indexOfFirst { it.id == id }
            if (i < 0) t else {
                val c = t.clips[i]
                if (playheadMs <= c.startMs + 300 || playheadMs >= c.endMs - 300) t else {
                    val cut = c.inMs + (playheadMs - c.startMs)
                    val a = c.copy(outMs = cut, fadeOutMs = 0)
                    val b = c.copy(id = newId(), startMs = playheadMs, inMs = cut, fadeInMs = 0)
                    val list = t.clips.toMutableList()
                    list[i] = a; list.add(i + 1, b)
                    t.copy(clips = list)
                }
            }
        }
    }

    private val fades = listOf(0L, 500L, 1000L, 2000L)
    fun cycleFade(isIn: Boolean) {
        val id = selectedClipId ?: return
        mutateClip(id) { c, _, _ ->
            val cur = if (isIn) c.fadeInMs else c.fadeOutMs
            val next = fades[(fades.indexOf(cur) + 1) % fades.size]
            if (isIn) c.copy(fadeInMs = next) else c.copy(fadeOutMs = next)
        }
    }

    // ---------- subtítulos ----------
    fun addSubtitle(): Subtitle {
        val s = Subtitle(newId(), playheadMs, playheadMs + 3000, "Texto")
        subtitles = subtitles + s
        selectSub(s.id)
        return s
    }
    fun updateSubtitle(s: Subtitle) { subtitles = subtitles.map { if (it.id == s.id) s else it } }
    fun dragSub(id: Long, d: Long) {
        subtitles = subtitles.map {
            if (it.id != id) it else {
                val ns = maxOf(0L, it.startMs + d)
                it.copy(startMs = ns, endMs = ns + (it.endMs - it.startMs))
            }
        }
    }

    fun delete() {
        val cid = selectedClipId
        if (cid != null) {
            tracks = tracks.map { t -> t.copy(clips = t.clips.filter { it.id != cid }) }
            selectedClipId = null
        } else selectedSubId?.let { sid ->
            subtitles = subtitles.filter { it.id != sid }
            selectedSubId = null
        }
    }
}
