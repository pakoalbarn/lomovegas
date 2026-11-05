package com.example.lomovegas

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.nio.ByteOrder

/** Palabra reconocida. Los tiempos son del archivo original (no de la línea de tiempo). */
data class AWord(val text: String, val startMs: Long, val endMs: Long)

/** Carga el modelo de voz (carpeta assets/model-es) copiándolo una vez al almacenamiento de la app. */
object ModelStore {
    private const val ASSET_DIR = "model-es"
    private var model: Model? = null

    fun available(ctx: Context): Boolean = (ctx.assets.list(ASSET_DIR)?.isNotEmpty() == true)

    @Synchronized
    fun get(ctx: Context): Model? {
        model?.let { return it }
        if (!available(ctx)) return null
        val target = File(ctx.filesDir, ASSET_DIR)
        val marker = File(target, ".ok")
        if (!marker.exists()) {
            target.deleteRecursively()
            copyAssets(ctx, ASSET_DIR, target)
            marker.writeText("ok")
        }
        val m = Model(target.absolutePath)
        model = m
        return m
    }

    private fun copyAssets(ctx: Context, path: String, dest: File) {
        val children = ctx.assets.list(path) ?: emptyArray()
        if (children.isEmpty()) {
            dest.parentFile?.mkdirs()
            try {
                ctx.assets.open(path).use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
            } catch (_: Exception) {
                dest.mkdirs()   // carpeta vacía
            }
        } else {
            dest.mkdirs()
            for (n in children) copyAssets(ctx, "$path/$n", File(dest, n))
        }
    }
}

class DecodeInfo { @Volatile var firstPtsMs: Long = -1L }

/** Decodifica el audio de un archivo a PCM de 16 kHz, mono, 16 bits, que es lo que pide el reconocedor. */
object AudioDecoder {
    fun decode(
        ctx: Context, uri: Uri, startMs: Long, endMs: Long, info: DecodeInfo,
        onProgress: (Float) -> Unit, cancelled: () -> Boolean, onPcm: (ShortArray, Int) -> Unit
    ): Boolean {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(ctx, uri, null)
            var track = -1
            var fmt: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) { track = i; fmt = f; break }
            }
            if (track < 0 || fmt == null) return false
            ex.selectTrack(track)
            ex.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val c = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            codec = c
            c.configure(fmt, null, null, 0)
            c.start()

            var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val bi = MediaCodec.BufferInfo()
            val startUs = startMs * 1000
            val endUs = endMs * 1000
            val spanUs = maxOf(1L, endUs - startUs)
            var inDone = false
            var outDone = false

            // remuestreo lineal
            var nextPos = 0.0
            var consumed = 0L
            var prevLast: Short = 0
            val acc = ShortArray(8000)
            var accN = 0

            while (!outDone) {
                if (cancelled()) return false
                if (!inDone) {
                    val ii = c.dequeueInputBuffer(10_000)
                    if (ii >= 0) {
                        val buf = c.getInputBuffer(ii)!!
                        val n = ex.readSampleData(buf, 0)
                        val t = ex.sampleTime
                        if (n < 0 || t > endUs) {
                            c.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inDone = true
                        } else {
                            c.queueInputBuffer(ii, 0, n, t, 0)
                            ex.advance()
                        }
                    }
                }
                val oi = c.dequeueOutputBuffer(bi, 10_000)
                if (oi >= 0) {
                    if (bi.size > 0 && (bi.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        if (info.firstPtsMs < 0) info.firstPtsMs = bi.presentationTimeUs / 1000
                        val out = c.getOutputBuffer(oi)!!
                        out.position(bi.offset)
                        out.limit(bi.offset + bi.size)
                        val sb = out.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val frames = sb.remaining() / ch
                        val mono = ShortArray(frames)
                        for (f in 0 until frames) {
                            var sum = 0
                            for (k in 0 until ch) sum += sb.get().toInt()
                            mono[f] = (sum / ch).toShort()
                        }
                        val buf = ShortArray(frames + 1)
                        buf[0] = prevLast
                        System.arraycopy(mono, 0, buf, 1, frames)
                        val base = consumed - 1
                        val ratio = rate / 16000.0
                        while (true) {
                            val rel = nextPos - base
                            val idx = rel.toInt()
                            if (idx + 1 >= buf.size) break
                            val frac = rel - idx
                            acc[accN++] = (buf[idx] * (1.0 - frac) + buf[idx + 1] * frac).toInt().toShort()
                            if (accN == acc.size) { onPcm(acc, accN); accN = 0 }
                            nextPos += ratio
                        }
                        consumed += frames
                        if (frames > 0) prevLast = mono[frames - 1]
                        onProgress(((bi.presentationTimeUs - startUs).toFloat() / spanUs).coerceIn(0f, 1f))
                    }
                    c.releaseOutputBuffer(oi, false)
                    if ((bi.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outDone = true
                } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val nf = c.outputFormat
                    rate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    ch = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
            }
            if (accN > 0) onPcm(acc, accN)
            return true
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            ex.release()
        }
    }
}

