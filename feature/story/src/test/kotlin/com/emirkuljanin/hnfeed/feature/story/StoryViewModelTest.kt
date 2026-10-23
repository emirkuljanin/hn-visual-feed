package com.emirkuljanin.hnfeed.feature.story

import androidx.lifecycle.SavedStateHandle
import com.emirkuljanin.hnfeed.core.testing.MainDispatcherRule
import com.emirkuljanin.hnfeed.core.testing.data.testStories
import com.emirkuljanin.hnfeed.core.testing.data.testStoryComments
import com.emirkuljanin.hnfeed.core.testing.repository.FakeStoryDetailsRepository
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class StoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val story = testStories.first()
    private val repository = FakeStoryDetailsRepository(story = story, comments = testStoryComments)

    @Test
    fun opening_refreshesTheStory_andShowsTheCachedOneMeanwhile() = runTest {
        repository.gate = CompletableDeferred()
        val viewModel = viewModel()
        viewModel.collectIn(this)

        val refreshing = viewModel.uiState.value
        assertEquals(story, refreshing.story)
        assertTrue(refreshing.isRefreshing)
        assertEquals(listOf(story.id), repository.refreshes)

        repository.gate!!.complete(Unit)
        val done = viewModel.uiState.first { !it.isRefreshing }
        assertFalse(done.refreshFailed)
        assertTrue(done.comments.isNotEmpty())
    }

    @Test
    fun aFailedRefresh_isReported_andRetryWorks() = runTest {
        repository.failure = IOException("offline")
        val viewModel = viewModel()
        viewModel.collectIn(this)
        assertTrue(viewModel.uiState.value.refreshFailed)

        repository.failure = null
        viewModel.refresh()

        assertFalse(viewModel.uiState.value.refreshFailed)
        assertEquals(2, repository.refreshes.size)
    }

    @Test
    fun noStoryCached_andTheRefreshFailed_leavesNoStory() = runTest {
        repository.story.value = null
        repository.failure = IOException("offline")
        val viewModel = viewModel()
        viewModel.collectIn(this)

        assertNull(viewModel.uiState.value.story)
        assertTrue(viewModel.uiState.value.refreshFailed)
    }

    @Test
    fun expandingACommentWithoutLoadedReplies_loadsThem_butExpandingOneWithRepliesDoesNot() = runTest {
        val viewModel = viewModel()
        viewModel.collectIn(this)

        viewModel.toggleReplies(102) // replies not loaded
        viewModel.toggleReplies(101) // replies loaded

        assertEquals(listOf(102L), repository.loadMoreRequests)
        val keys = viewModel.uiState.value.comments.map { it.key }
        assertTrue(keys.containsAll(listOf("comment-201", "comment-202")))
    }

    @Test
    fun collapsing_hidesTheReplies() = runTest {
        val viewModel = viewModel()
        viewModel.collectIn(this)
        viewModel.toggleReplies(101)

        viewModel.toggleReplies(101)

        assertFalse(viewModel.uiState.value.comments.any { it.key == "comment-201" })
    }

    @Test
    fun expandedComments_surviveProcessDeath() = runTest {
        val savedState = SavedStateHandle()
        viewModel(savedState).toggleReplies(101)

        val restored = viewModel(savedState)
        restored.collectIn(this)

        assertTrue(restored.uiState.value.comments.any { it.key == "comment-201" })
    }

    @Test
    fun loadMore_showsLoading_thenFailure_andIgnoresRepeatedRequestsWhileLoading() = runTest {
        val viewModel = viewModel()
        viewModel.collectIn(this)
        repository.gate = CompletableDeferred()
        repository.failure = IOException("offline")

        viewModel.loadMore(story.id)
        viewModel.loadMore(story.id)
        assertEquals(LoadState.LOADING, topLevelMore(viewModel).state)

        repository.gate!!.complete(Unit)
        assertEquals(LoadState.FAILED, topLevelMore(viewModel).state)
        assertEquals(listOf(story.id), repository.loadMoreRequests)
    }

    /** Keeps `uiState` subscribed (it is shared while collected), eagerly so each change is visible at once. */
    private fun StoryViewModel.collectIn(scope: TestScope) {
        scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { uiState.collect {} }
    }

    private fun topLevelMore(viewModel: StoryViewModel) =
        viewModel.uiState.value.comments.filterIsInstance<CommentListItem.MoreComments>().single { it.isTopLevel }

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        StoryViewModel(storyId = story.id, repository = repository, savedStateHandle = savedState)
}
