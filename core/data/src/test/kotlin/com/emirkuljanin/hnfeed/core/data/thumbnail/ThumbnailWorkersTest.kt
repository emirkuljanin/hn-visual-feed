package com.emirkuljanin.hnfeed.core.data.thumbnail

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.emirkuljanin.hnfeed.core.data.testing.FakeThumbnailGenerator
import com.emirkuljanin.hnfeed.core.data.testing.FakeThumbnailScheduler
import com.emirkuljanin.hnfeed.core.data.testing.FakeUpgradePause
import com.emirkuljanin.hnfeed.core.data.testing.InMemoryDatabaseTest
import com.emirkuljanin.hnfeed.core.database.model.StoryEntity
import com.emirkuljanin.hnfeed.core.imagegen.UpgradeOutcome
import com.emirkuljanin.hnfeed.core.model.StoryType
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
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

        assertEquals(Result.success(), worker<ThumbnailUpgradeWorker>().doWork())
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
    fun upgradeWorker_retriesWithBackoff_onATransientFailure() = runTest {
        feed(1)
        pipeline.generatePreviews()
        generator.upgradeOutcomes[1] = UpgradeOutcome.RetryLater("HTTP 503")

        assertEquals(Result.retry(), worker<ThumbnailUpgradeWorker>().doWork())
    }

    @Test
    fun resumeWorker_schedulesUpgrades() = runTest {
        assertEquals(Result.success(), worker<ThumbnailUpgradeResumeWorker>().doWork())
        assertEquals(1, scheduler.upgradesScheduled)
    }

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
