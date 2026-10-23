package com.emirkuljanin.hnfeed.feature.feed

import androidx.paging.testing.asSnapshot
import com.emirkuljanin.hnfeed.core.testing.MainDispatcherRule
import com.emirkuljanin.hnfeed.core.testing.data.testStories
import com.emirkuljanin.hnfeed.core.testing.repository.FakeTopStoriesRepository
import com.emirkuljanin.hnfeed.core.testing.util.TestNetworkMonitor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class FeedViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val networkMonitor = TestNetworkMonitor()

    @Test
    fun stories_exposesTheRepositoryFeed() = runTest {
        val viewModel = FeedViewModel(FakeTopStoriesRepository(testStories), networkMonitor)

        assertEquals(testStories, viewModel.stories.asSnapshot())
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class) // UnconfinedTestDispatcher
    fun uiState_followsTheConnection() = runTest {
        val viewModel = FeedViewModel(FakeTopStoriesRepository(testStories), networkMonitor)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertFalse(viewModel.uiState.value.isOffline)
        networkMonitor.setOnline(false)
        assertTrue(viewModel.uiState.value.isOffline)
    }
}
