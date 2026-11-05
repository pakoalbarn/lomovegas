package com.example.lomovegas

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/** Efectos de vídeo con shaders: blanco y negro, sepia, negativo, ondular, abombado, espejo, colores vivos, glitch. */
class VideoFxEffect(private val preset: Int) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        VideoFxShader(useHdr, preset)
}

class VideoFxShader(useHdr: Boolean, private val preset: Int) : BaseGlShaderProgram(useHdr, 1) {

    private val needsTime = preset == 4 || preset == 8
    private val program = GlProgram(VERTEX, fragmentFor(preset))

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
        if (needsTime) program.setFloatUniform("uTime", (presentationTimeUs / 1_000_000.0).toFloat())
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

        private fun fragmentFor(p: Int): String {
            val head = "precision mediump float;\n" +
                "uniform sampler2D uTexSampler;\n" +
                "varying vec2 vTexSamplingCoord;\n" +
                (if (p == 4 || p == 8) "uniform float uTime;\n" else "")
            val body = when (p) {
                1 -> """
vec4 c = texture2D(uTexSampler, vTexSamplingCoord);
float g = dot(c.rgb, vec3(0.299, 0.587, 0.114));
gl_FragColor = vec4(vec3(g), c.a);"""
                2 -> """
vec4 c = texture2D(uTexSampler, vTexSamplingCoord);
vec3 s = vec3(dot(c.rgb, vec3(0.393, 0.769, 0.189)),
              dot(c.rgb, vec3(0.349, 0.686, 0.168)),
              dot(c.rgb, vec3(0.272, 0.534, 0.131)));
gl_FragColor = vec4(min(s, vec3(1.0)), c.a);"""
                3 -> """
vec4 c = texture2D(uTexSampler, vTexSamplingCoord);
gl_FragColor = vec4(vec3(1.0) - c.rgb, c.a);"""
                4 -> """
vec2 uv = vTexSamplingCoord;
uv.x += 0.02 * sin(uv.y * 24.0 + uTime * 4.0);
uv.y += 0.012 * sin(uv.x * 18.0 + uTime * 3.0);
gl_FragColor = texture2D(uTexSampler, clamp(uv, 0.0, 1.0));"""
                5 -> """
vec2 p = vTexSamplingCoord - vec2(0.5);
float d = clamp(length(p) * 2.0, 0.0001, 1.0);
vec2 q = p * pow(d, 0.55) + vec2(0.5);
gl_FragColor = texture2D(uTexSampler, q);"""
                6 -> """
vec2 uv = vTexSamplingCoord;
if (uv.x > 0.5) { uv.x = 1.0 - uv.x; }
gl_FragColor = texture2D(uTexSampler, uv);"""
                7 -> """
vec4 c = texture2D(uTexSampler, vTexSamplingCoord);
float g = dot(c.rgb, vec3(0.299, 0.587, 0.114));
vec3 v = mix(vec3(g), c.rgb, 1.8);
gl_FragColor = vec4(clamp(v, 0.0, 1.0), c.a);"""
                8 -> """
vec2 uv = vTexSamplingCoord;
float j = 0.008 + 0.006 * sin(uTime * 7.0);
float r = texture2D(uTexSampler, clamp(uv + vec2(j, 0.0), 0.0, 1.0)).r;
float g = texture2D(uTexSampler, uv).g;
float b = texture2D(uTexSampler, clamp(uv - vec2(j, 0.0), 0.0, 1.0)).b;
gl_FragColor = vec4(r, g, b, 1.0);"""
                else -> """
gl_FragColor = texture2D(uTexSampler, vTexSamplingCoord);"""
            }
            return head + "void main() {" + body + "\n}"
        }
    }
}
