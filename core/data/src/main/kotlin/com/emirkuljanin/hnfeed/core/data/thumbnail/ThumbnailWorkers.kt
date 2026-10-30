package com.emirkuljanin.hnfeed.core.data.thumbnail

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Phase 1: on-device previews for the feed, then cache cleanup, then upgrades for the new previews. Always succeeds:
 * stories whose preview failed are tried again by the next [ThumbnailScheduler.schedule] (every page load and app
 * start), whereas a retry with backoff would hold every later preview run back behind this one.
 */
@HiltWorker
class ThumbnailPreviewWorker @AssistedInject internal constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pipeline: ThumbnailPipeline,
    private val scheduler: ThumbnailScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        pipeline.generatePreviews()
        // Before cleanup, so a cleanup failure cannot keep the new previews from being upgraded.
        scheduler.scheduleUpgrades()
        pipeline.cleanUp()
        return Result.success()
    }
}

/**
 * Phase 2: cloud upgrades for the feed, best rank first, until done, out of quota, refused for bad credentials, the
 * provider unreachable or a story failing transiently.
 */
@HiltWorker
class ThumbnailUpgradeWorker @AssistedInject internal constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pipeline: ThumbnailPipeline,
    private val scheduler: ThumbnailScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (val result = pipeline.upgrade()) {
        UpgradeRunResult.Completed -> Result.success()

        // Not Result.retry(): a run in backoff would hold every later upgrade request back behind it, and WorkManager
        // counts the backoff per request, not per story, so it grows across stories up to its 5 h cap. A separate
        // delayed job tries again instead.
        is UpgradeRunResult.RetryLater -> {
            scheduler.scheduleUpgradeRetry(result.retryIn)
            Result.success()
        }

        // Backoff would only repeat the refusal; the next schedule (page load, app start) tries again.
        UpgradeRunResult.Misconfigured -> Result.success()

        is UpgradeRunResult.QuotaExhausted -> {
            scheduler.scheduleUpgradesAt(result.resumeAt)
            Result.success()
        }
    }
}

/** Runs once the quota has reset, or a while after a failure stopped a run, and puts upgrades back in their queue. */
@HiltWorker
class ThumbnailUpgradeResumeWorker @AssistedInject internal constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scheduler: ThumbnailScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        scheduler.scheduleUpgrades()
        return Result.success()
    }
}
