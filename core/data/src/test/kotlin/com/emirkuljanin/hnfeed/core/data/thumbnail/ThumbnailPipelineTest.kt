package com.emirkuljanin.hnfeed.core.data.thumbnail

import com.emirkuljanin.hnfeed.core.data.testing.FakeThumbnailGenerator
import com.emirkuljanin.hnfeed.core.data.testing.FakeUpgradePause
import com.emirkuljanin.hnfeed.core.data.testing.InMemoryDatabaseTest
import com.emirkuljanin.hnfeed.core.database.model.StoryEntity
import com.emirkuljanin.hnfeed.core.database.model.ThumbnailEntity
import com.emirkuljanin.hnfeed.core.database.model.UpgradeState
import com.emirkuljanin.hnfeed.core.imagegen.UpgradeOutcome
import com.emirkuljanin.hnfeed.core.model.StoryType
import com.emirkuljanin.hnfeed.core.model.ThumbnailSource
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder

internal class ThumbnailPipelineTest : InMemoryDatabaseTest() {

    @get:Rule
    val folder = TemporaryFolder()

    private val generator = FakeThumbnailGenerator()
    private val pause = FakeUpgradePause()
    private val thumbnailDao get() = database.thumbnailDao()
    private val store by lazy { ThumbnailStore(File(folder.root, "thumbnails"), Dispatchers.IO) }
    private val pipeline by lazy { pipeline() }

    private fun pipeline(clock: Clock = this.clock) =
        ThumbnailPipeline(thumbnailDao, storyDao, generator, store, pause, clock)

    @Test
    fun generatePreviews_givesEveryFeedStoryOnDeviceArt_bestRankFirst() = runTest {
        feed(loaded = listOf(3, 1, 2), unloaded = listOf(4))

        val allDone = pipeline.generatePreviews()

        assertTrue(allDone)
        assertEquals(setOf(3L, 1L, 2L), generator.previewRequests.toSet())
        listOf(1L, 2L, 3L).forEach { id ->
            val row = thumbnailDao.get(id)!!
            assertEquals(ThumbnailSource.ON_DEVICE, row.source)
            assertEquals(UpgradeState.PENDING, row.upgradeState)
            assertEquals("preview-$id", File(store.path(row.fileName)).readText())
        }
        assertEquals(null, thumbnailDao.get(4), "Stories outside the visible feed wait")
    }

    @Test
    fun generatePreviews_isIdempotent() = runTest {
        feed(loaded = listOf(1, 2))
        pipeline.generatePreviews()

        pipeline.generatePreviews()

        assertEquals(listOf(1L, 2L), generator.previewRequests.sorted())
    }

    @Test
    fun generatePreviews_skipsAFailingStory_withoutLooping_andReportsIt() = runTest {
        feed(loaded = listOf(1, 2, 3))
        generator.previewFailures += 2

        val allDone = pipeline.generatePreviews()

        assertFalse(allDone)
        assertEquals(1, generator.previewRequests.count { it == 2L })
        assertTrue(thumbnailDao.get(1) != null && thumbnailDao.get(3) != null)
    }

    @Test
    fun generatePreviews_whenTheStoryIsDeletedMeanwhile_movesOn_andLeavesOnlyAnOrphanFile() = runTest {
        feed(loaded = listOf(1, 2))
        generator.onGenerate = { id -> if (id == 2L) storyDao.delete(listOf(2)) }

        val allDone = pipeline.generatePreviews()

        assertTrue(allDone)
        assertEquals(null, thumbnailDao.get(2))
        assertEquals(1, generator.previewRequests.count { it == 2L })
        assertTrue(File(store.path("2-on_device.webp")).exists(), "Swept later by cleanUp")
    }

    @Test
    fun upgrade_replacesThePreviewWithTheCloudImage_andDeletesTheOldFile() = runTest {
        feed(loaded = listOf(1))
        pipeline.generatePreviews()
        val previewFile = File(store.path(thumbnailDao.get(1)!!.fileName))

        val result = pipeline.upgrade()

        assertEquals(UpgradeRunResult.Completed, result)
        val row = thumbnailDao.get(1)!!
        assertEquals(ThumbnailSource.CLOUD, row.source)
        assertEquals(UpgradeState.DONE, row.upgradeState)
        assertEquals("cloud-1", File(store.path(row.fileName)).readText())
        assertFalse(previewFile.exists())
    }

