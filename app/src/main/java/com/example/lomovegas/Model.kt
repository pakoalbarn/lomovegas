package com.example.lomovegas

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

enum class Kind { VIDEO, AUDIO }

data class Clip(
    val id: Long,
    val uri: Uri,
    val name: String,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val isImage: Boolean,
    val srcMs: Long,       // duración del archivo original
    val startMs: Long,     // posición en la línea de tiempo
    val inMs: Long,        // entrada dentro del archivo
    val outMs: Long,       // salida dentro del archivo
    val vw: Int = 0,       // ancho visible del vídeo/foto
    val vh: Int = 0,
    val fadeInMs: Long = 0,
    val fadeOutMs: Long = 0,
    val speed: Float = 1f,     // 0.25 .. 4
    val rotation: Int = 0,     // 0, 90, 180, 270 (sentido horario)
    val zoom: Int = 0,         // 0 ninguno, 1 zoom in, 2 zoom out
    val fitMode: Int = 0,      // 0 normal, 1 vertical->horizontal con barras, 2 rellenando
    val vfx: Int = 0,          // preset de vídeo (ver Presets.kt)
    val afx: Int = 0           // preset de audio (ver Presets.kt)
) {
    /** Duración en la línea de tiempo (ya con la velocidad aplicada). */
    val lengthMs: Long get() = ((outMs - inMs) / speed).toLong()
    val endMs: Long get() = startMs + lengthMs

    fun toMediaItem(): MediaItem =
        if (isImage) MediaItem.fromUri(uri)
        else MediaItem.Builder()
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
    val clips: List<Clip> = emptyList(),   // ordenados por startMs, sin solaparse
    val hidden: Boolean = false,           // ocultar imagen (solo pistas de vídeo)
    val muted: Boolean = false             // silenciar audio
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
    val sizePct: Int = 6,                       // % de la altura del vídeo
    val position: Int = 0,                      // 0 abajo, 1 centro, 2 arriba
    val bgOn: Boolean = true,
    val bgColor: Int = 0xFF000000.toInt(),
    val bgOpacity: Int = 60,                    // 0..100 %
    val outlineOn: Boolean = false,
    val outlineColor: Int = 0xFF000000.toInt(),
    val shadowOn: Boolean = false,
    val shadowColor: Int = 0xFF000000.toInt(),
    val track: Int = 0                          // 0 = pista S1 (manual), 1 = pista S2 (automática)
)

class EditorState {
    var tracks by mutableStateOf(listOf(Track(1, Kind.VIDEO, "V1"), Track(2, Kind.AUDIO, "A1")))
    var subtitles by mutableStateOf(listOf<Subtitle>())
    var selectedClipId by mutableStateOf<Long?>(null)
    var selectedTrackId by mutableStateOf<Long?>(1L)
    var selectedSubId by mutableStateOf<Long?>(null)
    var playheadMs by mutableStateOf(0L)
    var playing by mutableStateOf(false)
    var zoom by mutableStateOf(0.04f)        // dp por milisegundo (zoom de la línea de tiempo)
    var projectName by mutableStateOf<String?>(null)
    var draggingClipId by mutableStateOf<Long?>(null)   // clip que se está arrastrando
    var notice by mutableStateOf<String?>(null)          // aviso para mostrar al usuario
    var cutMode by mutableStateOf(false)      // modo cortar: tocar un clip lo divide ahí
    var fitTick by mutableStateOf(0)          // se incrementa para pedir "ver todo el proyecto"

    // ---------- historial (deshacer / rehacer) ----------
    private class Snap(val tracks: List<Track>, val subtitles: List<Subtitle>)
    private val undoStack = ArrayDeque<Snap>()
    private val redoStack = ArrayDeque<Snap>()
    private var committed = Snap(tracks, subtitles)
    var undoCount by mutableStateOf(0)
    var redoCount by mutableStateOf(0)

    private var nextId = 100L
    private var vCount = 1
    private var aCount = 1
    private fun newId() = nextId++

