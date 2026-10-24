package com.emirkuljanin.hnfeed.feature.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.height
import com.emirkuljanin.hnfeed.core.testing.data.TEST_NOW
import com.emirkuljanin.hnfeed.core.testing.data.testStories
import com.emirkuljanin.hnfeed.core.ui.PreviewTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The card's layout (`StoryThumbnailLayout`) in parents that ask for intrinsic sizes. */
@RunWith(RobolectricTestRunner::class)
class StoryCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun intrinsicHeight_isTheMeasuredHeight_whenNarrow() = assertIntrinsicHeightIsTheMeasuredHeight()

    @Test
    @Config(qualifiers = "w914dp-h411dp-land")
    fun intrinsicHeight_isTheMeasuredHeight_whenWide() = assertIntrinsicHeightIsTheMeasuredHeight()

    @Test
    fun intrinsicWidth_isSupported() {
        composeRule.setContent {
            PreviewTheme(now = TEST_NOW) {
                StoryCard(
                    story = testStories[0],
                    onClick = {},
                    modifier = Modifier.width(IntrinsicSize.Max).testTag("card"),
                )
            }
        }

        composeRule.onNodeWithTag("card").assertIsDisplayed()
    }

    private fun assertIntrinsicHeightIsTheMeasuredHeight() {
        composeRule.setContent {
            PreviewTheme(now = TEST_NOW) {
                // Scrollable, so the second card is not squeezed into what the first leaves of the window.
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    StoryCard(story = testStories[0], onClick = {}, modifier = Modifier.testTag("measured"))
                    StoryCard(
                        story = testStories[0],
                        onClick = {},
                        modifier = Modifier.height(IntrinsicSize.Min).testTag("intrinsic"),
                    )
                }
            }
        }

        val measured = composeRule.onNodeWithTag("measured").getUnclippedBoundsInRoot().height
        val intrinsic = composeRule.onNodeWithTag("intrinsic").getUnclippedBoundsInRoot().height
        assertEquals(measured.value, intrinsic.value, absoluteTolerance = 0.5f)
    }
}
