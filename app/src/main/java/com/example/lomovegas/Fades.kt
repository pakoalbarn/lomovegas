package com.example.lomovegas

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.C
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import java.nio.ByteBuffer

/** Ganancia 0..1 de un fundido de entrada/salida. rMs = tiempo dentro del clip. */
fun fadeGain(rMs: Long, lenMs: Long, inMs: Long, outMs: Long): Float {
    var g = 1f
    if (inMs > 0 && rMs < inMs) g = minOf(g, rMs.toFloat() / inMs)
    if (outMs > 0 && lenMs - rMs < outMs) g = minOf(g, (lenMs - rMs).toFloat() / outMs)
    return g.coerceIn(0f, 1f)
}

// ---------- Fundido de vídeo (a negro) ----------
class FadeEffect(private val inMs: Long, private val outMs: Long, private val lenMs: Long) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        FadeShader(useHdr, inMs, outMs, lenMs)
}

class FadeShader(useHdr: Boolean, private val inMs: Long, private val outMs: Long, private val lenMs: Long) :
    BaseGlShaderProgram(useHdr, 1) {

    private val program = GlProgram(VERTEX, FRAGMENT)

    init {
        program.setBufferAttribute(
            "aFramePosition",
            GlUtil.getNormalizedCoordinateBounds(),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
        )
        program.setFloatsUniform("uTexTransformationMatrix", GlUtil.create4x4IdentityMatrix())
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        program.use()
        program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
        program.setFloatUniform("uGain", fadeGain(presentationTimeUs / 1000, lenMs, inMs, outMs))
        program.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    override fun release() {
        super.release()
        program.delete()
    }

    companion object {
        private const val VERTEX = """
attribute vec4 aFramePosition;
uniform mat4 uTexTransformationMatrix;
varying vec2 vTexSamplingCoord;
void main() {
  gl_Position = aFramePosition;
  vTexSamplingCoord = (uTexTransformationMatrix *
      vec4(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5, 0.0, 1.0)).xy;
}"""
        private const val FRAGMENT = """
precision mediump float;
uniform sampler2D uTexSampler;
uniform float uGain;
varying vec2 vTexSamplingCoord;
void main() {
  vec4 c = texture2D(uTexSampler, vTexSamplingCoord);
  gl_FragColor = vec4(c.rgb * uGain, c.a);
}"""
    }
}

// ---------- Fundido de audio ----------
class FadeAudio(private val inMs: Long, private val outMs: Long, private val lenMs: Long) : BaseAudioProcessor() {
    private var frames = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT)
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        return inputAudioFormat
    }

    override fun onFlush() { frames = 0 }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val ch = inputAudioFormat.channelCount
        val rate = inputAudioFormat.sampleRate
        val out = replaceOutputBuffer(inputBuffer.remaining())
        while (inputBuffer.remaining() >= 2 * ch) {
            val g = fadeGain(frames * 1000 / rate, lenMs, inMs, outMs)
            for (i in 0 until ch) out.putShort((inputBuffer.getShort() * g).toInt().toShort())
            frames++
        }
        out.flip()
    }
}
