package com.emirkuljanin.hnfeed.core.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.compositionLocalOf

/**
 * The app's shared-element scope (a `SharedTransitionLayout` around the navigation). `null` where there is none,
 * as in previews and tests: shared elements then simply do not animate.
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * The current destination's enter/exit animation, which shared elements follow. Provided by the app for each
 * navigation entry, so features need no dependency on the navigation library.
 */
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** The key that ties a story's thumbnail in the feed to the same thumbnail on the story screen. */
fun storyThumbnailKey(storyId: Long): Any = "story-thumbnail-$storyId"
