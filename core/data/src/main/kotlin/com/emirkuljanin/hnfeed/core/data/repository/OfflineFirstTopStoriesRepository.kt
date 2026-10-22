package com.emirkuljanin.hnfeed.core.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.emirkuljanin.hnfeed.core.data.paging.TopStoriesRemoteMediator
import com.emirkuljanin.hnfeed.core.database.dao.StoryDao
import com.emirkuljanin.hnfeed.core.database.model.asExternalModel
import com.emirkuljanin.hnfeed.core.model.Story
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** One feed page is one parallel batch of item requests, so this is also the network batch size. */
internal const val TOP_STORIES_PAGE_SIZE = 20

internal class OfflineFirstTopStoriesRepository @Inject constructor(
    private val storyDao: StoryDao,
    private val remoteMediator: Provider<TopStoriesRemoteMediator>,
) : TopStoriesRepository {

    @OptIn(ExperimentalPagingApi::class)
    override fun topStories(): Flow<PagingData<Story>> = Pager(
        config = PagingConfig(
            pageSize = TOP_STORIES_PAGE_SIZE,
            initialLoadSize = TOP_STORIES_PAGE_SIZE,
            // No placeholders: the snapshot size is known, but showing 500 empty cards is not useful.
            enablePlaceholders = false,
        ),
        // A fresh mediator per Pager: Paging drives each mediator from a single Pager and never shares one.
        remoteMediator = remoteMediator.get(),
        pagingSourceFactory = storyDao::topStoriesPagingSource,
    ).flow.map { pagingData -> pagingData.map { it.asExternalModel() } }
}
