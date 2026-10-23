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

/** Phase 2: cloud upgrades for the feed, best rank first, until done, out of quota or failing transiently. */
@HiltWorker
class ThumbnailUpgradeWorker @AssistedInject internal constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pipeline: ThumbnailPipeline,
    private val scheduler: ThumbnailScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (val result = pipeline.upgrade()) {
        UpgradeRunResult.Completed -> Result.success()

        UpgradeRunResult.RetryLater -> Result.retry()

        is UpgradeRunResult.QuotaExhausted -> {
            scheduler.scheduleUpgradesAt(result.resumeAt)
            Result.success()
        }
    }
}

/** Runs once the provider's quota has reset and puts upgrades back in their own queue. */
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
