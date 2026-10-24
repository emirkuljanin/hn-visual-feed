package com.emirkuljanin.hnfeed.core.data.thumbnail

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.emirkuljanin.hnfeed.core.data.thumbnail.WorkManagerThumbnailScheduler.Companion.PREVIEW_WORK
import com.emirkuljanin.hnfeed.core.data.thumbnail.WorkManagerThumbnailScheduler.Companion.UPGRADE_RESUME_WORK
import com.emirkuljanin.hnfeed.core.data.thumbnail.WorkManagerThumbnailScheduler.Companion.UPGRADE_RETRY_WORK
import com.emirkuljanin.hnfeed.core.data.thumbnail.WorkManagerThumbnailScheduler.Companion.UPGRADE_WORK
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The scheduler against WorkManager's test implementation. Workers are replaced by no-ops that record which job ran,
 * so this checks the jobs, their constraints and delays, not what the workers do (see [ThumbnailWorkersTest]).
 */
@RunWith(RobolectricTestRunner::class)
class ThumbnailSchedulerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = Instant.parse("2026-10-23T12:00:00Z")
    private val ran = mutableListOf<String>()
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkManagerThumbnailScheduler

    @BeforeTest
    fun setUp() {
        val configuration = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(RecordingWorkerFactory())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
        workManager = WorkManager.getInstance(context)
        scheduler = WorkManagerThumbnailScheduler(workManager, Clock.fixed(now, ZoneOffset.UTC))
    }

    @Test
    fun schedule_runsPreviewsRightAway_andLeavesUpgradesToThem() {
        scheduler.schedule()

        assertEquals(listOf(ThumbnailPreviewWorker::class.java.name), ran)
        assertTrue(infos(UPGRADE_WORK).isEmpty())
    }

    @Test
    fun upgrades_waitForANetworkConnection() {
        scheduler.scheduleUpgrades()
        val upgrade = infos(UPGRADE_WORK).single()

        assertEquals(WorkInfo.State.ENQUEUED, upgrade.state)
        assertEquals(NetworkType.CONNECTED, upgrade.constraints.requiredNetworkType)
        driver().setAllConstraintsMet(upgrade.id)

        assertEquals(listOf(ThumbnailUpgradeWorker::class.java.name), ran)
    }

    @Test
    fun quotaResume_isOneDelayedJob_outsideTheUpgradeQueue() {
        val resumeAt = Instant.parse("2026-10-24T00:05:00Z")
        scheduler.scheduleUpgradesAt(resumeAt)
        scheduler.scheduleUpgradesAt(resumeAt)
        val resume = infos(UPGRADE_RESUME_WORK).single()

        assertEquals(WorkInfo.State.ENQUEUED, resume.state)
        assertEquals(Duration.ofHours(12).plusMinutes(5).toMillis(), resume.initialDelayMillis)

        // An upgrade request during the pause is not queued behind the resume.
        scheduler.scheduleUpgrades()
        driver().setAllConstraintsMet(infos(UPGRADE_WORK).single().id)
        assertEquals(listOf(ThumbnailUpgradeWorker::class.java.name), ran)

        driver().setInitialDelayMet(resume.id)
        assertEquals(ThumbnailUpgradeResumeWorker::class.java.name, ran.last())
    }

    @Test
    fun upgradeRetry_isOneDelayedJob_outsideTheUpgradeQueue_andTheLatestFailureDecides() {
        // A long retry left over from an earlier outage, then a new failure that asks for a short one.
        scheduler.scheduleUpgradeRetry(Duration.ofHours(4))
        scheduler.scheduleUpgradeRetry(Duration.ofMinutes(15))
        val retry = infos(UPGRADE_RETRY_WORK).single { !it.state.isFinished }

        assertEquals(WorkInfo.State.ENQUEUED, retry.state)
        assertEquals(Duration.ofMinutes(15).toMillis(), retry.initialDelayMillis, "REPLACE: the latest delay wins")
        assertTrue(infos(UPGRADE_RESUME_WORK).isEmpty(), "A quota resume is not held back by this retry")

        // An upgrade request meanwhile is not queued behind the retry.
        scheduler.scheduleUpgrades()
        driver().setAllConstraintsMet(infos(UPGRADE_WORK).single().id)
        assertEquals(listOf(ThumbnailUpgradeWorker::class.java.name), ran)

        driver().setInitialDelayMet(retry.id)
        assertEquals(ThumbnailUpgradeResumeWorker::class.java.name, ran.last())
    }

    @Test
    fun aRequestWhileARunIsPending_queuesOneMoreRunBehindIt() {
        // The first run waits for the network, so the second request arrives while it is unfinished.
        scheduler.scheduleUpgrades()
        scheduler.scheduleUpgrades()
        val (first, second) = infos(UPGRADE_WORK).sortedBy { it.state != WorkInfo.State.ENQUEUED }

        assertEquals(WorkInfo.State.ENQUEUED, first.state)
        assertEquals(WorkInfo.State.BLOCKED, second.state, "KEEP would have dropped the second request")
        driver().setAllConstraintsMet(first.id)
        driver().setAllConstraintsMet(second.id)

        assertEquals(List(2) { ThumbnailUpgradeWorker::class.java.name }, ran)
    }

    @Test
    fun previewsRequestedRepeatedly_allRun() {
        repeat(3) { scheduler.schedule() }

        assertEquals(3, ran.size)
        assertTrue(infos(PREVIEW_WORK).all { it.state == WorkInfo.State.SUCCEEDED })
    }

    private fun infos(uniqueName: String): List<WorkInfo> = workManager.getWorkInfosForUniqueWork(uniqueName).get()

    private fun driver() = WorkManagerTestInitHelper.getTestDriver(context)!!

    private inner class RecordingWorkerFactory : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker = object : Worker(appContext, workerParameters) {
            override fun doWork(): Result {
                ran += workerClassName
                return Result.success()
            }
        }
    }
}
