package com.emirkuljanin.hnfeed.feature.story

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.WindowInsets
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import com.emirkuljanin.hnfeed.core.model.StoryType
import com.emirkuljanin.hnfeed.core.testing.data.testStories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Story author"))
    }

    @Test
    fun accessibility_aCommentIsOneItem_repliesSayTheirLevel_andTitlesAreHeadings() {
        composeRule.setContent { StoryTestContent(loadedState()) }

        composeRule.onNode(hasText(story.title) and isHeading()).assertExists()
        scrollTo("Thanks! The cloud images come later.")
        // Merged: one node holds the author, the OP badge's description and the text.
        composeRule.onNode(
            hasText("alice") and hasText("Thanks! The cloud images come later.") and
                hasContentDescription("Story author") and hasStateDescription("Reply, level 1"),
        ).assertExists()
        composeRule.onNode(hasText("Comments") and isHeading()).assertExists()
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
    fun goingOffline_whileReading_showsTheBannerOnScreen() {
        var state by mutableStateOf(loadedState())
        composeRule.setContent { StoryTestContent(state) }
        composeRule.onNodeWithText(story.title).assertIsDisplayed()

        state = state.copy(isOffline = true)

        composeRule.onNodeWithText("You're offline. Showing what's saved.").assertIsDisplayed()
    }

    @Test
    fun linksInAComment_stillOpen_thoughTheCommentIsOneAccessibilityItem() {
        val opened = mutableListOf<String>()
        composeRule.setContent { StoryTestContent(loadedState(), onOpenLink = { opened += it }) }
        scrollTo("Generating the art", substring = true)

        composeRule.onNode(hasText("Generating the art", substring = true)).performFirstLinkClick()

        assertEquals(listOf("https://example.com/notes"), opened)
    }

    @Test
    fun aPullShowsTheIndicator_evenWithoutComments() {
        var state by mutableStateOf(StoryUiState(story = story, isRefreshing = false))
        composeRule.setContent {
            // Like the ViewModel: a pull starts a refresh.
            StoryTestContent(state, onRefresh = { state = state.copy(isRefreshing = true) })
        }

        composeRule.onNode(hasScrollAction()).performTouchInput { swipeDown() }

        composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
    }

    @Test
    fun offline_showsTheOfflineNote_insteadOfTheRefreshError() {
        composeRule.setContent { StoryTestContent(loadedState(refreshFailed = true).copy(isOffline = true)) }

        composeRule.onNodeWithText("You're offline. Showing what's saved.").assertIsDisplayed()
        composeRule.onNodeWithText("Couldn't refresh. Showing saved comments.").assertDoesNotExist()
    }

    @Test
    fun firstLoad_withNoCommentsCached_showsOneSpinner_notAlsoThePullToRefreshOne() {
        composeRule.setContent { StoryTestContent(StoryUiState(story = story, isRefreshing = true)) }

        // Only the header is on screen: the pull-to-refresh indicator must not spin as well.
        composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertDoesNotExist()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Loading comments"))
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

    /**
     * In landscape the camera cutout is at the side. The story's text must start after it, as the feed's cards and
     * the top bar's back button do, not scroll through the cutout band.
     */
    @Test
    fun content_staysClearOfASideDisplayCutout() {
        val cutout = 48.dp
        composeRule.setContent {
            val cutoutPx = with(LocalDensity.current) { cutout.roundToPx() }
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(cutoutPx, 0, 0, 0))
                .build()
            DeviceConfigurationOverride(DeviceConfigurationOverride.WindowInsets(insets)) {
                StoryTestContent(loadedState(refreshFailed = true))
            }
        }

        val back = composeRule.onNodeWithContentDescription("Back").getUnclippedBoundsInRoot()
        assertTrue(back.left >= cutout, "Back button at ${back.left}")
        fun assertClear(node: SemanticsNodeInteraction) {
            val left = node.getUnclippedBoundsInRoot().left
            assertTrue(left >= cutout + 16.dp, "Text starts at $left, inside the $cutout cutout plus margin")
        }
        assertClear(composeRule.onNodeWithText("Couldn't refresh. Showing saved comments."))
        assertClear(composeRule.onNodeWithText(story.title))
        val commentsTitle = hasText("Comments") and isHeading()
        composeRule.onNode(hasScrollAction()).performScrollToNode(commentsTitle)
        assertClear(composeRule.onNode(commentsTitle))
    }

    /**
     * A phone in landscape (QA: the story opened on nothing but its header image): the thumbnail goes beside the
     * title, metadata and "Read article", so they are on screen from the start.
     */
    @Test
    @Config(qualifiers = "w914dp-h411dp-land-420dpi")
    fun inLandscape_theHeaderShowsTheTitleAndReadArticle_besideTheThumbnail() {
        composeRule.setContent { StoryTestContent(loadedState()) }

        composeRule.onNodeWithText(story.title, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Read article", useUnmergedTree = true).assertIsDisplayed()
    }

    /**
     * A compact phone in landscape with its camera cutout at the side: the header is under 600 dp wide (592 dp), but
     * the window is so short that a full-width image would fill it, so it goes wide too.
     */
    @Test
    @Config(qualifiers = "w640dp-h360dp-land")
    fun inACompactLandscapeWindow_theHeaderShowsTheTitle() {
        composeRule.setContent {
            val cutoutPx = with(LocalDensity.current) { 48.dp.roundToPx() }
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(cutoutPx, 0, 0, 0))
                .build()
            DeviceConfigurationOverride(DeviceConfigurationOverride.WindowInsets(insets)) {
                StoryTestContent(loadedState())
            }
        }

        composeRule.onNodeWithText(story.title, useUnmergedTree = true).assertIsDisplayed()
    }

    private fun scrollTo(text: String, substring: Boolean = false) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = substring))
    }
}
