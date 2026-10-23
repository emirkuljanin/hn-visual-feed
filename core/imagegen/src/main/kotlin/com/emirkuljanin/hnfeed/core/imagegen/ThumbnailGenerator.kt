package com.emirkuljanin.hnfeed.core.imagegen

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException

/**
 * Generates story thumbnails in two phases, the module's entry point (an interface, so callers test with a fake):
 * a [generatePreview] that is instant, offline and always works, and a [generateUpgrade] that tries to produce a
 * better image within the provider's limits and reports what to do next.
 */
interface ThumbnailGenerator {

    /** On-device art. Main-safe. Throws [IOException] only if the image cannot be encoded. */
    suspend fun generatePreview(request: ImageRequest): GeneratedImage

    /** A better image than the preview (cloud today). Main-safe; never throws [IOException], see [UpgradeOutcome]. */
    suspend fun generateUpgrade(request: ImageRequest): UpgradeOutcome
}

/** What an upgrade attempt produced, and with it whether trying again makes sense. */
sealed interface UpgradeOutcome {

    class Upgraded(val image: GeneratedImage) : UpgradeOutcome

    /** No upgrade applies to this title: no credentials, or the safety filter keeps it off the cloud. Final. */
    data object NotApplicable : UpgradeOutcome

    /** Refused for good (moderation, bad credentials): the same request would fail again. Final. */
    data class Rejected(val reason: String) : UpgradeOutcome

    /** The provider's daily quota is used up: no request can succeed before it resets. */
    data class QuotaExhausted(val reason: String) : UpgradeOutcome

    /**
     * The provider could not be reached (no connection, DNS) or is busy for everyone. Says nothing about this
     * story: try again later without holding it against the story.
     */
    data class Unavailable(val reason: String) : UpgradeOutcome

    /** This request failed in a way that might not repeat (timeout, server error, unreadable image). */
    data class RetryLater(val reason: String) : UpgradeOutcome
}

/**
 * The preview comes from [preview] (on-device); upgrades try [upgrades] in priority order and the first allowed one
 * that succeeds wins (a future on-device diffusion generator slots in here). When several could not deliver, the
 * outcome that still leaves room for success wins: quota or retry over rejection over not applicable.
 * Cancellation is never swallowed: only [IOException]s are mapped to outcomes.
 */
internal class TieredThumbnailGenerator(
    private val preview: ImageGenerator,
    private val upgrades: List<ImageGenerator>,
) : ThumbnailGenerator {

    override suspend fun generatePreview(request: ImageRequest): GeneratedImage = preview.generate(request)

    override suspend fun generateUpgrade(request: ImageRequest): UpgradeOutcome {
        var outcome: UpgradeOutcome = UpgradeOutcome.NotApplicable
        for (generator in upgrades) {
            if (!generator.canGenerate(request)) continue
            val failure = try {
                return UpgradeOutcome.Upgraded(generator.generate(request))
            } catch (e: QuotaExceededException) {
                UpgradeOutcome.QuotaExhausted(e.message.orEmpty())
            } catch (e: PermanentGenerationException) {
                UpgradeOutcome.Rejected(e.message.orEmpty())
            } catch (e: IOException) {
                if (e.isUnavailable()) {
                    UpgradeOutcome.Unavailable(e.message.orEmpty())
                } else {
                    UpgradeOutcome.RetryLater(e.message.orEmpty())
                }
            }
            if (failure.hope > outcome.hope) outcome = failure
        }
        return outcome
    }

    private val UpgradeOutcome.hope: Int
        get() = when (this) {
            is UpgradeOutcome.Upgraded -> 4
            is UpgradeOutcome.RetryLater, is UpgradeOutcome.QuotaExhausted, is UpgradeOutcome.Unavailable -> 3
            is UpgradeOutcome.Rejected -> 2
            UpgradeOutcome.NotApplicable -> 1
        }

    /** Connection-level failures happen before the provider sees the request. Timeouts may be the request's own. */
    private fun IOException.isUnavailable() = this is ProviderUnavailableException ||
        this is UnknownHostException ||
        this is ConnectException ||
        this is NoRouteToHostException
}
