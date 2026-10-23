package com.emirkuljanin.hnfeed.feature.feed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.emirkuljanin.hnfeed.core.model.Story
import com.emirkuljanin.hnfeed.core.testing.data.testStories
import com.emirkuljanin.hnfeed.core.testing.data.testStory
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FeedScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun storyCard_showsTitleDomainBylineAndCounts() {
        composeRule.setContent { FeedTestContent(feed(stories = testStories.take(1))) }

        composeRule.onNodeWithText(testStories[0].title).assertIsDisplayed()
        composeRule.onNodeWithText("github.com").assertIsDisplayed()
        composeRule.onNodeWithText("by alice · 2 hours ago").assertIsDisplayed()
        composeRule.onNodeWithText("412 points · 128 comments").assertIsDisplayed()
    }

    @Test
    fun storyCard_usesSingularsAndMarksJobs() {
        composeRule.setContent { FeedTestContent(feed(stories = listOf(testStories[2]))) }

        composeRule.onNodeWithText("Job").assertIsDisplayed()
        composeRule.onNodeWithText("1 point · 0 comments").assertIsDisplayed()
    }

    @Test
    fun clickingACard_reportsThatStory() {
        var clicked: Story? = null
        composeRule.setContent { FeedTestContent(feed(stories = testStories), onStoryClick = { clicked = it }) }

        composeRule.onNodeWithText(testStories[0].title).performClick()

        assertEquals(testStories[0], clicked)
    }

    @Test
    fun firstLoad_withNothingCached_showsASpinner() {
        composeRule.setContent { FeedTestContent(feed(refresh = loading)) }

        composeRule.onNodeWithContentDescription("Loading stories").assertIsDisplayed()
    }

    @Test
    fun failedFirstLoad_withNothingCached_showsAnErrorWithRetry() {
        composeRule.setContent { FeedTestContent(feed(refresh = error)) }

        composeRule.onNodeWithText("Couldn't load stories").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test
    fun retry_afterAFailedFirstLoad_loadsTheStories() {
        val source = FlakyPagingSource(failures = 1)
        composeRule.setContent { FeedTestContent(Pager(PagingConfig(pageSize = 10)) { source }.flow) }
        composeRule.onNodeWithText("Couldn't load stories").assertIsDisplayed()

        composeRule.onNodeWithText("Retry").performClick()

        composeRule.onNodeWithText(testStories[0].title).assertIsDisplayed()
    }

    @Test
    fun aFailedFirstLoad_isRetriedByItself_whenTheConnectionComesBack() {
        val source = FlakyPagingSource(failures = 1)
        var isOffline by mutableStateOf(true)
        composeRule.setContent {
            FeedTestContent(Pager(PagingConfig(pageSize = 10)) { source }.flow, isOffline = isOffline)
        }
        composeRule.onNodeWithText("You're offline").assertIsDisplayed()

        isOffline = false

        composeRule.onNodeWithText(testStories[0].title).assertIsDisplayed()
    }

    @Test
    fun goingOffline_whileReading_showsTheBannerOnScreen_asALiveRegion() {
        var isOffline by mutableStateOf(false)
        composeRule.setContent { FeedTestContent(feed(stories = testStories), isOffline = isOffline) }
        composeRule.onNodeWithText(testStories[0].title).assertIsDisplayed()

        isOffline = true

        composeRule.onNodeWithText("You're offline. Showing what's saved.").assertIsDisplayed()
        composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assertIsDisplayed()
    }

    @Test
    fun offline_withCachedStories_saysSo_insteadOfReportingAFailedRefresh() {
        composeRule.setContent { FeedTestContent(feed(stories = testStories, refresh = error), isOffline = true) }

        composeRule.onNodeWithText("You're offline. Showing what's saved.").assertIsDisplayed()
        composeRule.onNodeWithText("Couldn't refresh. Showing saved stories.").assertDoesNotExist()
    }

    @Test
    fun refresh_fromTheEmptyState_loadsTheStories() {
        var refreshes = 0
        val pager = Pager(PagingConfig(pageSize = 10)) {
            StaticPagingSource(if (refreshes++ == 0) emptyList() else testStories)
        }
        composeRule.setContent { FeedTestContent(pager.flow) }
        composeRule.onNodeWithText("No stories right now").assertIsDisplayed()

        composeRule.onNodeWithText("Refresh").performClick()

        composeRule.onNodeWithText(testStories[0].title).assertIsDisplayed()
    }

    @Test
    fun failedRefresh_withCachedStories_keepsThemAndShowsABanner() {
        composeRule.setContent { FeedTestContent(feed(stories = testStories, refresh = error)) }

        composeRule.onNodeWithText("Couldn't refresh. Showing saved stories.").assertIsDisplayed()
        composeRule.onNodeWithText(testStories[0].title).assertIsDisplayed()
    }

    @Test
    fun failedLaunchRefresh_withMorePagesHeldBack_endsTheListWithARetryFooter() {
        // Paging holds appends back until a launch refresh succeeds; they report NotLoading, not an error.
        composeRule.setContent {
            FeedTestContent(feed(stories = testStories.take(1), refresh = error, append = notLoading))
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Couldn't load more stories."))
        composeRule.onNodeWithText("Couldn't load more stories.").assertIsDisplayed()
    }

    @Test
    fun runningLaunchRefresh_withMorePagesHeldBack_endsTheListWithASpinner() {
        composeRule.setContent {
            FeedTestContent(feed(stories = testStories.take(1), append = notLoading, mediatorRefresh = loading))
        }

        composeRule.onNodeWithContentDescription("Loading stories").assertIsDisplayed()
    }

    @Test
    fun remoteRefresh_showsThePullToRefreshIndicator() {
        composeRule.setContent { FeedTestContent(feed(stories = testStories.take(1), mediatorRefresh = loading)) }

        composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
    }

    @Test
    fun noRefresh_showsNoProgressIndicator() {
        composeRule.setContent { FeedTestContent(feed(stories = testStories.take(1))) }

        composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertDoesNotExist()
    }

    @Test
    fun emptyFeed_offersARefresh() {
        composeRule.setContent { FeedTestContent(feed()) }

        composeRule.onNodeWithText("No stories right now").assertIsDisplayed()
        composeRule.onNodeWithText("Refresh").assertIsDisplayed()
    }

    @Test
    fun emptyFirstPage_whileTheNextPageLoads_showsASpinnerNotTheEmptyState() {
        composeRule.setContent { FeedTestContent(feed(append = loading)) }

        composeRule.onNodeWithContentDescription("Loading stories").assertIsDisplayed()
    }

    @Test
    fun failedAppend_showsARetryFooter() {
        composeRule.setContent { FeedTestContent(feed(stories = testStories.take(1), append = error)) }

        composeRule.onNodeWithText("Couldn't load more stories.").assertIsDisplayed()
    }

    @Test
    fun finishedRemoteRefresh_scrollsToTheNewTopStory_evenWhenScrolledDown() {
        val before = (1L..10L).map { testStory(it, title = "Story $it") }
        val feed = MutableStateFlow(pagingData(before))
        composeRule.setContent { FeedTestContent(feed) }
        composeRule.onNode(hasScrollAction()).performScrollToIndex(6)

        feed.value = pagingData(before, mediatorRefresh = loading)
        composeRule.waitForIdle()
        feed.value = pagingData(before.reversed())

        composeRule.onNodeWithText("Story 10").assertIsDisplayed()
    }

    /** Fails the first [failures] loads, then returns the test stories; retry re-runs the load on the same source. */
    private class FlakyPagingSource(private var failures: Int) : PagingSource<Int, Story>() {
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Story> =
            if (failures-- > 0) LoadResult.Error(IOException("offline")) else LoadResult.Page(testStories, null, null)

        override fun getRefreshKey(state: PagingState<Int, Story>): Int? = null
    }

    private class StaticPagingSource(private val stories: List<Story>) : PagingSource<Int, Story>() {
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Story> =
            LoadResult.Page(stories, prevKey = null, nextKey = null)

        override fun getRefreshKey(state: PagingState<Int, Story>): Int? = null
    }
}
