package com.emirkuljanin.hnfeed.core.data.paging

import android.database.sqlite.SQLiteFullException
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator.InitializeAction
import androidx.paging.RemoteMediator.MediatorResult
import com.emirkuljanin.hnfeed.core.data.testing.FakeHnNetworkDataSource
import com.emirkuljanin.hnfeed.core.data.testing.InMemoryDatabaseTest
import com.emirkuljanin.hnfeed.core.data.testing.networkStory
import com.emirkuljanin.hnfeed.core.database.DatabaseTransactionRunner
import com.emirkuljanin.hnfeed.core.database.model.StoryEntity
import java.io.IOException
import java.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalPagingApi::class)
internal class TopStoriesRemoteMediatorTest : InMemoryDatabaseTest() {

    private val network = FakeHnNetworkDataSource()
    private val mediator by lazy { mediator() }

    private fun mediator(clock: Clock = this.clock, transaction: DatabaseTransactionRunner = this.transaction) =
        TopStoriesRemoteMediator(network, topStoryDao, storyDao, transaction, clock)

    @Test
    fun initialize_withNothingCached_launchesARefresh() = runTest {
        assertEquals(InitializeAction.LAUNCH_INITIAL_REFRESH, mediator.initialize())
    }

    @Test
    fun initialize_withAFreshSnapshot_skipsTheRefresh_andKeepsTheCachedFeed() = runTest {
        network.ranking = listOf(1, 2)
        network.addStories(1, 2)
        mediator.load(LoadType.REFRESH, state(pageSize = 2))

        val later = mediator(clock = Clock.offset(clock, SNAPSHOT_MAX_AGE.minusMinutes(1)))

        assertEquals(InitializeAction.SKIP_INITIAL_REFRESH, later.initialize())
    }

    @Test
    fun initialize_withAStaleSnapshot_launchesARefresh() = runTest {
        network.ranking = listOf(1, 2)
        network.addStories(1, 2)
        mediator.load(LoadType.REFRESH, state(pageSize = 2))

        val later = mediator(clock = Clock.offset(clock, SNAPSHOT_MAX_AGE))

        assertEquals(InitializeAction.LAUNCH_INITIAL_REFRESH, later.initialize())
    }

    @Test
    fun refresh_storesTheRanking_andShowsItsFirstPage() = runTest {
        network.ranking = listOf(5, 4, 3, 2, 1)
        network.addStories(1, 2, 3, 4, 5)

        val result = mediator.load(LoadType.REFRESH, state(pageSize = 2))

        assertEndOfPagination(result, expected = false)
        assertEquals(listOf(5L, 4L), feedIds())
        assertEquals(listOf(5L, 4L), network.itemRequests.single())
        assertEquals(listOf(3L, 2L, 1L), topStoryDao.nextUnloadedStoryIds(limit = 10))
    }

    @Test
    fun refresh_whenTheWholeRankingFitsOnePage_reportsEndOfPagination() = runTest {
        network.ranking = listOf(1, 2)
        network.addStories(1, 2)

        val result = mediator.load(LoadType.REFRESH, state(pageSize = 2))

        assertEndOfPagination(result, expected = true)
    }

    @Test
    fun refresh_hidesUnshowableItems_butMarksThemLoaded_soPagingMovesOn() = runTest {
        network.ranking = listOf(1, 2, 3, 4, 5, 6, 7)
        network.addStories(1, 7)
        network.items[2] = networkStory(2).copy(deleted = true)
        network.items[3] = networkStory(3).copy(dead = true)
        network.items[4] = networkStory(4, type = "comment")
        network.items[5] = networkStory(5).copy(title = null)
        // 6 is unknown to the API (JSON null).

        mediator.load(LoadType.REFRESH, state(pageSize = 6))

        assertEquals(listOf(1L), feedIds())
        assertEquals(listOf(7L), topStoryDao.nextUnloadedStoryIds(limit = 10))
    }

    @Test
    fun refresh_removesTheCachedRowOfAStoryThatHasSinceBeenKilled() = runTest {
        network.ranking = listOf(1, 2)
        network.addStories(1, 2)
        mediator.load(LoadType.REFRESH, state(pageSize = 2))

        network.items[2] = networkStory(2).copy(dead = true)
        mediator.load(LoadType.REFRESH, state(pageSize = 2))

        assertEquals(listOf(1L), feedIds())
    }

    @Test
    fun refresh_whenTheRankingRequestFails_keepsThePreviousFeed_andReturnsTheError() = runTest {
        network.ranking = listOf(1, 2)
        network.addStories(1, 2)
        mediator.load(LoadType.REFRESH, state(pageSize = 2))
        val failure = IOException("offline")
        network.rankingFailure = failure

        val result = mediator.load(LoadType.REFRESH, state(pageSize = 2))

        assertSame(failure, assertIs<MediatorResult.Error>(result).throwable)
        assertEquals(listOf(1L, 2L), feedIds())
    }

