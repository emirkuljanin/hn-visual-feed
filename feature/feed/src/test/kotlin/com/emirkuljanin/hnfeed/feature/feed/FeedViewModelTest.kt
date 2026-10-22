package com.emirkuljanin.hnfeed.feature.feed

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import com.emirkuljanin.hnfeed.core.data.repository.TopStoriesRepository
import com.emirkuljanin.hnfeed.core.model.Story
import com.emirkuljanin.hnfeed.core.model.StoryType
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {

    @BeforeTest
    fun setUp() {
        // viewModelScope runs on Dispatchers.Main, which does not exist on the JVM.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun stories_exposesTheRepositoryFeed() = runTest {
        val stories = listOf(story(1), story(2))
        val viewModel = FeedViewModel(FakeTopStoriesRepository(stories))

        assertEquals(stories, viewModel.stories.asSnapshot())
    }

    private class FakeTopStoriesRepository(private val stories: List<Story>) : TopStoriesRepository {
        // A complete, static feed: without end-of-pagination states asSnapshot would wait for more pages.
        override fun topStories(): Flow<PagingData<Story>> = flowOf(
            PagingData.from(
                data = stories,
                sourceLoadStates = LoadStates(
                    refresh = LoadState.NotLoading(endOfPaginationReached = false),
                    prepend = LoadState.NotLoading(endOfPaginationReached = true),
                    append = LoadState.NotLoading(endOfPaginationReached = true),
                ),
            ),
        )
    }

    private fun story(id: Long) = Story(
        id = id,
        title = "Story $id",
        author = "author$id",
        score = 1,
        postedAt = Instant.parse("2026-10-23T10:00:00Z"),
        url = null,
        commentCount = 0,
        type = StoryType.STORY,
    )
}
