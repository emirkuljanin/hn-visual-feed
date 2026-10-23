package com.emirkuljanin.hnfeed

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.espresso.Espresso.pressBack
import com.emirkuljanin.hnfeed.core.testing.data.testStories
import com.emirkuljanin.hnfeed.core.testing.repository.FakeStoryDetailsRepository
import com.emirkuljanin.hnfeed.core.testing.util.TestNetworkMonitor
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The whole app on Robolectric (no emulator): `MainActivity`, Hilt, navigation, ViewModels and screens, with only
 * the data layer replaced by fakes ([TestDataModule]). Covers what screen tests cannot: navigating between screens,
 * back handling, state across recreation and the shared thumbnail transition.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = RobolectricDeviceQualifiers.MediumPhone)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HnFeedAppTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var storyDetails: FakeStoryDetailsRepository

    @Inject
    lateinit var networkMonitor: TestNetworkMonitor

    private val story = testStories.first()

    @BeforeTest
    fun inject() {
        hiltRule.inject()
    }

    @Test
    fun openingAStory_showsItsDiscussion_andBackReturnsToTheFeed() {
        composeRule.onNodeWithText("Hacker News").assertIsDisplayed()

        composeRule.onNodeWithText(story.title).performClick()
        composeRule.onNodeWithText("Read article").assertIsDisplayed()
        scrollTo("Generating the art on device first is a nice touch.", substring = true)
        assertEquals(listOf(story.id), storyDetails.refreshes, "Opening a story refreshes it")

        pressBack()
        composeRule.onNodeWithText("Hacker News").assertIsDisplayed()
        composeRule.onNodeWithText("Read article").assertDoesNotExist()
    }

    @Test
    fun theTopBarBackButton_returnsToTheFeed() {
        composeRule.onNodeWithText(story.title).performClick()

        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNodeWithText("Hacker News").assertIsDisplayed()
    }

    @Test
    fun expandingReplies_loadsThemThroughTheRepository() {
        composeRule.onNodeWithText(story.title).performClick()

        scrollTo("Show 3 replies")
        composeRule.onNodeWithText("Show 3 replies").performClick()

        composeRule.waitForIdle()
        assertEquals(true, 102L in storyDetails.loadMoreRequests)
    }

    @Test
    fun theOpenStory_andItsExpandedThreads_surviveRecreation() {
        composeRule.onNodeWithText(story.title).performClick()
        scrollTo("Show 2 replies")
        composeRule.onNodeWithText("Show 2 replies").performClick()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithText("Read article").assertExists()
        scrollTo("Thanks! The cloud images come later.")
    }

    @Test
    fun goingOffline_isShownOnBothScreens() {
        // Offline only once the feed is on screen, as when the connection drops while reading.
        composeRule.onNodeWithText(story.title).assertIsDisplayed()
        networkMonitor.setOnline(false)

        composeRule.onNodeWithText("You're offline. Showing what's saved.").assertIsDisplayed()
        composeRule.onNodeWithText(story.title).performClick()
        composeRule.onNodeWithText("You're offline. Showing what's saved.").assertIsDisplayed()
    }

    /** A frame halfway through opening a story: the card's thumbnail on its way into the story header. */
    @Test
    fun openingAStory_theThumbnailFliesIntoTheHeader() {
        composeRule.onNodeWithText(story.title).assertIsDisplayed()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText(story.title).performClick()
        composeRule.mainClock.advanceTimeBy(TRANSITION_MIDPOINT_MILLIS)

        composeRule.onRoot().captureRoboImage("src/test/screenshots/thumbnail_transition_midway.png")

        composeRule.mainClock.autoAdvance = true
        composeRule.onNodeWithText("Read article").assertIsDisplayed()
    }

    private fun scrollTo(text: String, substring: Boolean = false) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = substring))
    }

    private companion object {
        const val TRANSITION_MIDPOINT_MILLIS = 150L
    }
}
