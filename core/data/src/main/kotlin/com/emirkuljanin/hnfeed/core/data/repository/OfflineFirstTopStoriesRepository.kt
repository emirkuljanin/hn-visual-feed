package com.emirkuljanin.hnfeed.core.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.emirkuljanin.hnfeed.core.data.paging.TopStoriesRemoteMediator
import com.emirkuljanin.hnfeed.core.data.thumbnail.ThumbnailScheduler
import com.emirkuljanin.hnfeed.core.data.thumbnail.ThumbnailStore
import com.emirkuljanin.hnfeed.core.database.dao.StoryDao
import com.emirkuljanin.hnfeed.core.database.model.asExternalModel
import com.emirkuljanin.hnfeed.core.model.Story
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** One feed page is one parallel batch of item requests, so this is also the network batch size. */
internal const val TOP_STORIES_PAGE_SIZE = 20

internal class OfflineFirstTopStoriesRepository @Inject constructor(
    private val storyDao: StoryDao,
    private val remoteMediator: Provider<TopStoriesRemoteMediator>,
    private val thumbnailStore: ThumbnailStore,
    private val thumbnailScheduler: ThumbnailScheduler,
) : TopStoriesRepository {

    @OptIn(ExperimentalPagingApi::class)
    override fun topStories(): Flow<PagingData<Story>> = Pager(
        config = PagingConfig(
            pageSize = TOP_STORIES_PAGE_SIZE,
            initialLoadSize = TOP_STORIES_PAGE_SIZE,
            // Placeholders make list indices absolute positions in the feed, so a story keeps its index when a
            // write (a thumbnail, an opened story's refresh) invalidates the source and Room reloads around the
            // anchor, and an index restored after process death points at the same story. They only span stories
            // already loaded into Room (the query's row count), not the whole ~500-id snapshot.
            enablePlaceholders = true,
        ),
        // A fresh mediator per Pager: Paging drives each mediator from a single Pager and never shares one.
        remoteMediator = remoteMediator.get(),
        pagingSourceFactory = storyDao::topStoriesPagingSource,
    ).flow
        // A cached feed shown without a refresh (fresh snapshot, offline) may still lack thumbnails.
        .onStart { thumbnailScheduler.schedule() }
        .map { pagingData -> pagingData.map { it.asExternalModel(thumbnailStore::path) } }
}
