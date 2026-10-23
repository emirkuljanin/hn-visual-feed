package com.emirkuljanin.hnfeed.core.testing.repository

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import com.emirkuljanin.hnfeed.core.data.repository.TopStoriesRepository
import com.emirkuljanin.hnfeed.core.model.Story
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** A repository whose feed is a fixed, fully loaded list of [stories]. */
class FakeTopStoriesRepository(private val stories: List<Story> = emptyList()) : TopStoriesRepository {
    override fun topStories(): Flow<PagingData<Story>> = flowOf(completeFeed(stories))
}

/**
 * A static, fully loaded feed. Plain `PagingData.from(list)` leaves appends open, so consumers such as
 * `asSnapshot` would wait for pages that never come.
 */
fun completeFeed(stories: List<Story>): PagingData<Story> = PagingData.from(
    data = stories,
    sourceLoadStates = LoadStates(
        refresh = LoadState.NotLoading(endOfPaginationReached = false),
        prepend = LoadState.NotLoading(endOfPaginationReached = true),
        append = LoadState.NotLoading(endOfPaginationReached = true),
    ),
)
