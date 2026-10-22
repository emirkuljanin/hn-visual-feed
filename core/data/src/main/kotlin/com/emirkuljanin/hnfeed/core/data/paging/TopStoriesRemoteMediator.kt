package com.emirkuljanin.hnfeed.core.data.paging

import android.database.SQLException
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.emirkuljanin.hnfeed.core.data.model.asStoryEntityOrNull
import com.emirkuljanin.hnfeed.core.database.DatabaseTransactionRunner
import com.emirkuljanin.hnfeed.core.database.dao.StoryDao
import com.emirkuljanin.hnfeed.core.database.dao.TopStoryDao
import com.emirkuljanin.hnfeed.core.database.model.StoryEntity
import com.emirkuljanin.hnfeed.core.network.HnNetworkDataSource
import com.emirkuljanin.hnfeed.core.network.model.NetworkItem
import java.io.IOException
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

/**
 * How long a snapshot counts as current. A new Pager (cold start, return after process death) within this window
 * keeps the cached feed and the user's scroll position instead of re-ranking everything under them.
 */
internal val SNAPSHOT_MAX_AGE: Duration = Duration.ofMinutes(15)

/**
 * Fills the database for the top-stories feed. The UI only ever reads the database (see
 * `StoryDao.topStoriesPagingSource`); this class decides when to fetch and writes the results.
 *
 * - **initialize**: skips the launch refresh while the stored snapshot is younger than [SNAPSHOT_MAX_AGE].
 *   A refresh the user asks for always runs.
 * - **REFRESH** fetches the current ranking and its first page, then replaces the snapshot and writes the page in
 *   one transaction. Network first, database second: a failed refresh leaves the previous feed untouched.
 * - **APPEND** takes the next unloaded ranks of the stored snapshot (it does not re-fetch the ranking, so pages
 *   never shift) and writes them the same way.
 * - **PREPEND** never happens: the feed starts at rank 0.
 *
 * Every requested id is marked loaded, including ones that turned out missing, deleted or dead, so paging always
 * moves forward; cached rows for those are deleted so a killed story cannot resurface.
 *
 * Failures become [MediatorResult.Error] (shown by the UI as a load state) rather than exceptions, which would
 * otherwise escape into Paging's scope and crash the app.
 */
@OptIn(ExperimentalPagingApi::class)
internal class TopStoriesRemoteMediator @Inject constructor(
    private val network: HnNetworkDataSource,
    private val topStoryDao: TopStoryDao,
    private val storyDao: StoryDao,
    private val transaction: DatabaseTransactionRunner,
    private val clock: Clock,
) : RemoteMediator<Int, StoryEntity>() {

    override suspend fun initialize(): InitializeAction {
        val refreshedAt = topStoryDao.snapshotLoadedAt() ?: return InitializeAction.LAUNCH_INITIAL_REFRESH
        val isFresh = Duration.between(refreshedAt, clock.instant()) < SNAPSHOT_MAX_AGE
        return if (isFresh) InitializeAction.SKIP_INITIAL_REFRESH else InitializeAction.LAUNCH_INITIAL_REFRESH
    }

    override suspend fun load(loadType: LoadType, state: PagingState<Int, StoryEntity>): MediatorResult {
        val pageSize = state.config.pageSize
        return try {
            when (loadType) {
                LoadType.REFRESH -> refresh(pageSize)
                LoadType.PREPEND -> MediatorResult.Success(endOfPaginationReached = true)
                LoadType.APPEND -> append(pageSize)
            }
        } catch (e: IOException) {
            // The network layer reports every failure as an IOException (HnApiException for bad responses).
            MediatorResult.Error(e)
        } catch (e: SQLException) {
            // E.g. a full disk. The cache may be unusable, but that is an error state, not a crash.
            MediatorResult.Error(e)
        }
    }

    private suspend fun refresh(pageSize: Int): MediatorResult {
        val ranking = network.topStoryIds().distinct()
        // HN always has top stories; an empty list is a bad response and must not wipe the offline feed.
        if (ranking.isEmpty()) return MediatorResult.Error(IOException("The API returned no top stories"))

        val firstPage = ranking.take(pageSize)
        val items = network.items(firstPage)
        transaction {
            topStoryDao.replaceSnapshot(ranking)
            writePage(requestedIds = firstPage, items = items)
        }
        return MediatorResult.Success(endOfPaginationReached = ranking.size <= pageSize)
    }

    private suspend fun append(pageSize: Int): MediatorResult {
        val pageIds = topStoryDao.nextUnloadedStoryIds(limit = pageSize)
        if (pageIds.isEmpty()) return MediatorResult.Success(endOfPaginationReached = true)

        val items = network.items(pageIds)
        transaction {
            // Another Pager may have refreshed while we were fetching. Writing then would mark ids loaded at their
            // new, arbitrary ranks and break the "feed is the loaded top N" rule; the next APPEND starts over instead.
            if (topStoryDao.nextUnloadedStoryIds(limit = pageIds.size) == pageIds) {
                writePage(requestedIds = pageIds, items = items)
            }
        }
        return MediatorResult.Success(endOfPaginationReached = false)
    }

    /** Must run inside a transaction together with the snapshot write it belongs to. */
    private suspend fun writePage(requestedIds: List<Long>, items: List<NetworkItem>) {
        val fetchedAt = clock.instant()
        val stories = items.mapNotNull { it.asStoryEntityOrNull(fetchedAt) }
        val showableIds = stories.mapTo(HashSet()) { it.id }

        storyDao.upsert(stories)
        storyDao.delete(requestedIds.filterNot { it in showableIds })
        topStoryDao.markLoaded(requestedIds)
    }
}
