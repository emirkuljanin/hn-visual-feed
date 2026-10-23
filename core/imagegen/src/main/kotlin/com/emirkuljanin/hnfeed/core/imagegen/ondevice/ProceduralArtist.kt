package com.emirkuljanin.hnfeed.core.imagegen.ondevice

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import com.emirkuljanin.hnfeed.core.imagegen.THUMBNAIL_HEIGHT
import com.emirkuljanin.hnfeed.core.imagegen.THUMBNAIL_WIDTH
import java.util.zip.CRC32
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Paints abstract art from a vector: a two-colour gradient sky, soft glowing orbs and a wave, with palette,
 * angles, positions and sizes all read from the vector. The mapping is continuous, so nearby vectors (titles with
 * similar meaning, per the embedder) produce visually similar pictures, and it is deterministic: the same vector
 * always gives the same pixels. It cannot depict anything, which makes it the safe fallback for any title.
 */
internal class ProceduralArtist @Inject constructor() {

    fun draw(vector: FloatArray): Bitmap {
        val params = Params(vector)
        val bitmap = createBitmap(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
        val canvas = Canvas(bitmap)
        val palette = palette(params)

        drawSky(canvas, palette, params)
        repeat(ORB_COUNT) { drawOrb(canvas, palette[(it + 1) % palette.size], params, it) }
        drawWave(canvas, palette.last(), params)
        return bitmap
    }

    private fun palette(params: Params): List<Int> {
        val baseHue = params.hue
        // Harmony from analogous (30°) to complementary (180°), chosen by the vector.
        val spread = 30f + 150f * params[1]
        val saturation = 0.45f + 0.3f * params[2]
        return listOf(
            hsv(baseHue, saturation * 0.9f, 0.32f + 0.15f * params[3]),
            hsv(baseHue + spread * 0.5f, saturation, 0.85f),
            hsv(baseHue + spread, saturation, 0.95f),
            hsv(baseHue - spread * 0.5f, saturation * 0.8f, 0.9f),
            hsv(baseHue + 180f, saturation * 0.6f, 0.98f),
        )
    }

    private fun drawSky(canvas: Canvas, palette: List<Int>, params: Params) {
        val angle = 2 * PI * params[4]
        val cx = THUMBNAIL_WIDTH / 2f
        val cy = THUMBNAIL_HEIGHT / 2f
        val dx = cos(angle).toFloat() * cx
        val dy = sin(angle).toFloat() * cy
        val paint = Paint().apply {
            shader = LinearGradient(cx - dx, cy - dy, cx + dx, cy + dy, palette[0], palette[2], Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, THUMBNAIL_WIDTH.toFloat(), THUMBNAIL_HEIGHT.toFloat(), paint)
    }

    private fun drawOrb(canvas: Canvas, color: Int, params: Params, index: Int) {
        val offset = ORB_PARAMS_START + index * 4
        val x = THUMBNAIL_WIDTH * params[offset]
        val y = THUMBNAIL_HEIGHT * params[offset + 1]
        val radius = THUMBNAIL_HEIGHT * (0.2f + 0.5f * params[offset + 2])
        val alpha = (90 + 110 * params[offset + 3]).toInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                x,
                y,
                radius,
                Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(x, y, radius, paint)
    }

    private fun drawWave(canvas: Canvas, color: Int, params: Params) {
        val baseline = THUMBNAIL_HEIGHT * (0.6f + 0.25f * params[5])
        val amplitude = THUMBNAIL_HEIGHT * (0.04f + 0.1f * params[6])
        val waves = 1f + 2f * params[7]
        val phase = 2 * PI * params[8]
        val path = Path().apply {
            moveTo(0f, THUMBNAIL_HEIGHT.toFloat())
            for (x in 0..THUMBNAIL_WIDTH step 8) {
                val t = x.toDouble() / THUMBNAIL_WIDTH
                lineTo(x.toFloat(), baseline + amplitude * sin(2 * PI * waves * t + phase).toFloat())
            }
            lineTo(THUMBNAIL_WIDTH.toFloat(), THUMBNAIL_HEIGHT.toFloat())
            close()
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            alpha = 150
        }
        canvas.drawPath(path, paint)
    }

    private fun hsv(hue: Float, saturation: Float, value: Float): Int =
        Color.HSVToColor(floatArrayOf(((hue % 360f) + 360f) % 360f, saturation.coerceIn(0f, 1f), value))

    /**
     * Drawing parameters read from the vector. Each one is a projection of the whole (standardised) vector onto a
     * fixed pseudo-random direction, squashed into 0..1 with tanh: no parameter depends on a single component, so
     * small differences spread over many components (as between similar sentences) move every parameter only a
     * little, while unrelated vectors give independent values. Standardising removes the embedding's scale.
     */
    private class Params(vector: FloatArray) {
        private val z: FloatArray = run {
            require(vector.isNotEmpty()) { "Empty vector" }
            val mean = vector.average().toFloat()
            val std = sqrt(vector.map { (it - mean) * (it - mean) }.average()).toFloat().takeIf { it > 0f } ?: 1f
            FloatArray(vector.size) { (vector[it] - mean) / std }
        }

        operator fun get(index: Int): Float = 0.5f + 0.5f * tanh(projection(index).toFloat())

        /**
         * The base hue as the angle of a 2-D projection: uniformly spread around the colour wheel, and much more
         * stable under small changes than a single projection stretched to 360 degrees.
         */
        val hue: Float
            get() = (Math.toDegrees(kotlin.math.atan2(projection(HUE_X), projection(HUE_Y))).toFloat() + 360f) % 360f

        private fun projection(index: Int): Double {
            val direction = SplitMix64(DIRECTION_SEED + index.toLong())
            var dot = 0.0
            var norm = 0.0
            for (value in z) {
                val weight = direction.nextSignedUnit()
                dot += value * weight
                norm += weight * weight
            }
            return dot / sqrt(norm)
        }

        private companion object {
            const val DIRECTION_SEED = 0x5EEDL
            const val HUE_X = 1_000
            const val HUE_Y = 1_001
        }
    }

    companion object {
        private const val ORB_COUNT = 5
        private const val ORB_PARAMS_START = 9

        /** A stand-in vector when no embedding model is available: stable per title, but without semantics. */
        fun vectorFromText(text: String): FloatArray {
            val seed = CRC32().apply { update(text.toByteArray()) }.value
            val random = SplitMix64(seed)
            return FloatArray(64) { random.nextSignedUnit().toFloat() }
        }
    }
}
