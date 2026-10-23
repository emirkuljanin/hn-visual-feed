package com.emirkuljanin.hnfeed.feature.story

import androidx.compose.runtime.Composable
import com.emirkuljanin.hnfeed.core.testing.data.TEST_NOW
import com.emirkuljanin.hnfeed.core.testing.data.testStories
import com.emirkuljanin.hnfeed.core.testing.data.testStoryComments
import com.emirkuljanin.hnfeed.core.ui.PreviewTheme

/** [StoryScreen] in the deterministic [PreviewTheme]. */
@Composable
internal fun StoryTestContent(
    uiState: StoryUiState,
    darkTheme: Boolean = false,
    onBack: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onToggleReplies: (Long) -> Unit = {},
    onLoadMore: (Long) -> Unit = {},
    onOpenLink: (String) -> Unit = {},
) {
    PreviewTheme(darkTheme = darkTheme, now = TEST_NOW) {
        StoryScreen(uiState, onBack, onRefresh, onToggleReplies, onLoadMore, onOpenLink)
    }
}

/** The test discussion with 101 and 201 expanded, after a successful refresh. */
internal fun loadedState(expanded: Set<Long> = setOf(101, 201), refreshFailed: Boolean = false): StoryUiState {
    val story = testStories.first()
    return StoryUiState(
        story = story,
        comments = buildCommentList(
            storyId = story.id,
            storyAuthor = story.author,
            comments = testStoryComments,
            expanded = expanded,
            loading = emptySet(),
            failed = emptySet(),
            isRefreshing = false,
        ),
        isRefreshing = false,
        refreshFailed = refreshFailed,
    )
}
