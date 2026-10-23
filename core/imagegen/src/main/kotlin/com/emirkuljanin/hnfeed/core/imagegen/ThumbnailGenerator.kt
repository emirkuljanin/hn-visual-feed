package com.emirkuljanin.hnfeed.core.imagegen

import java.io.IOException
import javax.inject.Inject

/** Generates a story's thumbnail. The module's entry point; an interface so callers can test with a fake. */
interface ThumbnailGenerator {

    /** Main-safe. Throws [IOException] only if no way of generating an image worked. */
    suspend fun generate(request: ImageRequest): GeneratedImage
}

/**
 * Tries the generators in priority order (cloud first, on-device last): the first that is allowed to handle the
 * title and succeeds wins. A failed cloud call therefore still yields an on-device image, marked
 * [GeneratedImage.isFallback], and an unsafe title never reaches the cloud at all. Cancellation is never swallowed:
 * only [IOException]s fall through to the next generator.
 */
internal class ChainedThumbnailGenerator @Inject constructor(
    private val generators: List<@JvmSuppressWildcards ImageGenerator>,
) : ThumbnailGenerator {

    override suspend fun generate(request: ImageRequest): GeneratedImage {
        var lastFailure: IOException? = null
        for (generator in generators) {
            if (!generator.canGenerate(request)) continue
            try {
                val image = generator.generate(request)
                // Worth regenerating later only if a preferred generator failed for a reason that may go away.
                val retryable = lastFailure != null && lastFailure !is PermanentGenerationException
                return if (retryable) GeneratedImage(image.webpBytes, image.source, isFallback = true) else image
            } catch (e: IOException) {
                lastFailure = e
            }
        }
        throw lastFailure ?: IOException("No image generator can handle story ${request.storyId}")
    }
}
