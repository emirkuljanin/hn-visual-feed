package com.emirkuljanin.hnfeed.core.data.thumbnail

import android.database.SQLException
import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import com.emirkuljanin.hnfeed.core.database.dao.StoryDao
import com.emirkuljanin.hnfeed.core.database.dao.ThumbnailDao
import com.emirkuljanin.hnfeed.core.database.model.ThumbnailEntity
import com.emirkuljanin.hnfeed.core.database.model.ThumbnailWork
import com.emirkuljanin.hnfeed.core.database.model.UpgradeState
import com.emirkuljanin.hnfeed.core.imagegen.ImageRequest
import com.emirkuljanin.hnfeed.core.imagegen.ImageSource
import com.emirkuljanin.hnfeed.core.imagegen.ThumbnailGenerator
import com.emirkuljanin.hnfeed.core.imagegen.UpgradeOutcome
import com.emirkuljanin.hnfeed.core.model.ThumbnailSource
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * The two phases of thumbnail generation, plus cache cleanup. Plain code so it can be tested without WorkManager;
 * the workers only call it.
 *
 * 1. **Previews**: every story in the visible feed without a thumbnail gets on-device art, best rank first. Fast
 *    (well under a second each), offline, never refused: no card waits on the network or the quota.
 * 2. **Upgrades**: previews are replaced by a better image (cloud) best rank first, so a limited free quota goes to
 *    what the user sees. The run stops at the first quota error (and pauses upgrades until the reset), at a
 *    misconfigured provider (bad credentials: no story can succeed, the next scheduled run tries again), at an
 *    unreachable or busy provider, or at a transient failure of a story (which would likely hit the next ones too).
 *    Only the last counts against the story.
 *
 * The database is written after the file, and the replaced file deleted after the database; a crash in between
 * leaves an orphan file that cleanup removes, never a row pointing at a missing file.
 */
