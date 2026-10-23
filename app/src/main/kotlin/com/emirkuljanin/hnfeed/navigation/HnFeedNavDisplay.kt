package com.emirkuljanin.hnfeed.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import com.emirkuljanin.hnfeed.core.ui.LocalNavAnimatedVisibilityScope
import com.emirkuljanin.hnfeed.core.ui.LocalSharedTransitionScope
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
 *
 * The whole display sits in a `SharedTransitionLayout`, and each entry gets its enter/exit animation as
 * [LocalNavAnimatedVisibilityScope], so a story's thumbnail flies from its card into the story header and back,
 * also during predictive back. Screens change with Material's "fade through": the old one fades out quickly
 * before the new one fades in, so their text never overlaps while the thumbnail carries the eye across.
 */
@Composable
fun HnFeedNavDisplay(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(FeedKey)
    SharedTransitionLayout(modifier) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            HnFeedEntries(backStack)
        }
    }
}

@Composable
private fun HnFeedEntries(backStack: NavBackStack<NavKey>) {
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        transitionSpec = { fadeThrough() },
        popTransitionSpec = { fadeThrough() },
        predictivePopTransitionSpec = { fadeThrough() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            // Both callbacks check that their entry is still on top: a quick double tap would otherwise open the same
            // story twice, or pop the feed too and leave NavDisplay with an empty back stack (which throws).
            entry<FeedKey> {
                WithEntryAnimation {
                    FeedRoute(
                        onStoryClick = { storyId ->
                            if (backStack.lastOrNull() == FeedKey) backStack.add(StoryKey(storyId))
                        },
                    )
                }
            }
            entry<StoryKey> { key ->
                WithEntryAnimation {
                    StoryRoute(
                        storyId = key.storyId,
                        onBack = { if (backStack.lastOrNull() == key) backStack.removeLastOrNull() },
                    )
                }
            }
        },
    )
}

private fun fadeThrough(): ContentTransform =
    fadeIn(tween(FADE_IN_MILLIS, delayMillis = FADE_OUT_MILLIS)) togetherWith fadeOut(tween(FADE_OUT_MILLIS))

private const val FADE_OUT_MILLIS = 90
private const val FADE_IN_MILLIS = 210

/** Hands the entry's animation to shared elements in features, which do not depend on Navigation 3. */
@Composable
private fun WithEntryAnimation(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalNavAnimatedVisibilityScope provides LocalNavAnimatedContentScope.current,
        content = content,
    )
}
