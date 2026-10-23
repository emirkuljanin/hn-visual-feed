package com.emirkuljanin.hnfeed.core.imagegen

/** What to draw: one thumbnail per story, generated from its title. */
data class ImageRequest(val storyId: Long, val title: String)

/** Where a thumbnail came from. Stored with it, so the UI or a later pass can tell the two apart. */
enum class ImageSource {
    CLOUD,
    ON_DEVICE,
}

/**
 * A finished thumbnail: WebP bytes of [THUMBNAIL_WIDTH] x [THUMBNAIL_HEIGHT] (16:9, the card's image slot).
 *
 * [isFallback] is true when a preferred generator was allowed to handle the title but failed transiently (offline,
 * timeout, quota, server error), so a later attempt could produce a better image. It is false when the result is
 * the intended one (on-device art for a title the safety filter keeps away from the cloud) or when the preferred
 * generator refused for good (moderation, bad credentials), where retrying would only fail again.
 */
class GeneratedImage(val webpBytes: ByteArray, val source: ImageSource, val isFallback: Boolean = false)

const val THUMBNAIL_WIDTH = 512
const val THUMBNAIL_HEIGHT = 288

/**
 * One way of turning a title into a thumbnail. [ChainedThumbnailGenerator] tries its generators in priority order
 * and uses the first that [canGenerate] and succeeds, so adding one (e.g. on-device diffusion) needs no other change.
 */
internal interface ImageGenerator {

    /** Cheap: configuration, safety rules, availability. No generation work. */
    suspend fun canGenerate(request: ImageRequest): Boolean

    /** Main-safe. Throws an [java.io.IOException] when this generator cannot produce an image right now. */
    suspend fun generate(request: ImageRequest): GeneratedImage
}

/**
 * A failure that will repeat on every retry: a prompt the provider's moderation rejects, a revoked or mis-scoped
 * token. Unlike other [java.io.IOException]s (offline, timeout, quota, server error) it does not make the
 * fallback result worth regenerating later.
 */
internal class PermanentGenerationException(message: String) : java.io.IOException(message)