    @Test
    fun upgrade_recordsFinalOutcomes_andWorksOffTheQueue_bestRankFirst() = runTest {
        feed(loaded = listOf(3, 2, 1))
        pipeline.generatePreviews()
        generator.upgradeOutcomes[2] = UpgradeOutcome.NotApplicable
        generator.upgradeOutcomes[1] = UpgradeOutcome.Rejected("HTTP 400: NSFW")

        val result = pipeline.upgrade()

        assertEquals(UpgradeRunResult.Completed, result)
        assertEquals(listOf(3L, 2L, 1L), generator.upgradeRequests)
        assertEquals(UpgradeState.DONE, thumbnailDao.get(3)!!.upgradeState)
        assertEquals(UpgradeState.NOT_APPLICABLE, thumbnailDao.get(2)!!.upgradeState)
        assertEquals(UpgradeState.REJECTED, thumbnailDao.get(1)!!.upgradeState)
        assertEquals(ThumbnailSource.ON_DEVICE, thumbnailDao.get(1)!!.source, "A rejected story keeps its preview")
    }

    @Test
    fun upgrade_stopsAtTheQuota_withoutTouchingTheRemainingStories() = runTest {
        feed(loaded = listOf(1, 2, 3, 4))
        pipeline.generatePreviews()
        listOf(1L, 2L, 3L, 4L).forEach { generator.upgradeOutcomes[it] = UpgradeOutcome.QuotaExhausted("HTTP 429") }

        val result = pipeline.upgrade()

        assertEquals(UpgradeRunResult.QuotaExhausted(Instant.parse("2026-10-24T00:05:00Z")), result)
        assertEquals(2, generator.upgradeRequests.size, "Only the first parallel batch is attempted")
        listOf(1L, 2L, 3L, 4L).forEach {
            assertEquals(UpgradeState.PENDING, thumbnailDao.get(it)!!.upgradeState)
            assertEquals(0, thumbnailDao.get(it)!!.upgradeAttempts)
        }
        assertEquals(Instant.parse("2026-10-24T00:05:00Z"), pause.resumesAt, "Paused until after the reset")
    }

    @Test
    fun upgrade_stopsWhenTheProviderIsMisconfigured_withoutTouchingAnyStory() = runTest {
        feed(loaded = listOf(1, 2, 3, 4))
        pipeline.generatePreviews()
        listOf(1L, 2L, 3L, 4L).forEach { generator.upgradeOutcomes[it] = UpgradeOutcome.Misconfigured("HTTP 401") }

        assertEquals(UpgradeRunResult.Misconfigured, pipeline.upgrade())

        assertEquals(2, generator.upgradeRequests.size, "Only the first parallel batch is attempted")
        listOf(1L, 2L, 3L, 4L).forEach {
            assertEquals(UpgradeState.PENDING, thumbnailDao.get(it)!!.upgradeState)
            assertEquals(0, thumbnailDao.get(it)!!.upgradeAttempts)
        }
        assertNull(pause.resumesAt, "No timed pause: the next run tries again")

        // Once the token is fixed, the next run upgrades them.
        generator.upgradeOutcomes.clear()
        assertEquals(UpgradeRunResult.Completed, pipeline.upgrade())
        assertEquals(UpgradeState.DONE, thumbnailDao.get(1)!!.upgradeState)
    }

    @Test
    fun upgrade_duringAQuotaPause_doesNotAskTheProvider_andResumesAfterIt() = runTest {
        feed(loaded = listOf(1))
        pipeline.generatePreviews()
        pause.resumesAt = now.plusSeconds(3_600)

        assertEquals(UpgradeRunResult.QuotaExhausted(now.plusSeconds(3_600)), pipeline.upgrade())
        assertTrue(generator.upgradeRequests.isEmpty())

        assertEquals(UpgradeRunResult.Completed, pipeline(Clock.offset(clock, Duration.ofHours(1))).upgrade())
        assertEquals(listOf(1L), generator.upgradeRequests)
    }