internal class ThumbnailPipeline @Inject constructor(
    private val thumbnailDao: ThumbnailDao,
    private val storyDao: StoryDao,
    private val generator: ThumbnailGenerator,
    private val store: ThumbnailStore,
    private val pause: UpgradePause,
    private val clock: Clock,
) {

    /** Returns `true` if every story got a preview, `false` if some failed and should be retried later. */
    suspend fun generatePreviews(): Boolean {
        val failed = mutableSetOf<Long>()
        val permits = Semaphore(PREVIEW_PARALLELISM)
        while (true) {
            // Failed stories are still in the queue; ask for enough rows to get past them.
            val batch = thumbnailDao.storiesNeedingPreview(limit = BATCH_SIZE + failed.size)
                .filterNot { it.storyId in failed }
                .take(BATCH_SIZE)
            if (batch.isEmpty()) return failed.isEmpty()
            coroutineScope {
                batch.map { work -> async { permits.withPermit { work.takeUnless { preview(it) } } } }
                    .awaitAll()
                    .forEach { if (it != null) failed += it.storyId }
            }
        }
    }

    /** Returns whether the upgrade queue was worked off, or why it stopped. */
    suspend fun upgrade(): UpgradeRunResult {
        val resumesAt = pause.resumesAt()
        if (resumesAt != null && clock.instant() < resumesAt) return UpgradeRunResult.QuotaExhausted(resumesAt)
        while (true) {
            val batch = thumbnailDao.storiesNeedingUpgrade(limit = UPGRADE_PARALLELISM)
            if (batch.isEmpty()) return UpgradeRunResult.Completed
            val outcomes = coroutineScope {
                batch.map { work -> async { work to generator.generateUpgrade(work.request()) } }.awaitAll()
            }
            var quotaExhausted = false
            var misconfigured: UpgradeOutcome.Misconfigured? = null
            var retryLater = false
            for ((work, outcome) in outcomes) {
                when (outcome) {
                    is UpgradeOutcome.Upgraded -> replace(work, outcome)

                    UpgradeOutcome.NotApplicable -> setUpgradeState(work, UpgradeState.NOT_APPLICABLE)

                    is UpgradeOutcome.Rejected -> setUpgradeState(work, UpgradeState.REJECTED)

                    is UpgradeOutcome.QuotaExhausted -> quotaExhausted = true

                    is UpgradeOutcome.Misconfigured -> misconfigured = outcome

                    is UpgradeOutcome.Unavailable -> retryLater = true

                    is UpgradeOutcome.RetryLater -> {
                        countFailure(work)
                        retryLater = true
                    }
                }
            }
            if (quotaExhausted) {
                val resumeAt = nextQuotaReset(clock.instant())
                pause.pauseUntil(resumeAt)
                return UpgradeRunResult.QuotaExhausted(resumeAt)
            }
            misconfigured?.let {
                Log.w(TAG, "Upgrades stopped, the provider refuses every request: ${it.reason}")
                return UpgradeRunResult.Misconfigured
            }
            if (retryLater) return UpgradeRunResult.RetryLater
        }
    }

    /**
     * Evicts stories that are no longer in the snapshot and were fetched over [STALE_AFTER] ago (their thumbnail rows
     * go with them), and deletes image files no row points at.
     */
    suspend fun cleanUp() {
        val now = clock.instant()
        storyDao.deleteStale(cutoff = now.minus(STALE_AFTER))
        store.deleteAllExcept(keep = thumbnailDao.fileNames().toSet(), modifiedBefore = now.minus(ORPHAN_GRACE))
    }

    private suspend fun preview(work: ThumbnailWork): Boolean {
        val fileName = try {
            store.write(work.storyId, ThumbnailSource.ON_DEVICE, generator.generatePreview(work.request()).webpBytes)
        } catch (e: IOException) {
            Log.w(TAG, "Preview failed for story ${work.storyId}", e)
            return false
        }
        return try {
            thumbnailDao.upsert(
                ThumbnailEntity(
                    storyId = work.storyId,
                    fileName = fileName,
                    source = ThumbnailSource.ON_DEVICE,
                    upgradeState = UpgradeState.PENDING,
                    upgradeAttempts = 0,
                    updatedAt = clock.instant(),
                ),
            )
            true
        } catch (e: SQLiteConstraintException) {
            // The story was removed meanwhile (foreign key): its file is now an orphan for cleanup.
            Log.w(TAG, "Story ${work.storyId} disappeared before its preview was saved", e)
            true
        } catch (e: SQLException) {
            // E.g. a full disk. Reported as failed so this run does not pick the story again.
            Log.w(TAG, "Could not save the preview for story ${work.storyId}", e)
            false
        }
    }

    private suspend fun replace(work: ThumbnailWork, upgraded: UpgradeOutcome.Upgraded) {
        val source = upgraded.image.source.asThumbnailSource()
        val fileName = try {
            store.write(work.storyId, source, upgraded.image.webpBytes)
        } catch (e: IOException) {
            Log.w(TAG, "Could not store the upgrade for story ${work.storyId}", e)
            countFailure(work)
            return
        }
        try {
            thumbnailDao.upsert(
                ThumbnailEntity(
                    storyId = work.storyId,
                    fileName = fileName,
                    source = source,
                    upgradeState = UpgradeState.DONE,
                    upgradeAttempts = work.upgradeAttempts,
                    updatedAt = clock.instant(),
                ),
            )
        } catch (e: SQLiteConstraintException) {
            Log.w(TAG, "Story ${work.storyId} disappeared before its upgrade was saved", e)
            return
        } catch (e: SQLException) {
            // Counted, or the run would pick the story again and spend quota on it in a loop.
            Log.w(TAG, "Could not save the upgrade for story ${work.storyId}", e)
            countFailure(work)
            return
        }
        work.fileName?.takeIf { it != fileName }?.let { store.delete(it) }
    }

    private suspend fun countFailure(work: ThumbnailWork) {
        val attempts = work.upgradeAttempts + 1
        val state = if (attempts >= MAX_UPGRADE_ATTEMPTS) UpgradeState.GAVE_UP else UpgradeState.PENDING
        thumbnailDao.updateUpgrade(work.storyId, state, attempts, clock.instant())
    }

    private suspend fun setUpgradeState(work: ThumbnailWork, state: UpgradeState) {
        thumbnailDao.updateUpgrade(work.storyId, state, work.upgradeAttempts, clock.instant())
    }

    private fun ThumbnailWork.request() = ImageRequest(storyId = storyId, title = title)

    private fun ImageSource.asThumbnailSource() = when (this) {
        ImageSource.CLOUD -> ThumbnailSource.CLOUD
        ImageSource.ON_DEVICE -> ThumbnailSource.ON_DEVICE
    }

    companion object {
        private const val TAG = "ThumbnailPipeline"
        private const val BATCH_SIZE = 12

        /** Drawing and encoding are CPU-bound; MediaPipe inference is serialised inside the embedder anyway. */
        private const val PREVIEW_PARALLELISM = 3

        /** Small on purpose: a quota error should stop the run before many more calls are wasted. */
        private const val UPGRADE_PARALLELISM = 2

        /** Transient failures per story before its upgrade is given up. */
        const val MAX_UPGRADE_ATTEMPTS = 3

        val STALE_AFTER: Duration = Duration.ofDays(7)
        private val ORPHAN_GRACE: Duration = Duration.ofMinutes(10)
    }
}

internal sealed interface UpgradeRunResult {
    /** No more stories to upgrade right now. */
    data object Completed : UpgradeRunResult

    /** Stopped at the provider's quota (or found upgrades paused): try again at [resumeAt]. */
    data class QuotaExhausted(val resumeAt: Instant) : UpgradeRunResult

    /** Stopped because the provider refuses every request (bad credentials): try again on the next scheduled run. */
    data object Misconfigured : UpgradeRunResult

    /** Stopped at a transient failure: try again with backoff. */
    data object RetryLater : UpgradeRunResult
}
