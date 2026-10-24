package com.emirkuljanin.hnfeed.core.data.testing

import com.emirkuljanin.hnfeed.core.data.thumbnail.ThumbnailScheduler
import com.emirkuljanin.hnfeed.core.data.thumbnail.UpgradePause
import com.emirkuljanin.hnfeed.core.imagegen.GeneratedImage
import com.emirkuljanin.hnfeed.core.imagegen.ImageRequest
import com.emirkuljanin.hnfeed.core.imagegen.ImageSource
import com.emirkuljanin.hnfeed.core.imagegen.ThumbnailGenerator
import com.emirkuljanin.hnfeed.core.imagegen.UpgradeOutcome
import java.io.IOException
import java.time.Duration
import java.time.Instant

/** Records schedule calls instead of talking to WorkManager. */
internal class FakeThumbnailScheduler : ThumbnailScheduler {
    var scheduled = 0
        private set
    var upgradesScheduled = 0
        private set
    var upgradesScheduledAt: Instant? = null
        private set
    var upgradeRetries = 0
        private set
    var upgradeRetryDelay: Duration? = null
        private set

    override fun schedule() {
        scheduled++
    }

    override fun scheduleUpgrades() {
        upgradesScheduled++
    }

    override fun scheduleUpgradesAt(resumeAt: Instant) {
        upgradesScheduledAt = resumeAt
    }

    override fun scheduleUpgradeRetry(after: Duration) {
        upgradeRetries++
        upgradeRetryDelay = after
    }
}

internal class FakeUpgradePause(var resumesAt: Instant? = null, var failingSince: Instant? = null) : UpgradePause {
    override suspend fun resumesAt(): Instant? = resumesAt

    override suspend fun pauseUntil(resumeAt: Instant) {
        resumesAt = resumeAt
    }

    override suspend fun recordFailure(now: Instant): Instant =
        failingSince?.takeIf { it <= now } ?: now.also { failingSince = it }

    override suspend fun clearFailures() {
        failingSince = null
    }
}

/**
 * Previews are bytes naming the story ("preview-<id>"); upgrades follow [upgradeOutcomes] per story id (default:
 * upgraded with "cloud-<id>"). Records every request in order; [onGenerate] runs before each result is returned,
 * e.g. to change the database while generation is "in flight".
 */
internal class FakeThumbnailGenerator : ThumbnailGenerator {
    val previewFailures = mutableSetOf<Long>()
    val upgradeOutcomes = mutableMapOf<Long, UpgradeOutcome>()
    val previewRequests = mutableListOf<Long>()
    val upgradeRequests = mutableListOf<Long>()
    var onGenerate: suspend (storyId: Long) -> Unit = {}

    override suspend fun generatePreview(request: ImageRequest): GeneratedImage {
        previewRequests += request.storyId
        onGenerate(request.storyId)
        if (request.storyId in previewFailures) throw IOException("Could not encode ${request.storyId}")
        return GeneratedImage("preview-${request.storyId}".toByteArray(), ImageSource.ON_DEVICE)
    }

    override suspend fun generateUpgrade(request: ImageRequest): UpgradeOutcome {
        upgradeRequests += request.storyId
        onGenerate(request.storyId)
        return upgradeOutcomes[request.storyId]
            ?: UpgradeOutcome.Upgraded(GeneratedImage("cloud-${request.storyId}".toByteArray(), ImageSource.CLOUD))
    }
}
