package com.emirkuljanin.hnfeed.core.imagegen.encode

import android.graphics.Bitmap
import android.graphics.Color
import com.emirkuljanin.hnfeed.core.imagegen.THUMBNAIL_HEIGHT
import com.emirkuljanin.hnfeed.core.imagegen.THUMBNAIL_WIDTH
import com.emirkuljanin.hnfeed.core.imagegen.testing.decode
import com.emirkuljanin.hnfeed.core.imagegen.testing.isWebp
import com.emirkuljanin.hnfeed.core.imagegen.testing.splitColourJpeg
import java.io.IOException
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThumbnailEncoderTest {

    @Test
    fun squareImage_isCentreCroppedTo16by9_scaled_andEncodedAsWebp() = runTest {
        val webp = encoder().fromEncodedImage(splitColourJpeg(1024, 1024))

        assertTrue(isWebp(webp))
        val thumbnail = decode(webp)
        assertEquals(THUMBNAIL_WIDTH, thumbnail.width)
        assertEquals(THUMBNAIL_HEIGHT, thumbnail.height)
        // A centre crop of a square keeps the full width, so both halves survive.
        assertColourNear(Color.RED, thumbnail.getPixel(64, THUMBNAIL_HEIGHT / 2))
        assertColourNear(Color.BLUE, thumbnail.getPixel(THUMBNAIL_WIDTH - 64, THUMBNAIL_HEIGHT / 2))
    }

    @Test
    fun veryWideImage_isCroppedFromTheCentre() = runTest {
        // 4:1, red left half and blue right half: a centre crop to 16:9 shows the seam in the middle.
        val thumbnail = decode(encoder().fromEncodedImage(splitColourJpeg(2048, 512)))

        assertColourNear(Color.RED, thumbnail.getPixel(THUMBNAIL_WIDTH / 4, THUMBNAIL_HEIGHT / 2))
        assertColourNear(Color.BLUE, thumbnail.getPixel(THUMBNAIL_WIDTH * 3 / 4, THUMBNAIL_HEIGHT / 2))
    }

    @Test
    fun smallImage_isScaledUpToTheThumbnailSize() = runTest {
        assertEquals(THUMBNAIL_WIDTH, decode(encoder().fromEncodedImage(splitColourJpeg(256, 144))).width)
    }

    @Test
    fun bytesThatAreNotAnImage_failWithAnIOException() = runTest {
        assertFailsWith<IOException> { encoder().fromEncodedImage("<html>Rate limited</html>".toByteArray()) }
    }

    @Test
    fun fromBitmap_rejectsAWrongSize() = runTest {
        val wrong = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)

        assertFailsWith<IllegalArgumentException> { encoder().fromBitmap(wrong) }
    }

    private fun TestScope.encoder() = ThumbnailEncoder(StandardTestDispatcher(testScheduler))

    private fun assertColourNear(expected: Int, actual: Int) {
        val distance = listOf(Color::red, Color::green, Color::blue).maxOf { channel ->
            abs(channel(expected) - channel(actual))
        }
        assertTrue(distance < 40, "Expected ${Integer.toHexString(expected)} but was ${Integer.toHexString(actual)}")
    }
}