object Transcriber {
    class Result(val words: List<AWord>, val error: String?)

    fun run(ctx: Context, clip: Clip, onProgress: (Float) -> Unit, cancelled: () -> Boolean): Result {
        val model: Model? = try {
            ModelStore.get(ctx)
        } catch (e: Exception) {
            return Result(emptyList(), "No se pudo cargar el modelo de voz: ${e.message}")
        }
        if (model == null) return Result(emptyList(),
            "Esta versión de la app no incluye el modelo de voz. Compílala con GitHub Actions (descarga el modelo automáticamente).")

        val rec = Recognizer(model, 16000f)
        rec.setWords(true)
        val words = mutableListOf<AWord>()
        val info = DecodeInfo()

        fun collect(json: String) {
            val arr = JSONObject(json).optJSONArray("result") ?: return
            val base = if (info.firstPtsMs >= 0) info.firstPtsMs else clip.inMs
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val w = o.optString("word")
                if (w.isNotEmpty())
                    words += AWord(w, base + (o.optDouble("start") * 1000).toLong(), base + (o.optDouble("end") * 1000).toLong())
            }
        }

        try {
            val ok = AudioDecoder.decode(ctx, clip.uri, clip.inMs, clip.outMs, info, onProgress, cancelled) { pcm, n ->
                val bytes = ByteArray(n * 2)
                for (i in 0 until n) {
                    val v = pcm[i].toInt()
                    bytes[2 * i] = (v and 0xFF).toByte()
                    bytes[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
                }
                if (rec.acceptWaveForm(bytes, bytes.size)) collect(rec.result)
            }
            if (!ok) return Result(emptyList(), if (cancelled()) null else "No se pudo leer el audio de este archivo.")
            collect(rec.finalResult)
        } catch (e: Exception) {
            return Result(emptyList(), "Error al analizar el audio: ${e.message}")
        } finally {
            rec.close()
        }
        return Result(words, null)
    }
}

object Speech {
    /** Tramos con voz (tiempos del archivo original). Los silencios largos quedan fuera. */
    fun segments(
        words: List<AWord>, inMs: Long, outMs: Long, gapMs: Long = 900, padMs: Long = 250
    ): List<Pair<Long, Long>> {
        val ws = words.filter { it.endMs > inMs && it.startMs < outMs }.sortedBy { it.startMs }
        if (ws.isEmpty()) return emptyList()
        val raw = mutableListOf<Pair<Long, Long>>()
        var cs = ws[0].startMs
        var ce = ws[0].endMs
        for (i in 1 until ws.size) {
            val w = ws[i]
            if (w.startMs - ce <= gapMs) ce = maxOf(ce, w.endMs)
            else { raw += Pair(cs, ce); cs = w.startMs; ce = w.endMs }
        }
        raw += Pair(cs, ce)
        val out = mutableListOf<Pair<Long, Long>>()
        for (r in raw) {
            val a = maxOf(inMs, r.first - padMs)
            val b = minOf(outMs, r.second + padMs)
            if (out.isNotEmpty() && a <= out.last().second)
                out[out.size - 1] = Pair(out.last().first, maxOf(out.last().second, b))
            else out += Pair(a, b)
        }
        return out
    }

    /** Agrupa palabras en líneas de subtítulo (pausa larga, 7 palabras o 42 caracteres). */
    fun lines(words: List<AWord>): List<List<AWord>> {
        val res = mutableListOf<List<AWord>>()
        var cur = mutableListOf<AWord>()
        var len = 0
        for (w in words.sortedBy { it.startMs }) {
            if (cur.isNotEmpty() && (w.startMs - cur.last().endMs > 600 || cur.size >= 7 || len + w.text.length > 42)) {
                res += cur; cur = mutableListOf(); len = 0
            }
            cur.add(w)
            len += w.text.length + 1
        }
        if (cur.isNotEmpty()) res += cur
        return res
    }
}
