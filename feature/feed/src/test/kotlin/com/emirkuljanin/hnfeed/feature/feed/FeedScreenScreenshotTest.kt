package com.emirkuljanin.hnfeed.feature.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.emirkuljanin.hnfeed.core.testing.data.TEST_NOW
import com.emirkuljanin.hnfeed.core.testing.data.testStories
import com.emirkuljanin.hnfeed.core.ui.PreviewTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Golden images of every feed state, in `src/test/screenshots`. `./gradlew verifyRoborazziDebug` fails on any pixel
 * change; re-record with `./gradlew recordRoborazziDebug` only when the change is intended.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.MediumPhone)
class FeedScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun feed_light() = capture("feed_light") { FeedTestContent(feed(stories = testStories)) }

    @Test
    fun feed_dark() = capture("feed_dark") { FeedTestContent(feed(stories = testStories), darkTheme = true) }

    @Test
    fun feed_jobAndPoll() = capture("feed_job_and_poll") { FeedTestContent(feed(stories = testStories.drop(2))) }

    @Test
    fun feed_refreshFailed() = capture("feed_refresh_failed") {
        FeedTestContent(feed(stories = testStories, refresh = error))
    }

    /** The largest font size Android offers (200%): nothing may clip or overlap. */
    @Test
    fun feed_largestFont() {
        RuntimeEnvironment.setFontScale(2f)
        capture("feed_largest_font") { FeedTestContent(feed(stories = testStories)) }
    }

    @Test
    fun feed_offline() = capture("feed_offline") {
        FeedTestContent(feed(stories = testStories, refresh = error), isOffline = true)
    }

    @Test
    fun feed_appendFailed() = capture("feed_append_failed") {
        FeedTestContent(feed(stories = testStories.takeLast(1), append = error))
    }

    /** A phone in landscape: thumbnails beside the text, so more than one story fits on the screen. */
    @Test
    @Config(qualifiers = "+land")
    fun feed_landscape() = capture("feed_landscape") { FeedTestContent(feed(stories = testStories)) }

    /** A wide card above a placeholder: the placeholder keeps the card's shape and height. */
    @Test
    @Config(qualifiers = "+land")
    fun cards_landscape() = capture("cards_landscape") {
        PreviewTheme(now = TEST_NOW) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StoryCard(story = testStories[0], onClick = {})
                StoryCardPlaceholder()
            }
        }
    }

    @Test
    fun firstLoad() = capture("first_load") { FeedTestContent(feed(refresh = loading)) }

    @Test
    fun firstLoadFailed() = capture("first_load_failed") { FeedTestContent(feed(refresh = error)) }

    @Test
    fun empty() = capture("empty") { FeedTestContent(feed()) }

    private fun capture(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent(content)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
