package com.emirkuljanin.hnfeed.feature.feed

import androidx.paging.testing.asSnapshot
import com.emirkuljanin.hnfeed.core.testing.MainDispatcherRule
import com.emirkuljanin.hnfeed.core.testing.data.testStories
import com.emirkuljanin.hnfeed.core.testing.repository.FakeTopStoriesRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class FeedViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun stories_exposesTheRepositoryFeed() = runTest {
        val viewModel = FeedViewModel(FakeTopStoriesRepository(testStories))

        assertEquals(testStories, viewModel.stories.asSnapshot())
    }
}
