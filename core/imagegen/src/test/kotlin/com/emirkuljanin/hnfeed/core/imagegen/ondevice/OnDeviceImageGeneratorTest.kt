package com.emirkuljanin.hnfeed.core.imagegen.ondevice

import com.emirkuljanin.hnfeed.core.imagegen.ImageRequest
import com.emirkuljanin.hnfeed.core.imagegen.ImageSource
import com.emirkuljanin.hnfeed.core.imagegen.encode.ThumbnailEncoder
import com.emirkuljanin.hnfeed.core.imagegen.testing.isWebp
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnDeviceImageGeneratorTest {

    private val request = ImageRequest(storyId = 1, title = "Rust in the Linux kernel")

    @Test
    fun paintsFromTheEmbedding_andReturnsAWebpThumbnail() = runTest {
        val embedder = FakeEmbedder(FloatArray(100) { it / 100f })

        val image = generator(embedder).generate(request)

        assertEquals(ImageSource.ON_DEVICE, image.source)
        assertTrue(isWebp(image.webpBytes))
        assertEquals(listOf("Rust in the Linux kernel"), embedder.texts)
    }

    @Test
    fun withoutAnEmbeddingModel_stillProducesAStableImageFromTheTitle() = runTest {
        val first = generator(FakeEmbedder(vector = null)).generate(request)
        val second = generator(FakeEmbedder(vector = null)).generate(request)

        assertContentEquals(first.webpBytes, second.webpBytes)
    }

    @Test
    fun differentEmbeddings_giveDifferentImages() = runTest {
        val a = generator(FakeEmbedder(FloatArray(100) { it / 100f })).generate(request)
        val b = generator(FakeEmbedder(FloatArray(100) { -it / 100f })).generate(request)

        assertFalse(a.webpBytes.contentEquals(b.webpBytes))
    }

    @Test
    fun alwaysAcceptsARequest() = runTest {
        assertTrue(generator(FakeEmbedder(null)).canGenerate(request.copy(title = "anything at all")))
    }

    private fun kotlinx.coroutines.test.TestScope.generator(embedder: TextEmbedder) = OnDeviceImageGenerator(
        embedder = embedder,
        artist = ProceduralArtist(),
        encoder = ThumbnailEncoder(StandardTestDispatcher(testScheduler)),
        defaultDispatcher = StandardTestDispatcher(testScheduler),
    )

    private class FakeEmbedder(private val vector: FloatArray?) : TextEmbedder {
        val texts = mutableListOf<String>()

        override suspend fun embed(text: String): FloatArray? {
            texts += text
            return vector
        }
    }
}
