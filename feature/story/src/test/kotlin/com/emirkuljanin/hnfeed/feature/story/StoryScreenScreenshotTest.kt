package com.emirkuljanin.hnfeed.feature.story

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import com.emirkuljanin.hnfeed.core.model.StoryType
import com.emirkuljanin.hnfeed.core.testing.data.testStories
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Golden images of the story screen, in `src/test/screenshots`. `./gradlew verifyRoborazziDebug` fails on any pixel
 * change; re-record with `./gradlew recordRoborazziDebug` only when the change is intended.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.MediumPhone)
class StoryScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun story_light() = capture("story_light") { StoryTestContent(loadedState()) }

    @Test
    fun story_dark() = capture("story_dark") { StoryTestContent(loadedState(), darkTheme = true) }

    /** Scrolled to the comments: nesting, OP badge, placeholder, reply toggles and "more" rows. */
    @Test
    fun comments() = capture("comments", scrollToComments = true) { StoryTestContent(loadedState()) }

    @Test
    fun comments_loadStates() = capture("comments_load_states", scrollToComments = true) {
        val state = loadedState()
        StoryTestContent(
            state.copy(
                comments = state.comments.map { item ->
                    when {
                        item !is CommentListItem.MoreComments -> item
                        item.isTopLevel -> item.copy(state = LoadState.FAILED)
                        else -> item.copy(state = LoadState.LOADING)
                    }
                },
            ),
        )
    }

    @Test
    fun textPost_refreshFailed() = capture("text_post_refresh_failed") {
        val askHn = testStories[1].copy(
            text = "<p>I keep coming back to <code>WorkManager</code>:</p>" +
                "<pre><code>  WorkManager.getInstance(context)\n    .enqueue(request)</code></pre><p>What is yours?</p>",
        )
        StoryTestContent(loadedState(refreshFailed = true).copy(story = askHn, comments = emptyList()))
    }

    @Test
    fun job() = capture("job") {
        StoryTestContent(StoryUiState(story = testStories.first { it.type == StoryType.JOB }, isRefreshing = false))
    }

    @Test
    fun loading() = capture("loading") { StoryTestContent(StoryUiState()) }

    @Test
    fun error() = capture("error") { StoryTestContent(StoryUiState(isRefreshing = false, refreshFailed = true)) }

    private fun capture(name: String, scrollToComments: Boolean = false, content: @Composable () -> Unit) {
        composeRule.setContent(content)
        // Item 0 is the story header, item 1 the "Comments" title.
        if (scrollToComments) composeRule.onNode(hasScrollAction()).performScrollToIndex(1)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