    @Test
    fun upgrade_stopsWhenTheProviderIsUnavailable_withoutHoldingItAgainstTheStory() = runTest {
        feed(loaded = listOf(1, 2, 3))
        pipeline.generatePreviews()
        generator.upgradeOutcomes[1] = UpgradeOutcome.Unavailable("Unable to resolve host")
        generator.upgradeOutcomes[2] = UpgradeOutcome.Unavailable("Unable to resolve host")

        repeat(ThumbnailPipeline.MAX_UPGRADE_ATTEMPTS + 1) {
            assertEquals(UpgradeRunResult.RetryLater, pipeline.upgrade())
        }

        assertEquals(listOf(1L, 2L), generator.upgradeRequests.distinct(), "Story 3 waits for the next run")
        listOf(1L, 2L).forEach {
            assertEquals(UpgradeState.PENDING, thumbnailDao.get(it)!!.upgradeState)
            assertEquals(0, thumbnailDao.get(it)!!.upgradeAttempts)
        }
    }

    @Test
    fun upgrade_whenTheStoryIsDeletedMeanwhile_movesOn() = runTest {
        feed(loaded = listOf(1, 2))
        pipeline.generatePreviews()
        generator.onGenerate = { id -> if (id == 1L) storyDao.delete(listOf(1)) }

        assertEquals(UpgradeRunResult.Completed, pipeline.upgrade())

        assertEquals(listOf(1L, 2L), generator.upgradeRequests)
        assertEquals(UpgradeState.DONE, thumbnailDao.get(2)!!.upgradeState)
    }

    @Test
    fun upgrade_stopsAtATransientFailure_countsIt_andGivesUpAfterTheLimit() = runTest {
        feed(loaded = listOf(1))
        pipeline.generatePreviews()
        generator.upgradeOutcomes[1] = UpgradeOutcome.RetryLater("timeout")

        repeat(ThumbnailPipeline.MAX_UPGRADE_ATTEMPTS - 1) {
            assertEquals(UpgradeRunResult.RetryLater, pipeline.upgrade())
        }
        assertEquals(UpgradeState.PENDING, thumbnailDao.get(1)!!.upgradeState)
        assertEquals(UpgradeRunResult.RetryLater, pipeline.upgrade())

        assertEquals(UpgradeState.GAVE_UP, thumbnailDao.get(1)!!.upgradeState)
        assertEquals(UpgradeRunResult.Completed, pipeline.upgrade())
    }

    @Test
    fun cleanUp_evictsStaleStoriesAndDeletesOrphanedFiles() = runTest {
        val old = now.minus(ThumbnailPipeline.STALE_AFTER).minusSeconds(60)
        storyDao.upsert(listOf(story(1, fetchedAt = now), story(2, fetchedAt = old)))
        topStoryDao.replaceSnapshot(listOf(1))
        thumbnailDao.upsert(thumbnail(1, store.write(1, ThumbnailSource.ON_DEVICE, byteArrayOf(1))))
        thumbnailDao.upsert(thumbnail(2, store.write(2, ThumbnailSource.ON_DEVICE, byteArrayOf(2))))
        val stray = store.write(9, ThumbnailSource.CLOUD, byteArrayOf(9))
        // Make every file older than the grace period.
        File(folder.root, "thumbnails").listFiles()!!.forEach { it.setLastModified(old.toEpochMilli()) }

        pipeline.cleanUp()

        assertEquals(null, thumbnailDao.get(2), "Story 2 left the snapshot a week ago")
        assertEquals(listOf("1-on_device.webp"), File(folder.root, "thumbnails").list()!!.toList())
        assertFalse(File(store.path(stray)).exists())
    }

    private suspend fun feed(loaded: List<Long>, unloaded: List<Long> = emptyList()) {
        storyDao.upsert((loaded + unloaded).map { story(it) })
        topStoryDao.replaceSnapshot(loaded + unloaded)
        topStoryDao.markLoaded(loaded)
    }

    private fun story(id: Long, fetchedAt: Instant = now) = StoryEntity(
        id = id,
        title = "Story $id",
        author = "author$id",
        score = 1,
        postedAt = now,
        url = null,
        commentCount = 0,
        type = StoryType.STORY,
        fetchedAt = fetchedAt,
    )

    private fun thumbnail(storyId: Long, fileName: String) = ThumbnailEntity(
        storyId = storyId,
        fileName = fileName,
        source = ThumbnailSource.ON_DEVICE,
        upgradeState = UpgradeState.PENDING,
        upgradeAttempts = 0,
        updatedAt = now,
    )
}