    @Test
    fun refresh_whenAnItemRequestFails_keepsThePreviousSnapshot() = runTest {
        network.ranking = listOf(1, 2, 3)
        network.addStories(1, 2, 3)
        mediator.load(LoadType.REFRESH, state(pageSize = 1))
        network.ranking = listOf(3, 2, 1)
        network.itemsFailure = IOException("timeout")

        val result = mediator.load(LoadType.REFRESH, state(pageSize = 1))

        assertIs<MediatorResult.Error>(result)
        assertEquals(listOf(1L), feedIds())
        assertEquals(listOf(2L, 3L), topStoryDao.nextUnloadedStoryIds(limit = 10))
    }

    @Test
    fun refresh_whenTheRankingIsEmpty_keepsThePreviousFeed_andReturnsAnError() = runTest {
        network.ranking = listOf(1, 2)
        network.addStories(1, 2)
        mediator.load(LoadType.REFRESH, state(pageSize = 2))
        network.ranking = emptyList()

        val result = mediator.load(LoadType.REFRESH, state(pageSize = 2))

        assertIs<MediatorResult.Error>(result)
        assertEquals(listOf(1L, 2L), feedIds())
    }

    @Test
    fun load_whenTheDatabaseFails_returnsAnError_insteadOfThrowing() = runTest {
        network.ranking = listOf(1)
        network.addStories(1)
        val diskFull = SQLiteFullException("database or disk is full")
        val failingTransaction = object : DatabaseTransactionRunner {
            override suspend fun <T> invoke(block: suspend () -> T): T = throw diskFull
        }

        val result = mediator(transaction = failingTransaction).load(LoadType.REFRESH, state(pageSize = 1))

        assertSame(diskFull, assertIs<MediatorResult.Error>(result).throwable)
    }

    @Test
    fun append_whenARefreshReplacedTheSnapshotDuringTheFetch_writesNothing() = runTest {
        network.ranking = listOf(1, 2, 3, 4, 5)
        network.addStories(1, 2, 3, 4, 5, 6, 7)
        mediator.load(LoadType.REFRESH, state(pageSize = 2))
        // While this APPEND fetches ranks 2-3 (ids 3, 4), another Pager refreshes to a new ranking.
        network.whileFetchingItems = {
            network.whileFetchingItems = null
            network.ranking = listOf(7, 6, 5, 4, 3)
            mediator.load(LoadType.REFRESH, state(pageSize = 2))
        }

        val result = mediator.load(LoadType.APPEND, state(pageSize = 2))

        assertEndOfPagination(result, expected = false)
        // Ids 3 and 4 were not marked loaded at their new ranks; the feed is still the new snapshot's top 2.
        assertEquals(listOf(7L, 6L), feedIds())
        assertEquals(listOf(5L, 4L, 3L), topStoryDao.nextUnloadedStoryIds(limit = 10))
    }

    @Test
    fun append_loadsTheNextRanksOfTheStoredSnapshot_withoutRefetchingTheRanking() = runTest {
        network.ranking = listOf(1, 2, 3, 4, 5)
        network.addStories(1, 2, 3, 4, 5)
        mediator.load(LoadType.REFRESH, state(pageSize = 2))
        network.ranking = listOf(5, 4, 3, 2, 1) // The live ranking moved on meanwhile.

        val result = mediator.load(LoadType.APPEND, state(pageSize = 2))

        assertEndOfPagination(result, expected = false)
        assertEquals(1, network.rankingRequests)
        assertEquals(listOf(1L, 2L, 3L, 4L), feedIds())
    }

    @Test
    fun append_onceTheSnapshotIsFullyLoaded_reportsEndOfPagination_withoutCallingTheApi() = runTest {
        network.ranking = listOf(1, 2)
        network.addStories(1, 2)
        mediator.load(LoadType.REFRESH, state(pageSize = 2))

        val result = mediator.load(LoadType.APPEND, state(pageSize = 2))

        assertEndOfPagination(result, expected = true)
        assertEquals(1, network.itemRequests.size)
    }

    @Test
    fun append_whenItFails_returnsTheError_andLeavesThePageToBeRetried() = runTest {
        network.ranking = listOf(1, 2, 3)
        network.addStories(1, 2, 3)
        mediator.load(LoadType.REFRESH, state(pageSize = 1))
        network.itemsFailure = IOException("offline")

        val result = mediator.load(LoadType.APPEND, state(pageSize = 1))

        assertIs<MediatorResult.Error>(result)
        assertEquals(listOf(2L, 3L), topStoryDao.nextUnloadedStoryIds(limit = 10))
    }

    @Test
    fun prepend_isAlwaysTheEndOfPagination() = runTest {
        val result = mediator.load(LoadType.PREPEND, state(pageSize = 2))

        assertEndOfPagination(result, expected = true)
        assertTrue(network.itemRequests.isEmpty())
    }

    private fun state(pageSize: Int) = PagingState<Int, StoryEntity>(
        pages = emptyList(),
        anchorPosition = null,
        config = PagingConfig(pageSize = pageSize),
        leadingPlaceholderCount = 0,
    )

    private fun assertEndOfPagination(result: MediatorResult, expected: Boolean) {
        assertEquals(expected, assertIs<MediatorResult.Success>(result).endOfPaginationReached)
    }
}
