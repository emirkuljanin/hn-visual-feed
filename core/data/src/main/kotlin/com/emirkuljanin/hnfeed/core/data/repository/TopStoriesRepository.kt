package com.emirkuljanin.hnfeed.core.data.repository

import androidx.paging.PagingData
import com.emirkuljanin.hnfeed.core.model.Story
import kotlinx.coroutines.flow.Flow

interface TopStoriesRepository {

    /**
     * The top-stories feed, best first. Emits cached stories immediately (also offline) and refreshes them from the
     * network on first collection; further pages load as the UI scrolls. Refresh/retry through the collector's
     * `refresh()`/`retry()`; errors arrive as `LoadState.Error` rather than exceptions.
     */
    fun topStories(): Flow<PagingData<Story>>
}
