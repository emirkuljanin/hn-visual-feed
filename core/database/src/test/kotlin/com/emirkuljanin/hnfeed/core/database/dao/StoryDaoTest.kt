package com.emirkuljanin.hnfeed.core.database.dao

import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadResult
import com.emirkuljanin.hnfeed.core.database.DatabaseTest
import com.emirkuljanin.hnfeed.core.database.model.StoryEntity
import com.emirkuljanin.hnfeed.core.database.storyEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

internal class StoryDaoTest : DatabaseTest() {

    private val storyDao get() = database.storyDao()
    private val topStoryDao get() = database.topStoryDao()

    @Test
    fun topStories_followSnapshotRank_notInsertionOrderOrId() = runTest {
        storyDao.upsert(listOf(storyEntity(1), storyEntity(2), storyEntity(3)))
        topStoryDao.replaceSnapshot(listOf(2, 3, 1))
        topStoryDao.markLoaded(listOf(2, 3, 1))

        assertEquals(listOf(2L, 3L, 1L), loadTopStories().map { it.id })
    }

    @Test
    fun topStories_afterRefresh_areOnlyTheLoadedTopRanks_evenWhenLowerRanksAreStillCached() = runTest {
        // Everything is cached from an earlier snapshot.
        storyDao.upsert((1L..5L).map { storyEntity(it) })
        topStoryDao.replaceSnapshot(listOf(1, 2, 3, 4, 5))
        topStoryDao.markLoaded(listOf(1, 2, 3, 4, 5))

        // A refresh re-ranks them and loads the first page only.
        topStoryDao.replaceSnapshot(listOf(5, 4, 3, 2, 1))
        topStoryDao.markLoaded(listOf(5, 4))

        assertEquals(listOf(5L, 4L), loadTopStories().map { it.id })
    }

    @Test
    fun topStories_skipLoadedEntriesWithoutDetails_suchAsDeletedItems() = runTest {
        storyDao.upsert(listOf(storyEntity(1), storyEntity(3)))
        topStoryDao.replaceSnapshot(listOf(1, 2, 3))
        topStoryDao.markLoaded(listOf(1, 2, 3))

        assertEquals(listOf(1L, 3L), loadTopStories().map { it.id })
    }

    @Test
    fun topStories_leaveOutCachedStoriesThatAreNotInTheSnapshot() = runTest {
        storyDao.upsert(listOf(storyEntity(1), storyEntity(99)))
        topStoryDao.replaceSnapshot(listOf(1))
        topStoryDao.markLoaded(listOf(1))

        assertEquals(listOf(1L), loadTopStories().map { it.id })
    }

    @Test
    fun delete_removesACachedStory_soALoadedEntryForItDisappearsFromTheFeed() = runTest {
        // Story 2 was cached from an earlier snapshot; the new snapshot still lists it but the API now says it's dead.
        storyDao.upsert(listOf(storyEntity(1), storyEntity(2), storyEntity(3)))
        topStoryDao.replaceSnapshot(listOf(1, 2, 3))

        storyDao.delete(listOf(2))
        topStoryDao.markLoaded(listOf(1, 2, 3))

        assertEquals(listOf(1L, 3L), loadTopStories().map { it.id })
    }

    @Test
    fun upsert_updatesAnExistingStory() = runTest {
        topStoryDao.replaceSnapshot(listOf(1))
        topStoryDao.markLoaded(listOf(1))
        storyDao.upsert(listOf(storyEntity(1, score = 10)))

        storyDao.upsert(listOf(storyEntity(1, score = 250)))

        assertEquals(listOf(storyEntity(1, score = 250)), loadTopStories())
    }

    private suspend fun loadTopStories(): List<StoryEntity> {
        val result = storyDao.topStoriesPagingSource().load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 100, placeholdersEnabled = false),
        )
        return (result as LoadResult.Page).data.map { it.story }
    }
}
