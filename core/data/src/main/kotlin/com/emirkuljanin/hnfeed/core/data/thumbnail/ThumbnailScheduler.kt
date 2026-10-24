package com.emirkuljanin.hnfeed.core.data.thumbnail

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/** Starts thumbnail generation for whatever the feed currently needs. */
internal interface ThumbnailScheduler {

    /**
     * Thumbnails for every feed story that needs one: previews, which then start upgrades. Cheap and idempotent:
     * call it whenever stories may have been added.
     */
    fun schedule()

    /** Upgrades only: after new previews were written, or when a quota pause ends. */
    fun scheduleUpgrades()

    /** Upgrades hit the provider's daily quota: start them again at [resumeAt], shortly after it resets. */
    fun scheduleUpgradesAt(resumeAt: Instant)

    /** Upgrades stopped at a transient failure (provider unreachable or busy, or a story's): start them again [after]. */
    fun scheduleUpgradeRetry(after: Duration)
}

/**
 * Unique WorkManager jobs, so each phase has the right constraints and survives process death: previews need nothing
 * (on-device); upgrades need a network connection. The preview worker schedules upgrades
 * when it is done, so an upgrade run never starts before the previews it would upgrade exist.
 *
 * Previews and upgrades use `APPEND_OR_REPLACE`: a request made while a run is in progress queues one more run behind
 * it, so stories added at the very end of a run are still picked up (KEEP would drop that request). A failed or
 * cancelled chain is replaced rather than blocking new work.
 *
 * The quota-reset resume is a separate job with KEEP, outside the upgrade chain: a delay in the chain would start only
 * when the run before it finished, and every request made during the pause would queue up behind it. The retry after
 * a transient failure is a second such job, for the same reason: a run in backoff would hold the chain back. It uses
 * REPLACE, so the latest failure decides its delay (a short outage after a long one must not wait for the old
 * job). It only enqueues an upgrade run, so replacing it, even while it runs, loses nothing the new job does not
 * redo; a leftover one after a run got through only starts one more pass.
 * Neither has a constraint of its own; the upgrade run it enqueues waits for the network.
 */
internal class WorkManagerThumbnailScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val clock: Clock,
) : ThumbnailScheduler {

    override fun schedule() {
        workManager.enqueueUniqueWork(
            PREVIEW_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<ThumbnailPreviewWorker>().build(),
        )
    }

    override fun scheduleUpgrades() {
        workManager.enqueueUniqueWork(
            UPGRADE_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<ThumbnailUpgradeWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    override fun scheduleUpgradesAt(resumeAt: Instant) {
        workManager.enqueueUniqueWork(
            UPGRADE_RESUME_WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ThumbnailUpgradeResumeWorker>()
                .setInitialDelay(Duration.between(clock.instant(), resumeAt).coerceAtLeast(Duration.ZERO))
                .build(),
        )
    }

    override fun scheduleUpgradeRetry(after: Duration) {
        workManager.enqueueUniqueWork(
            UPGRADE_RETRY_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ThumbnailUpgradeResumeWorker>().setInitialDelay(after).build(),
        )
    }

    companion object {
        const val PREVIEW_WORK = "thumbnail-previews"
        const val UPGRADE_WORK = "thumbnail-upgrades"
        const val UPGRADE_RESUME_WORK = "thumbnail-upgrades-resume"
        const val UPGRADE_RETRY_WORK = "thumbnail-upgrades-retry"
    }
}
