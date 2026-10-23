package com.emirkuljanin.hnfeed.core.imagegen.ondevice

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.emirkuljanin.hnfeed.core.imagegen.THUMBNAIL_HEIGHT
import com.emirkuljanin.hnfeed.core.imagegen.THUMBNAIL_WIDTH
import com.github.takahirom.roborazzi.captureRoboImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProceduralArtistTest {

    private val artist = ProceduralArtist()

    @Test
    fun drawsAThumbnailSizedBitmap() {
        val bitmap = artist.draw(vector(seed = 1))

        assertEquals(THUMBNAIL_WIDTH, bitmap.width)
        assertEquals(THUMBNAIL_HEIGHT, bitmap.height)
    }

    @Test
    fun isDeterministic() {
        assertContentEquals(pixels(artist.draw(vector(seed = 7))), pixels(artist.draw(vector(seed = 7))))
    }

    @Test
    fun nearbyVectorsGiveSimilarPictures_andUnrelatedVectorsDifferentOnes() {
        val base = vector(seed = 3)
        val nearby = FloatArray(base.size) { base[it] + 0.02f * (if (it % 2 == 0) 1 else -1) }

        val toNearby = meanDifference(artist.draw(base), artist.draw(nearby))
        val toUnrelated = meanDifference(artist.draw(base), artist.draw(vector(seed = 99)))

        assertTrue(toNearby * 4 < toUnrelated, "nearby=$toNearby unrelated=$toUnrelated")
    }

    @Test
    fun vectorFromText_isStablePerTitle() {
        assertContentEquals(ProceduralArtist.vectorFromText("Same"), ProceduralArtist.vectorFromText("Same"))
    }

    /**
     * A contact sheet of on-device thumbnails for review (`src/test/screenshots/procedural_art.png`): each row is a
     * base vector, a slightly perturbed one (a "similar title") and an unrelated one.
     */
    @Test
    fun contactSheet() {
        val gap = 8
        val sheet = Bitmap.createBitmap(
            3 * THUMBNAIL_WIDTH + 4 * gap,
            3 * THUMBNAIL_HEIGHT + 4 * gap,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(sheet).apply { drawColor(Color.WHITE) }
        listOf(11, 23, 42).forEachIndexed { row, seed ->
            val base = vector(seed)
            val similar = FloatArray(base.size) { base[it] + 0.05f * (if (it % 3 == 0) 1 else -1) }
            listOf(base, similar, vector(seed + 1000)).forEachIndexed { column, v ->
                val x = gap + column * (THUMBNAIL_WIDTH + gap)
                val y = gap + row * (THUMBNAIL_HEIGHT + gap)
                canvas.drawBitmap(artist.draw(v), x.toFloat(), y.toFloat(), null)
            }
        }
        sheet.captureRoboImage("src/test/screenshots/procedural_art.png")
    }

    /** A vector shaped like a sentence embedding (100 values around zero). */
    private fun vector(seed: Int) = SplitMix64(seed.toLong()).let { random ->
        FloatArray(100) { (random.nextSignedUnit() * 0.2).toFloat() }
    }

    private fun pixels(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    private fun meanDifference(a: Bitmap, b: Bitmap): Double {
        val pa = pixels(a)
        val pb = pixels(b)
        return pa.indices.sumOf { i ->
            (
                abs(Color.red(pa[i]) - Color.red(pb[i])) + abs(Color.green(pa[i]) - Color.green(pb[i])) +
                    abs(Color.blue(pa[i]) - Color.blue(pb[i]))
                ).toLong()
        }.toDouble() / pa.size
    }
}
