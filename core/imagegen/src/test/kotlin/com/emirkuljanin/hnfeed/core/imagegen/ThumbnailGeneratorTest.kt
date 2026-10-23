package com.emirkuljanin.hnfeed.core.imagegen

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ThumbnailGeneratorTest {

    private val request = ImageRequest(storyId = 1, title = "A story")

    @Test
    fun usesTheFirstGeneratorThatMayHandleTheRequest() = runTest {
        val cloud = FakeGenerator(ImageSource.CLOUD)
        val onDevice = FakeGenerator(ImageSource.ON_DEVICE)

        val image = ChainedThumbnailGenerator(listOf(cloud, onDevice)).generate(request)

        assertEquals(ImageSource.CLOUD, image.source)
        assertFalse(image.isFallback)
        assertEquals(0, onDevice.calls)
    }

    @Test
    fun skipsGeneratorsThatMayNotHandleTheRequest_withoutMarkingTheResultAsAFallback() = runTest {
        val cloud = FakeGenerator(ImageSource.CLOUD, allowed = false) // e.g. an unsafe title

        val image = ChainedThumbnailGenerator(listOf(cloud, FakeGenerator(ImageSource.ON_DEVICE))).generate(request)

        assertEquals(ImageSource.ON_DEVICE, image.source)
        assertFalse(image.isFallback, "On-device art is the intended result here, not a fallback")
        assertEquals(0, cloud.calls)
    }

    @Test
    fun fallsBackToTheNextGenerator_whenOneFails_andMarksTheResult() = runTest {
        val cloud = FakeGenerator(ImageSource.CLOUD, failure = IOException("HTTP 429"))

        val image = ChainedThumbnailGenerator(listOf(cloud, FakeGenerator(ImageSource.ON_DEVICE))).generate(request)

        assertEquals(ImageSource.ON_DEVICE, image.source)
        assertTrue(image.isFallback)
        assertEquals(1, cloud.calls)
    }

    @Test
    fun doesNotMarkTheResultAsAFallback_whenThePreferredGeneratorRefusedForGood() = runTest {
        val cloud = FakeGenerator(ImageSource.CLOUD, failure = PermanentGenerationException("HTTP 400: NSFW"))

        val image = ChainedThumbnailGenerator(listOf(cloud, FakeGenerator(ImageSource.ON_DEVICE))).generate(request)

        assertEquals(ImageSource.ON_DEVICE, image.source)
        assertFalse(image.isFallback, "Retrying a moderation or auth rejection would only fail again")
    }

    @Test
    fun failsWithTheLastError_whenEveryGeneratorFails() = runTest {
        val generators = listOf(
            FakeGenerator(ImageSource.CLOUD, failure = IOException("offline")),
            FakeGenerator(ImageSource.ON_DEVICE, failure = IOException("disk full")),
        )

        val error = assertFailsWith<IOException> { ChainedThumbnailGenerator(generators).generate(request) }

        assertEquals("disk full", error.message)
    }

    @Test
    fun failsWithAnIOException_whenNoGeneratorMayHandleTheRequest() = runTest {
        val generators = listOf(FakeGenerator(ImageSource.CLOUD, allowed = false))

        assertFailsWith<IOException> { ChainedThumbnailGenerator(generators).generate(request) }
    }

    @Test
    fun doesNotSwallowCancellation() = runTest {
        val cancelled = FakeGenerator(ImageSource.CLOUD, failure = CancellationException("cancelled"))
        val onDevice = FakeGenerator(ImageSource.ON_DEVICE)

        assertFailsWith<CancellationException> {
            ChainedThumbnailGenerator(listOf(cancelled, onDevice)).generate(request)
        }
        assertEquals(0, onDevice.calls)
    }

    private class FakeGenerator(
        private val source: ImageSource,
        private val allowed: Boolean = true,
        private val failure: Exception? = null,
    ) : ImageGenerator {
        var calls = 0
            private set

        override suspend fun canGenerate(request: ImageRequest) = allowed

        override suspend fun generate(request: ImageRequest): GeneratedImage {
            calls++
            failure?.let { throw it }
            return GeneratedImage(webpBytes = byteArrayOf(1), source = source)
        }
    }
}
