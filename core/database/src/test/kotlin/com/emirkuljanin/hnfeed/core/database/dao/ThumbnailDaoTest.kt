package com.emirkuljanin.hnfeed.core.database.dao

import androidx.paging.PagingSource.LoadParams
import androidx.paging.PagingSource.LoadResult
import com.emirkuljanin.hnfeed.core.database.DatabaseTest
import com.emirkuljanin.hnfeed.core.database.model.ThumbnailEntity
import com.emirkuljanin.hnfeed.core.database.model.UpgradeState
import com.emirkuljanin.hnfeed.core.database.storyEntity
import com.emirkuljanin.hnfeed.core.model.ThumbnailSource
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

internal class ThumbnailDaoTest : DatabaseTest() {

    private val storyDao get() = database.storyDao()
    private val topStoryDao get() = database.topStoryDao()
    private val dao get() = database.thumbnailDao()
    private val now = Instant.parse("2026-10-23T12:00:00Z")

    @Test
    fun storiesNeedingPreview_areLoadedFeedStoriesWithoutAThumbnail_bestRankFirst() = runTest {
        feed(loaded = listOf(3, 1, 2), unloaded = listOf(4))
        dao.upsert(thumbnail(1))

        assertEquals(listOf(3L, 2L), dao.storiesNeedingPreview(limit = 10).map { it.storyId })
        assertEquals("Story 3", dao.storiesNeedingPreview(limit = 1).single().title)
    }

    @Test
    fun storiesNeedingUpgrade_arePendingThumbnailsOfLoadedFeedStories_bestRankFirst() = runTest {
        feed(loaded = listOf(3, 1, 2), unloaded = listOf(4))
        dao.upsert(thumbnail(1))
        dao.upsert(thumbnail(2, state = UpgradeState.DONE))
        dao.upsert(thumbnail(3, attempts = 2))
        dao.upsert(thumbnail(4)) // pending, but not in the visible feed

        val work = dao.storiesNeedingUpgrade(limit = 10)

        assertEquals(listOf(3L, 1L), work.map { it.storyId })
        assertEquals("3-on_device.webp", work.first().fileName)
        assertEquals(2, work.first().upgradeAttempts)
    }

    @Test
    fun updateUpgrade_changesStateAndAttempts() = runTest {
        feed(loaded = listOf(1))
        dao.upsert(thumbnail(1))

        dao.updateUpgrade(1, UpgradeState.GAVE_UP, attempts = 3, updatedAt = now.plusSeconds(60))

        val row = dao.get(1)!!
        assertEquals(UpgradeState.GAVE_UP, row.upgradeState)
        assertEquals(3, row.upgradeAttempts)
    }

    @Test
    fun deletingAStory_deletesItsThumbnail() = runTest {
        feed(loaded = listOf(1, 2))
        dao.upsert(thumbnail(1))
        dao.upsert(thumbnail(2))

        storyDao.delete(listOf(1))

        assertNull(dao.get(1))
        assertEquals(listOf("2-on_device.webp"), dao.fileNames())
    }

    @Test
    fun upsertingAStoryAgain_keepsItsThumbnail() = runTest {
        feed(loaded = listOf(1))
        dao.upsert(thumbnail(1))

        storyDao.upsert(listOf(storyEntity(1, score = 999)))

        assertEquals("1-on_device.webp", dao.get(1)?.fileName)
    }

    @Test
    fun deleteStale_removesOldStoriesOutsideTheSnapshot_andTheirThumbnails() = runTest {
        val old = now.minusSeconds(8 * 24 * 3_600)
        storyDao.upsert(
            listOf(
                storyEntity(1, fetchedAt = old), // in the snapshot: kept however old
                storyEntity(2, fetchedAt = old), // left the snapshot and old: evicted
                storyEntity(3, fetchedAt = now), // left the snapshot but recent: kept
            ),
        )
        topStoryDao.replaceSnapshot(listOf(1))
        dao.upsert(thumbnail(2))

        val evicted = storyDao.deleteStale(cutoff = now.minusSeconds(7 * 24 * 3_600))

        assertEquals(1, evicted)
        assertNull(dao.get(2))
        topStoryDao.replaceSnapshot(listOf(1, 2, 3))
        topStoryDao.markLoaded(listOf(1, 2, 3))
        assertEquals(listOf(1L, 3L), feedIds())
    }

    @Test
    fun feedRows_carryTheThumbnail_onceThereIsOne() = runTest {
        feed(loaded = listOf(1, 2))
        dao.upsert(thumbnail(1, source = ThumbnailSource.CLOUD))

        val rows = (storyDao.topStoriesPagingSource().load(LoadParams.Refresh(null, 10, false)) as LoadResult.Page).data

        assertEquals("1-cloud.webp", rows[0].thumbnailFile)
        assertEquals(ThumbnailSource.CLOUD, rows[0].thumbnailSource)
        assertNull(rows[1].thumbnailFile)
    }

    private suspend fun feed(loaded: List<Long>, unloaded: List<Long> = emptyList()) {
        storyDao.upsert((loaded + unloaded).map { storyEntity(it) })
        topStoryDao.replaceSnapshot(loaded + unloaded)
        topStoryDao.markLoaded(loaded)
    }

    private suspend fun feedIds(): List<Long> =
        (storyDao.topStoriesPagingSource().load(LoadParams.Refresh(null, 10, false)) as LoadResult.Page)
            .data.map { it.story.id }

    private fun thumbnail(
        storyId: Long,
        source: ThumbnailSource = ThumbnailSource.ON_DEVICE,
        state: UpgradeState = UpgradeState.PENDING,
        attempts: Int = 0,
    ) = ThumbnailEntity(
        storyId = storyId,
        fileName = "$storyId-${source.name.lowercase()}.webp",
        source = source,
        upgradeState = state,
        upgradeAttempts = attempts,
        updatedAt = now,
    )
}
