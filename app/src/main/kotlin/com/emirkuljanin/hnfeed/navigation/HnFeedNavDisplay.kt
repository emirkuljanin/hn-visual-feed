package com.emirkuljanin.hnfeed.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.emirkuljanin.hnfeed.feature.feed.FeedRoute
import com.emirkuljanin.hnfeed.feature.story.StoryRoute
import kotlinx.serialization.Serializable

/** The feed: the start destination. */
@Serializable
data object FeedKey : NavKey

/** One story with its comments. */
@Serializable
data class StoryKey(val storyId: Long) : NavKey

/**
 * The app's navigation (Navigation 3). The back stack is a plain list of keys that survives configuration changes
 * and process death (keys are serializable). Features know nothing about navigation: they report clicks through
 * callbacks, and only this file maps them to destinations.
 *
 * Entry decorators give every entry its own saved state (the feed keeps its scroll position under a story) and its
 * own ViewModelStore (each story key gets its own ViewModel, cleared when the entry is popped).
 */
@Composable
fun HnFeedNavDisplay(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(FeedKey)
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        modifier = modifier,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            // Both callbacks check that their entry is still on top: a quick double tap would otherwise open the same
            // story twice, or pop the feed too and leave NavDisplay with an empty back stack (which throws).
            entry<FeedKey> {
                FeedRoute(
                    onStoryClick = { storyId ->
                        if (backStack.lastOrNull() == FeedKey) backStack.add(StoryKey(storyId))
                    },
                )
            }
            entry<StoryKey> { key ->
                StoryRoute(
                    storyId = key.storyId,
                    onBack = { if (backStack.lastOrNull() == key) backStack.removeLastOrNull() },
                )
            }
        },
    )
}
