package com.emirkuljanin.hnfeed.core.data.thumbnail

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.emirkuljanin.hnfeed.core.data.testing.FakeThumbnailGenerator
import com.emirkuljanin.hnfeed.core.data.testing.FakeThumbnailScheduler
import com.emirkuljanin.hnfeed.core.data.testing.FakeUpgradePause
import com.emirkuljanin.hnfeed.core.data.testing.InMemoryDatabaseTest
import com.emirkuljanin.hnfeed.core.data.thumbnail.WorkManagerThumbnailScheduler.Companion.UPGRADE_WORK
import com.emirkuljanin.hnfeed.core.database.model.StoryEntity
import com.emirkuljanin.hnfeed.core.database.model.UpgradeState
import com.emirkuljanin.hnfeed.core.imagegen.UpgradeOutcome
import com.emirkuljanin.hnfeed.core.model.StoryType
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder

/** The workers' mapping from pipeline results to WorkManager results and follow-up scheduling. */
internal class ThumbnailWorkersTest : InMemoryDatabaseTest() {

    @get:Rule
    val folder = TemporaryFolder()

    private val generator = FakeThumbnailGenerator()
    private val scheduler = FakeThumbnailScheduler()
    private val pause = FakeUpgradePause()
    private val pipeline by lazy {
        ThumbnailPipeline(
            thumbnailDao = database.thumbnailDao(),
            storyDao = storyDao,
            generator = generator,
            store = ThumbnailStore(File(folder.root, "thumbnails"), Dispatchers.IO),
            pause = pause,
            clock = clock,
        )
    }

    @Test
    fun previewWorker_writesPreviews_thenSchedulesUpgrades() = runTest {
        feed(1, 2)

        val result = worker<ThumbnailPreviewWorker>().doWork()

        assertEquals(Result.success(), result)
        assertNotNull(database.thumbnailDao().get(2))
        assertEquals(1, scheduler.upgradesScheduled)
    }

    @Test
    fun previewWorker_succeedsEvenIfAPreviewFailed_soLaterRunsAreNotHeldBack() = runTest {
        feed(1, 2)
        generator.previewFailures += 1

        assertEquals(Result.success(), worker<ThumbnailPreviewWorker>().doWork())
        assertEquals(1, scheduler.upgradesScheduled)
    }

    @Test
    fun upgradeWorker_succeeds_whenTheQueueIsWorkedOff() = runTest {
        feed(1)
        pipeline.generatePreviews()

        assertEquals(Result.success(), worker<ThumbnailUpgradeWorker>().doWork())
        assertEquals(listOf(1L), generator.upgradeRequests)
        assertEquals(UpgradeState.DONE, database.thumbnailDao().get(1)?.upgradeState)
        assertNull(scheduler.upgradesScheduledAt)
    }

    @Test
    fun upgradeWorker_atTheQuota_succeeds_andSchedulesTheResumeForTheStoredPause() = runTest {
        feed(1)
        pipeline.generatePreviews()
        generator.upgradeOutcomes[1] = UpgradeOutcome.QuotaExhausted("HTTP 429")

        assertEquals(Result.success(), worker<ThumbnailUpgradeWorker>().doWork())
        assertEquals(pause.resumesAt, scheduler.upgradesScheduledAt)

        // A run during the pause (e.g. the clock was set back) resumes at the stored time, not a day later.
        assertEquals(Result.success(), worker<ThumbnailUpgradeWorker>().doWork())
        assertEquals(Instant.parse("2026-10-24T00:05:00Z"), scheduler.upgradesScheduledAt)
    }

    @Test
    fun upgradeWorker_succeeds_andSchedulesARetry_whenAStoryFailsTransiently() = runTest {
        feed(1)
        pipeline.generatePreviews()
        generator.upgradeOutcomes[1] = UpgradeOutcome.RetryLater("timeout")

        assertEquals(Result.success(), worker<ThumbnailUpgradeWorker>().doWork())
        assertEquals(1, scheduler.upgradeRetries)
        assertEquals(Duration.ofMinutes(15), scheduler.upgradeRetryDelay)
        assertEquals(1, database.thumbnailDao().get(1)?.upgradeAttempts)
    }

    @Test
    fun upgradeWorker_retriesLater_theLongerTheProviderHasBeenFailing() = runTest {
        feed(1)
        pipeline.generatePreviews()
        generator.upgradeOutcomes[1] = UpgradeOutcome.Unavailable("Unable to resolve host")
        pause.failingSince = now.minus(Duration.ofHours(1))

        assertEquals(Result.success(), worker<ThumbnailUpgradeWorker>().doWork())
        assertEquals(Duration.ofHours(1), scheduler.upgradeRetryDelay)
    }

    @Test
    fun upgradeWorker_succeeds_andSchedulesARetry_whenTheProviderIsUnavailable() = runTest {
        feed(1)
        pipeline.generatePreviews()
        generator.upgradeOutcomes[1] = UpgradeOutcome.Unavailable("Unable to resolve host")

        assertEquals(Result.success(), worker<ThumbnailUpgradeWorker>().doWork())
        assertEquals(1, scheduler.upgradeRetries)
        assertEquals(UpgradeState.PENDING, database.thumbnailDao().get(1)?.upgradeState)
        assertNull(scheduler.upgradesScheduledAt)
    }

