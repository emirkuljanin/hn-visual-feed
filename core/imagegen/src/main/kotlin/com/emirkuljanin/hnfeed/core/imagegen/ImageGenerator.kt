package com.emirkuljanin.hnfeed.core.imagegen

import java.io.IOException

/** What to draw: one thumbnail per story, generated from its title. */
data class ImageRequest(val storyId: Long, val title: String)

/** Where a thumbnail came from. Stored with it, so the UI or a later pass can tell the two apart. */
enum class ImageSource {
    CLOUD,
    ON_DEVICE,
}

/** A finished thumbnail: WebP bytes of [THUMBNAIL_WIDTH] x [THUMBNAIL_HEIGHT] (16:9, the card's image slot). */
class GeneratedImage(val webpBytes: ByteArray, val source: ImageSource)

const val THUMBNAIL_WIDTH = 512
const val THUMBNAIL_HEIGHT = 288

/**
 * One way of turning a title into a thumbnail. [TieredThumbnailGenerator] uses one for previews and tries the others
 * in priority order for upgrades, so adding one (e.g. on-device diffusion) needs no other change.
 */
internal interface ImageGenerator {

    /** Cheap: configuration, safety rules, availability. No generation work. */
    suspend fun canGenerate(request: ImageRequest): Boolean

    /** Main-safe. Throws an [IOException] when this generator cannot produce an image right now. */
    suspend fun generate(request: ImageRequest): GeneratedImage
}

/**
 * A failure of this request that will repeat on every retry, e.g. a prompt the provider's moderation rejects. Maps to
 * [UpgradeOutcome.Rejected].
 */
internal class PermanentGenerationException(message: String) : IOException(message)

/**
 * The provider refuses every request until its configuration changes: a bad, revoked or mis-scoped token, a wrong
 * account id. Says nothing about this request. Maps to [UpgradeOutcome.Misconfigured].
 */
internal class ProviderMisconfiguredException(message: String) : IOException(message)

/** The provider's daily quota is used up. Maps to [UpgradeOutcome.QuotaExhausted]. */
internal class QuotaExceededException(message: String) : IOException(message)

/**
 * The provider could not be reached (no connection, DNS, TLS, a connection dropped before any answer) or is busy or
 * down for everyone (e.g. "capacity temporarily exceeded"), which says nothing about this request. Maps to
 * [UpgradeOutcome.Unavailable].
 */
internal class ProviderUnavailableException(message: String, cause: Throwable? = null) : IOException(message, cause)
