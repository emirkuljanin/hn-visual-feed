package com.emirkuljanin.hnfeed.feature.story

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.emirkuljanin.hnfeed.core.model.StoryType
import com.emirkuljanin.hnfeed.core.testing.data.testStories
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StoryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val story = testStories.first()

    @Test
    fun showsTheStory_andItsComments_withHtmlRenderedAsText() {
        composeRule.setContent { StoryTestContent(loadedState()) }

        composeRule.onNodeWithText(story.title).assertIsDisplayed()
        composeRule.onNodeWithText("by alice · 2 hours ago").assertIsDisplayed()
        scrollTo("Generating the art", substring = true)
        composeRule.onNodeWithText("See my notes on embeddings.", substring = true).assertIsDisplayed()
        scrollTo("OP")
        composeRule.onNodeWithContentDescription("Story author").assertExists()
    }

    @Test
    fun placeholders_standInForDeletedComments() {
        composeRule.setContent { StoryTestContent(loadedState()) }

        scrollTo("[deleted]")
        composeRule.onNodeWithText("[flagged]").assertDoesNotExist()
    }

    @Test
    fun repliesToggle_andMoreButtons_reportTheirComment() {
        val toggled = mutableListOf<Long>()
        val loadMore = mutableListOf<Long>()
        composeRule.setContent {
            StoryTestContent(loadedState(), onToggleReplies = { toggled += it }, onLoadMore = { loadMore += it })
        }

        scrollTo("Show 3 replies")
        composeRule.onNodeWithText("Show 3 replies").performClick()
        // 101 and 201 are both expanded; the first toggle is 101's.
        scrollTo("Hide replies")
        composeRule.onAllNodesWithText("Hide replies")[0].performClick()
        scrollTo("Show 1 more reply")
        composeRule.onNodeWithText("Show 1 more reply").performClick()

        composeRule.waitForIdle()
        assertEquals(listOf(102L, 101L), toggled)
        // 201's reply, plus the top level, which loads by itself once its row is on screen: exactly once.
        assertEquals(1, loadMore.count { it == 201L })
        assertEquals(1, loadMore.count { it == story.id })
    }

    @Test
    fun readArticle_andTheTopBar_openLinksAndGoBack() {
        val opened = mutableListOf<String>()
        var back = 0
        composeRule.setContent { StoryTestContent(loadedState(), onOpenLink = { opened += it }, onBack = { back++ }) }

        composeRule.onNodeWithText("Read article").performClick()
        composeRule.onNodeWithContentDescription("Open on Hacker News").performClick()
        composeRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf(story.url!!, story.discussionUrl), opened)
        assertEquals(1, back)
    }

    @Test
    fun aFailedRefresh_withAStory_showsABanner_withRetry() {
        var retries = 0
        composeRule.setContent { StoryTestContent(loadedState(refreshFailed = true), onRefresh = { retries++ }) }

        composeRule.onNodeWithText("Couldn't refresh. Showing saved comments.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()

        assertEquals(1, retries)
    }

    @Test
    fun aFailedRefresh_withNothingCached_saysTheCommentsCouldNotBeLoaded() {
        composeRule.setContent { StoryTestContent(loadedState(refreshFailed = true).copy(comments = emptyList())) }

        scrollTo("Comments couldn't be loaded.")
        composeRule.onNodeWithText("No comments yet").assertDoesNotExist()
    }

    @Test
    fun withoutAStory_showsLoadingThenError() {
        var state by mutableStateOf(StoryUiState())
        var retries = 0
        composeRule.setContent { StoryTestContent(state, onRefresh = { retries++ }) }
        composeRule.onNodeWithContentDescription("Loading story").assertIsDisplayed()

        state = StoryUiState(isRefreshing = false, refreshFailed = true)
        composeRule.onNodeWithText("Couldn't load this story").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()

        assertEquals(1, retries)
    }

    @Test
    fun aStoryWithoutComments_saysSo_butAJobHasNoCommentSection() {
        var state by mutableStateOf(
            StoryUiState(story = story.copy(commentCount = 0), isRefreshing = false),
        )
        composeRule.setContent { StoryTestContent(state) }
        scrollTo("No comments yet")

        state = StoryUiState(story = testStories.first { it.type == StoryType.JOB }, isRefreshing = false)
        composeRule.onNodeWithText("Comments").assertDoesNotExist()
    }

    private fun scrollTo(text: String, substring: Boolean = false) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = substring))
    }
}