    // ---------- consultas ----------
    fun totalMs(): Long = maxOf(
        tracks.maxOfOrNull { t -> t.clips.maxOfOrNull { it.endMs } ?: 0L } ?: 0L,
        subtitles.maxOfOrNull { it.endMs } ?: 0L
    )
    /** Pistas en el orden en que se dibujan: vídeo (la más nueva arriba) y luego audio. */
    fun displayRows(): List<Track> =
        tracks.filter { it.kind == Kind.VIDEO }.reversed() + tracks.filter { it.kind == Kind.AUDIO }
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
        if (tracks.count { it.kind == t.kind } <= 1) return
        tracks = tracks.filter { it.id != t.id }
        if (t.clips.any { it.id == selectedClipId }) selectedClipId = null
        selectedTrackId = tracks.firstOrNull { it.kind == t.kind }?.id
    }

    fun toggleHidden(id: Long) { tracks = tracks.map { if (it.id == id) it.copy(hidden = !it.hidden) else it } }
    fun toggleMuted(id: Long) { tracks = tracks.map { if (it.id == id) it.copy(muted = !it.muted) else it } }

    // ---------- importar ----------
    fun importMedia(ctx: Context, uri: Uri, targetTrackId: Long? = null) {
        var name = "clip"
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) name = c.getString(i)
        }
        val mime = ctx.contentResolver.getType(uri) ?: ""
        if (mime.startsWith("image/")) {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            try {
                ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            } catch (_: Exception) {}
            addClip(Kind.VIDEO, Clip(
                id = newId(), uri = uri, name = name, hasVideo = true, hasAudio = false, isImage = true,
                srcMs = 3_600_000L, startMs = 0L, inMs = 0L, outMs = 5000L, vw = o.outWidth, vh = o.outHeight),
                targetTrackId)
            return
        }
        val mmr = MediaMetadataRetriever()
        var dur = 0L; var hasVideo = false; var hasAudio = false; var w = 0; var h = 0
        try {
            mmr.setDataSource(ctx, uri)
            dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            hasVideo = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            hasAudio = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
        } catch (_: Exception) {
        } finally { mmr.release() }
        if (dur <= 0) return
        addClip(if (hasVideo) Kind.VIDEO else Kind.AUDIO, Clip(
            id = newId(), uri = uri, name = name, hasVideo = hasVideo, hasAudio = hasAudio, isImage = false,
            srcMs = dur, startMs = 0L, inMs = 0L, outMs = dur, vw = w, vh = h),
            targetTrackId)
    }

    private fun addClip(kind: Kind, clip: Clip, targetTrackId: Long? = null) {
        val target = tracks.firstOrNull { it.id == targetTrackId && it.kind == kind }
            ?: tracks.firstOrNull { it.id == selectedTrackId && it.kind == kind }
            ?: tracks.first { it.kind == kind }
        val start = target.clips.maxOfOrNull { it.endMs } ?: 0L
        val c = clip.copy(startMs = start)
        tracks = tracks.map { if (it.id == target.id) it.copy(clips = it.clips + c) else it }
        selectClip(c.id)
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

    /** Mueve un clip a otra pista del mismo tipo (shift = filas hacia abajo, negativo = hacia arriba). */
    fun moveClipToRow(id: Long, shift: Int) {
        if (shift == 0) return
        val rows = displayRows()
        val from = rows.indexOfFirst { t -> t.clips.any { it.id == id } }
        if (from < 0) return
        val src = rows[from]
        var to = (from + shift).coerceIn(0, rows.lastIndex)
        val step = if (to > from) -1 else 1
        while (to != from && rows[to].kind != src.kind) to += step
        if (to == from) {
            notice = "Solo puedes mover un clip a otra pista del mismo tipo (vídeo con vídeo, audio con audio)."
            return
        }
        val dst = rows[to]
        val c = src.clips.first { it.id == id }
        if (dst.clips.any { it.startMs < c.endMs && it.endMs > c.startMs }) {
            notice = "No cabe: en esa pista ya hay un clip en ese tramo de tiempo. Muévelo antes a un hueco libre."
            return
        }
        tracks = tracks.map { t ->
            when (t.id) {
                src.id -> t.copy(clips = t.clips.filter { it.id != id })
                dst.id -> t.copy(clips = (t.clips + c).sortedBy { it.startMs })
                else -> t
            }
        }
        selectClip(id)
    }

    fun dragClip(id: Long, deltaMs: Long) = mutateClip(id) { c, minS, maxE ->
        val ns = (c.startMs + deltaMs).coerceIn(minS, maxOf(minS, maxE - c.lengthMs))
        c.copy(startMs = ns)
    }

    fun trimStart(d: Long) {
        val id = selectedClipId ?: return
        mutateClip(id) { c, minS, _ ->
            val lo = minOf(0L, maxOf((-c.inMs / c.speed).toLong(), minS - c.startMs))
            val hi = maxOf(0L, c.lengthMs - 500)
            val dd = d.coerceIn(lo, hi)
            c.copy(inMs = c.inMs + (dd * c.speed).toLong(), startMs = c.startMs + dd)
        }
    }

    fun trimEnd(d: Long) {
        val id = selectedClipId ?: return
        mutateClip(id) { c, _, maxE ->
            val lo = -maxOf(0L, c.lengthMs - 500)
            val hi = minOf(((c.srcMs - c.outMs) / c.speed).toLong(), maxE - c.endMs)
            val dd = d.coerceIn(lo, maxOf(lo, hi))
            c.copy(outMs = c.outMs + (dd * c.speed).toLong())
        }
    }

    /** Divide un clip en el instante "at" de la línea de tiempo y mueve el cursor allí. */
    fun splitAt(id: Long, at: Long) {
        var done = false
        tracks = tracks.map { t ->
            val i = t.clips.indexOfFirst { it.id == id }
            if (i < 0) t else {
                val c = t.clips[i]
                if (at <= c.startMs + 100 || at >= c.endMs - 100) t else {
                    val cut = c.inMs + ((at - c.startMs) * c.speed).toLong()
                    val left = c.copy(outMs = cut, fadeOutMs = 0)
                    val right = c.copy(id = newId(), startMs = at, inMs = cut, fadeInMs = 0)
                    val list = t.clips.toMutableList()
                    list[i] = left; list.add(i + 1, right)
                    done = true
                    t.copy(clips = list)
                }
            }
        }
        if (done) { playheadMs = at; selectClip(id) } else selectClip(id)
    }

    /** Divide el clip seleccionado en el cursor. */
    fun split() {
        val id = selectedClipId ?: return
        splitAt(id, playheadMs)
    }

    /** Zoom de la línea de tiempo con un límite para que el ancho no desborde. */
    fun zoomBy(f: Float) {
        val total = maxOf(totalMs() + 5000, 15_000L)
        val maxZ = minOf(1.0f, 150_000f / (total * 3f))
        zoom = (zoom * f).coerceIn(0.005f, maxOf(0.005f, maxZ))
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

    // ---------- efectos ----------
    fun setSpeed(f: Float) {
        val id = selectedClipId ?: return
        tracks = tracks.map { t ->
            val i = t.clips.indexOfFirst { it.id == id }
            if (i < 0 || t.clips[i].isImage) t else {
                val c = t.clips[i]
                val nc = c.copy(speed = f)
                val delta = nc.lengthMs - c.lengthMs
                t.copy(clips = t.clips.mapIndexed { j, x ->
                    when {
                        j == i -> nc
                        j > i -> x.copy(startMs = maxOf(0L, x.startMs + delta))
                        else -> x
                    }
                })
            }
        }
    }

    fun rotate90() {
        val id = selectedClipId ?: return
        mutateClip(id) { c, _, _ -> if (c.hasVideo) c.copy(rotation = (c.rotation + 90) % 360) else c }
    }

    fun setFit(mode: Int) {
        val id = selectedClipId ?: return
        mutateClip(id) { c, _, _ -> if (c.hasVideo) c.copy(fitMode = mode) else c }
    }

    fun setVfx(p: Int) {
        val id = selectedClipId ?: return
        mutateClip(id) { c, _, _ -> if (c.hasVideo) c.copy(vfx = p) else c }
    }

    fun setAfx(p: Int) {
        val id = selectedClipId ?: return
        mutateClip(id) { c, _, _ -> if (c.hasAudio) c.copy(afx = p) else c }
    }

    fun setZoom(mode: Int) {
        val id = selectedClipId ?: return
        mutateClip(id) { c, _, _ -> if (c.hasVideo) c.copy(zoom = mode) else c }
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

    // ---------- deshacer / rehacer ----------
    /** Registra como un paso del historial los cambios hechos desde la última vez (se llama tras una pausa de 0,4 s). */
    fun commitIfChanged() {
        if (tracks != committed.tracks || subtitles != committed.subtitles) {
            undoStack.addLast(committed)
            if (undoStack.size > 100) undoStack.removeFirst()
            redoStack.clear()
            committed = Snap(tracks, subtitles)
            undoCount = undoStack.size
            redoCount = 0
        }
    }

    private fun restore(snap: Snap) {
        tracks = snap.tracks
        subtitles = snap.subtitles
        committed = Snap(tracks, subtitles)
        if (selectedClipId != null && tracks.none { t -> t.clips.any { it.id == selectedClipId } }) selectedClipId = null
        if (selectedSubId != null && subtitles.none { it.id == selectedSubId }) selectedSubId = null
        if (tracks.none { it.id == selectedTrackId }) selectedTrackId = tracks.firstOrNull()?.id
        undoCount = undoStack.size
        redoCount = redoStack.size
    }

    fun undo() {
        commitIfChanged()
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(Snap(tracks, subtitles))
        restore(prev)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(Snap(tracks, subtitles))
        restore(next)
    }

    private fun resetHistory() {
        undoStack.clear()
        redoStack.clear()
        committed = Snap(tracks, subtitles)
        undoCount = 0
        redoCount = 0
    }

    // ---------- proyectos ----------
    fun newProject() {
        tracks = listOf(Track(1, Kind.VIDEO, "V1"), Track(2, Kind.AUDIO, "A1"))
        subtitles = emptyList()
        selectedClipId = null; selectedSubId = null; selectedTrackId = 1L
        playheadMs = 0L; playing = false; zoom = 0.04f
        projectName = null
        nextId = 100L; vCount = 1; aCount = 1
        resetHistory()
    }

    fun toJson(withPlayhead: Boolean = true): JSONObject {
        val o = JSONObject()
        o.put("version", 1)
        o.put("zoom", zoom.toDouble())
        if (withPlayhead) o.put("playheadMs", playheadMs)
        o.put("nextId", nextId)
        val ta = JSONArray()
        for (t in tracks) {
            val to = JSONObject()
            to.put("id", t.id); to.put("kind", t.kind.name); to.put("name", t.name)
            to.put("hidden", t.hidden); to.put("muted", t.muted)
            val ca = JSONArray()
            for (c in t.clips) {
                val co = JSONObject()
                co.put("id", c.id); co.put("uri", c.uri.toString()); co.put("name", c.name)
                co.put("hasVideo", c.hasVideo); co.put("hasAudio", c.hasAudio); co.put("isImage", c.isImage)
                co.put("srcMs", c.srcMs); co.put("startMs", c.startMs); co.put("inMs", c.inMs); co.put("outMs", c.outMs)
                co.put("vw", c.vw); co.put("vh", c.vh)
                co.put("fadeInMs", c.fadeInMs); co.put("fadeOutMs", c.fadeOutMs)
                co.put("speed", c.speed.toDouble()); co.put("rotation", c.rotation)
                co.put("zoom", c.zoom); co.put("fitMode", c.fitMode)
                co.put("vfx", c.vfx); co.put("afx", c.afx)
                ca.put(co)
            }
            to.put("clips", ca)
            ta.put(to)
        }
        o.put("tracks", ta)
        val sa = JSONArray()
        for (x in subtitles) {
            val so = JSONObject()
            so.put("id", x.id); so.put("startMs", x.startMs); so.put("endMs", x.endMs); so.put("text", x.text)
            so.put("font", x.font); so.put("bold", x.bold); so.put("italic", x.italic)
            so.put("color", x.color); so.put("sizePct", x.sizePct); so.put("position", x.position)
            so.put("bgOn", x.bgOn); so.put("bgColor", x.bgColor); so.put("bgOpacity", x.bgOpacity)
            so.put("outlineOn", x.outlineOn); so.put("outlineColor", x.outlineColor)
            so.put("shadowOn", x.shadowOn); so.put("shadowColor", x.shadowColor)
            so.put("track", x.track)
            sa.put(so)
        }
        o.put("subtitles", sa)
        return o
    }

    /** Carga un proyecto. Devuelve los nombres de los archivos que ya no existen (se omiten). */
    fun loadJson(o: JSONObject, canOpen: (Uri) -> Boolean): List<String> {
        val missing = mutableListOf<String>()
        val tl = mutableListOf<Track>()
        val ta = o.getJSONArray("tracks")
        for (i in 0 until ta.length()) {
            val t = ta.getJSONObject(i)
            val clips = mutableListOf<Clip>()
            val ca = t.getJSONArray("clips")
            for (j in 0 until ca.length()) {
                val c = ca.getJSONObject(j)
                val uri = Uri.parse(c.getString("uri"))
                if (!canOpen(uri)) { missing += c.optString("name", "archivo"); continue }
                clips += Clip(
                    id = c.getLong("id"), uri = uri, name = c.optString("name", "clip"),
                    hasVideo = c.getBoolean("hasVideo"), hasAudio = c.getBoolean("hasAudio"),
                    isImage = c.optBoolean("isImage", false),
                    srcMs = c.getLong("srcMs"), startMs = c.getLong("startMs"),
                    inMs = c.getLong("inMs"), outMs = c.getLong("outMs"),
                    vw = c.optInt("vw", 0), vh = c.optInt("vh", 0),
                    fadeInMs = c.optLong("fadeInMs", 0L), fadeOutMs = c.optLong("fadeOutMs", 0L),
                    speed = c.optDouble("speed", 1.0).toFloat(), rotation = c.optInt("rotation", 0),
                    zoom = c.optInt("zoom", 0), fitMode = c.optInt("fitMode", 0),
                    vfx = c.optInt("vfx", 0), afx = c.optInt("afx", 0))
            }
            tl += Track(
                id = t.getLong("id"), kind = Kind.valueOf(t.getString("kind")), name = t.getString("name"),
                clips = clips.sortedBy { it.startMs },
                hidden = t.optBoolean("hidden", false), muted = t.optBoolean("muted", false))
        }
        val sl = mutableListOf<Subtitle>()
        val sa = o.optJSONArray("subtitles")
        if (sa != null) for (i in 0 until sa.length()) {
            val x = sa.getJSONObject(i)
            sl += Subtitle(
                id = x.getLong("id"), startMs = x.getLong("startMs"), endMs = x.getLong("endMs"),
                text = x.optString("text", ""), font = x.optString("font", "sans-serif"),
                bold = x.optBoolean("bold", false), italic = x.optBoolean("italic", false),
                color = x.optInt("color", 0xFFFFFFFF.toInt()), sizePct = x.optInt("sizePct", 6),
                position = x.optInt("position", 0), bgOn = x.optBoolean("bgOn", true),
                bgColor = x.optInt("bgColor", 0xFF000000.toInt()), bgOpacity = x.optInt("bgOpacity", 60),
                outlineOn = x.optBoolean("outlineOn", false), outlineColor = x.optInt("outlineColor", 0xFF000000.toInt()),
                shadowOn = x.optBoolean("shadowOn", false), shadowColor = x.optInt("shadowColor", 0xFF000000.toInt()),
                track = x.optInt("track", 0))
        }

        var maxId = 100L
        for (t in tl) { maxId = maxOf(maxId, t.id); for (c in t.clips) maxId = maxOf(maxId, c.id) }
        for (x in sl) maxId = maxOf(maxId, x.id)
        nextId = maxOf(o.optLong("nextId", 100L), maxId + 1)
        vCount = tl.filter { it.kind == Kind.VIDEO }.maxOfOrNull { it.name.drop(1).toIntOrNull() ?: 1 } ?: 1
        aCount = tl.filter { it.kind == Kind.AUDIO }.maxOfOrNull { it.name.drop(1).toIntOrNull() ?: 1 } ?: 1
        if (tl.none { it.kind == Kind.VIDEO }) tl.add(0, Track(newId(), Kind.VIDEO, "V1"))
        if (tl.none { it.kind == Kind.AUDIO }) tl.add(Track(newId(), Kind.AUDIO, "A1"))

        tracks = tl
        subtitles = sl
        zoom = o.optDouble("zoom", 0.04).toFloat().coerceIn(0.005f, 0.4f)
        playheadMs = o.optLong("playheadMs", 0L)
        playing = false
        selectedClipId = null
        selectedSubId = null
        selectedTrackId = tl.first().id
        resetHistory()
        return missing
    }

    // ---------- subtitulado automático ----------
    fun autoSubCount(): Int = subtitles.count { it.track == 1 }
    fun deleteAutoSubs() { subtitles = subtitles.filter { it.track != 1 } }

    /**
     * Crea los subtítulos en la pista S2 y, si cut es true, recorta los tramos sin voz
     * del clip (solo en la pista del clip; el resto de pistas no se mueven).
     */
    fun applyAutoSubtitles(clipId: Long, words: List<AWord>, cut: Boolean): String {
        val t = tracks.firstOrNull { tr -> tr.clips.any { it.id == clipId } } ?: return "No se encontró el clip."
        val i = t.clips.indexOfFirst { it.id == clipId }
        val c = t.clips[i]
        val inW = words.filter { it.endMs > c.inMs && it.startMs < c.outMs }
        if (inW.isEmpty()) return "No se detectó voz en este clip."

        val segs: List<Pair<Long, Long>> =
            if (cut) Speech.segments(inW, c.inMs, c.outMs) else listOf(Pair(c.inMs, c.outMs))
        var pos = c.startMs
        val parts = segs.mapIndexed { k, seg ->
            val nc = c.copy(
                id = if (k == 0) c.id else newId(), startMs = pos, inMs = seg.first, outMs = seg.second,
                fadeInMs = if (k == 0) c.fadeInMs else 0L,
                fadeOutMs = if (k == segs.lastIndex) c.fadeOutMs else 0L)
            pos += nc.lengthMs
            nc
        }
        val delta = pos - c.endMs

        fun mapT(srcMs: Long): Long {
            val k = parts.indexOfFirst { srcMs >= it.inMs && srcMs <= it.outMs }
            val p = if (k >= 0) parts[k]
                    else parts.minByOrNull { minOf(abs(srcMs - it.inMs), abs(srcMs - it.outMs)) }!!
            return p.startMs + ((srcMs.coerceIn(p.inMs, p.outMs) - p.inMs) / p.speed).toLong()
        }

        val newList = t.clips.take(i) + parts + t.clips.drop(i + 1).map { it.copy(startMs = maxOf(0L, it.startMs + delta)) }
        tracks = tracks.map { if (it.id == t.id) it.copy(clips = newList) else it }

        val oldStart = c.startMs
        val oldEnd = c.endMs
        val kept = subtitles.filter { !(it.track == 1 && it.startMs < oldEnd && it.endMs > oldStart) }
        val lines = Speech.lines(inW)
        val made = mutableListOf<Subtitle>()
        for (ws in lines) {
            val s0 = mapT(ws.first().startMs)
            val e0 = maxOf(mapT(ws.last().endMs) + 150, s0 + 400)
            made += Subtitle(newId(), s0, e0, ws.joinToString(" ") { it.text }, track = 1)
        }
        for (k in 0 until made.size - 1) {
            if (made[k].endMs > made[k + 1].startMs) made[k] = made[k].copy(endMs = maxOf(made[k].startMs + 200, made[k + 1].startMs))
        }
        subtitles = kept + made
        selectClip(c.id)
        val cutInfo = if (cut) " Silencio recortado: ${"%.1f".format(-delta / 1000.0)} s." else ""
        return "Listo: ${made.size} subtítulos en la pista S2.$cutInfo"
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
