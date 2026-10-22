package com.emirkuljanin.hnfeed.core.database.dao

import com.emirkuljanin.hnfeed.core.database.DatabaseTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

internal class TopStoryDaoTest : DatabaseTest() {

    private val dao get() = database.topStoryDao()

    @Test
    fun nextUnloadedStoryIds_returnsBestRanksFirst_limitedToTheRequestedCount() = runTest {
        dao.replaceSnapshot(listOf(30, 10, 20, 40))

        assertEquals(listOf(30L, 10L), dao.nextUnloadedStoryIds(limit = 2))
    }

    @Test
    fun markLoaded_excludesEntriesFromTheNextPage() = runTest {
        dao.replaceSnapshot(listOf(30, 10, 20, 40))

        dao.markLoaded(listOf(30, 10))

        assertEquals(listOf(20L, 40L), dao.nextUnloadedStoryIds(limit = 10))
    }

    @Test
    fun nextUnloadedStoryIds_isEmpty_onceTheWholeSnapshotIsLoaded() = runTest {
        dao.replaceSnapshot(listOf(1, 2))
        dao.markLoaded(listOf(1, 2))

        assertTrue(dao.nextUnloadedStoryIds(limit = 10).isEmpty())
    }

    @Test
    fun replaceSnapshot_dropsThePreviousSnapshot_andResetsLoadedState() = runTest {
        dao.replaceSnapshot(listOf(1, 2, 3))
        dao.markLoaded(listOf(1, 2, 3))

        dao.replaceSnapshot(listOf(3, 4))

        assertEquals(listOf(3L, 4L), dao.nextUnloadedStoryIds(limit = 10))
    }

    @Test
    fun replaceSnapshot_keepsTheBestRank_ofADuplicatedId() = runTest {
        dao.replaceSnapshot(listOf(5, 6, 5, 7))

        assertEquals(listOf(5L, 6L, 7L), dao.nextUnloadedStoryIds(limit = 10))
    }
}
