package com.emirkuljanin.hnfeed.core.database

import androidx.paging.PagingSource.LoadParams
import androidx.paging.PagingSource.LoadResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

internal class RoomDatabaseTransactionRunnerTest : DatabaseTest() {

    private val transaction by lazy { RoomDatabaseTransactionRunner(database) }
    private val topStoryDao get() = database.topStoryDao()
    private val storyDao get() = database.storyDao()

    @Test
    fun commitsWritesAcrossDaos_andReturnsTheBlockResult() = runTest {
        val result = transaction {
            topStoryDao.replaceSnapshot(listOf(1))
            storyDao.upsert(listOf(storyEntity(1)))
            topStoryDao.markLoaded(listOf(1))
            "done"
        }

        assertEquals("done", result)
        // The feed only shows story 1 if all three writes were committed.
        assertEquals(listOf(1L), feedIds())
    }

    @Test
    fun rollsBackWritesToEveryTable_whenTheBlockThrows() = runTest {
        topStoryDao.replaceSnapshot(listOf(1, 2))
        storyDao.upsert(listOf(storyEntity(1)))
        topStoryDao.markLoaded(listOf(1))

        assertFailsWith<IllegalStateException> {
            transaction {
                topStoryDao.replaceSnapshot(listOf(3))
                storyDao.upsert(listOf(storyEntity(3)))
                topStoryDao.markLoaded(listOf(3))
                error("network failed halfway through a refresh")
            }
        }

        // Old snapshot (1 loaded, 2 pending) is intact, and story 3 never reached the stories table.
        assertEquals(listOf(1L), feedIds())
        assertEquals(listOf(2L), topStoryDao.nextUnloadedStoryIds(limit = 10))
        topStoryDao.replaceSnapshot(listOf(3))
        topStoryDao.markLoaded(listOf(3))
        assertEquals(emptyList(), feedIds())
    }

    private suspend fun feedIds(): List<Long> {
        val page = storyDao.topStoriesPagingSource().load(LoadParams.Refresh(key = null, loadSize = 10, false))
        return (page as LoadResult.Page).data.map { it.id }
    }
}
