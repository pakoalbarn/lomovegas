package com.example.lomovegas

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Efectos de audio: 5 distorsión, 6 robot, 7 eco, 8 teléfono. (Los de tono usan SonicAudioProcessor.) */
class FxAudio(private val preset: Int) : BaseAudioProcessor() {
    private var frames = 0L
    private var echoLen = 0
    private var echoPos = 0
    private var echoBuf = FloatArray(0)
    private var hpIn = FloatArray(0)
    private var hpOut = FloatArray(0)
    private var lp1 = FloatArray(0)
    private var lp2 = FloatArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT)
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        return inputAudioFormat
    }

    override fun onFlush() {
        frames = 0
        val ch = inputAudioFormat.channelCount
        val rate = inputAudioFormat.sampleRate
        if (ch <= 0 || rate <= 0) return
        echoLen = (rate * 0.25f).toInt().coerceAtLeast(1)
        echoPos = 0
        echoBuf = FloatArray(echoLen * ch)
        hpIn = FloatArray(ch); hpOut = FloatArray(ch); lp1 = FloatArray(ch); lp2 = FloatArray(ch)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val ch = inputAudioFormat.channelCount
        val rate = inputAudioFormat.sampleRate
        val out = replaceOutputBuffer(inputBuffer.remaining())
        val dt = 1f / rate
        val rcHp = 1f / (2f * PI.toFloat() * 400f)
        val aHp = rcHp / (rcHp + dt)
        val rcLp = 1f / (2f * PI.toFloat() * 3000f)
        val aLp = dt / (rcLp + dt)
        val w = 2.0 * PI * 55.0 / rate

        while (inputBuffer.remaining() >= 2 * ch) {
            val mod = sin(w * frames).toFloat()
            for (i in 0 until ch) {
                var x = inputBuffer.getShort() / 32768f
                when (preset) {
                    5 -> {
                        val v = x * 7f
                        x = (v / (1f + abs(v))) * 0.85f
                    }
                    6 -> x = x * mod * 1.5f
                    7 -> {
                        if (echoBuf.isNotEmpty()) {
                            val idx = echoPos * ch + i
                            val y = x + 0.45f * echoBuf[idx]
                            echoBuf[idx] = y
                            x = y * 0.8f
                        }
                    }
                    8 -> {
                        if (hpIn.size > i) {
                            val y = aHp * (hpOut[i] + x - hpIn[i])
                            hpIn[i] = x
                            hpOut[i] = y
                            lp1[i] += aLp * (y - lp1[i])
                            lp2[i] += aLp * (lp1[i] - lp2[i])
                            x = lp2[i] * 1.8f
                        }
                    }
                }
                out.putShort((x.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            }
            if (echoLen > 0) echoPos = (echoPos + 1) % echoLen
            frames++
        }
        out.flip()
    }
}