    /**
     * End to end with WorkManager's test driver: a run that found the provider unreachable must not leave a run in
     * backoff at the head of the upgrade queue, or every later request (page loads, app starts) would wait for that
     * backoff, up to WorkManager's 5 h cap, after the provider is back.
     */
    @Test
    fun upgradeRequest_afterTheProviderWasUnavailable_runsWithoutWaitingForABackoff() = runTest {
        assertTheNextRequestRunsAtOnce(after = UpgradeOutcome.Unavailable("Unable to resolve host"))
    }

    /**
     * The same after a story's transient failure: WorkManager counts backoff per request, not per story, so on a
     * provider that keeps timing out it would grow across stories and hold the queue back for hours.
     */
    @Test
    fun upgradeRequest_afterAStoryFailedTransiently_runsWithoutWaitingForABackoff() = runTest {
        assertTheNextRequestRunsAtOnce(after = UpgradeOutcome.RetryLater("timeout"))
    }

    private suspend fun assertTheNextRequestRunsAtOnce(after: UpgradeOutcome) {
        feed(1)
        pipeline.generatePreviews()
        generator.upgradeOutcomes[1] = after
        val workManager = testWorkManager()
        val scheduler = WorkManagerThumbnailScheduler(workManager, clock)
        scheduler.scheduleUpgrades()
        val failedRun = workManager.upgradeRuns().single()
        driver().setAllConstraintsMet(failedRun.id)
        workManager.awaitAttempt(failedRun.id)

        generator.upgradeOutcomes.remove(1)
        scheduler.scheduleUpgrades()
        val nextRun = workManager.upgradeRuns().single { it.id != failedRun.id }

        assertEquals(WorkInfo.State.ENQUEUED, nextRun.state, "The request is not queued behind anything")
        driver().setAllConstraintsMet(nextRun.id)
        assertEquals(WorkInfo.State.SUCCEEDED, workManager.awaitAttempt(nextRun.id).state)
        assertEquals(UpgradeState.DONE, database.thumbnailDao().get(1)?.upgradeState)
    }

    @Test
    fun upgradeWorker_succeedsWithoutBackoff_whenTheProviderIsMisconfigured() = runTest {
        feed(1)
        pipeline.generatePreviews()
        generator.upgradeOutcomes[1] = UpgradeOutcome.Misconfigured("HTTP 401")

        assertEquals(Result.success(), worker<ThumbnailUpgradeWorker>().doWork())
        assertEquals(listOf(1L), generator.upgradeRequests)
        assertEquals(UpgradeState.PENDING, database.thumbnailDao().get(1)?.upgradeState)
        assertNull(scheduler.upgradesScheduledAt)
        assertEquals(0, scheduler.upgradeRetries)
    }

    @Test
    fun resumeWorker_schedulesUpgrades() = runTest {
        assertEquals(Result.success(), worker<ThumbnailUpgradeResumeWorker>().doWork())
        assertEquals(1, scheduler.upgradesScheduled)
    }

    /** WorkManager's test implementation, running the real workers wired to the real scheduler it backs. */
    private fun testWorkManager(): WorkManager {
        lateinit var workManager: WorkManager
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker? {
                val scheduler = WorkManagerThumbnailScheduler(workManager, clock)
                return when (workerClassName) {
                    ThumbnailUpgradeWorker::class.java.name ->
                        ThumbnailUpgradeWorker(appContext, workerParameters, pipeline, scheduler)

                    ThumbnailUpgradeResumeWorker::class.java.name ->
                        ThumbnailUpgradeResumeWorker(appContext, workerParameters, scheduler)

                    else -> null
                }
            }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(factory)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
        workManager = WorkManager.getInstance(context)
        return workManager
    }

    private fun WorkManager.upgradeRuns(): List<WorkInfo> = getWorkInfosForUniqueWork(UPGRADE_WORK).get()

    /** Waits until the work has run once: it finished, or it is waiting to be retried. */
    private suspend fun WorkManager.awaitAttempt(id: UUID): WorkInfo = getWorkInfoByIdFlow(id).filterNotNull().first {
        it.state.isFinished || (it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount > 0)
    }

    private fun driver() = WorkManagerTestInitHelper.getTestDriver(ApplicationProvider.getApplicationContext())!!

    private suspend fun feed(vararg ids: Long) {
        storyDao.upsert(
            ids.map {
                StoryEntity(it, "Story $it", "author", 1, now, null, 0, StoryType.STORY, fetchedAt = now)
            },
        )
        topStoryDao.replaceSnapshot(ids.toList())
        topStoryDao.markLoaded(ids.toList())
    }

    private inline fun <reified W : ListenableWorker> worker(): W =
        TestListenableWorkerBuilder<W>(ApplicationProvider.getApplicationContext())
            .setWorkerFactory(factory)
            .build()

    private val factory = object : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker? = when (workerClassName) {
            ThumbnailPreviewWorker::class.java.name ->
                ThumbnailPreviewWorker(appContext, workerParameters, pipeline, scheduler)

            ThumbnailUpgradeWorker::class.java.name ->
                ThumbnailUpgradeWorker(appContext, workerParameters, pipeline, scheduler)

            ThumbnailUpgradeResumeWorker::class.java.name ->
                ThumbnailUpgradeResumeWorker(appContext, workerParameters, scheduler)

            else -> null
        }
    }
}
