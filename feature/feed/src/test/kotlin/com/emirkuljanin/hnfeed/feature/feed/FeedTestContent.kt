package com.emirkuljanin.hnfeed.feature.feed

import androidx.compose.runtime.Composable
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.emirkuljanin.hnfeed.core.model.Story
import com.emirkuljanin.hnfeed.core.testing.data.TEST_NOW
import com.emirkuljanin.hnfeed.core.ui.PreviewTheme
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** [FeedScreen] in the deterministic [PreviewTheme], so tests and screenshots look the same everywhere. */
@Composable
internal fun FeedTestContent(
    feed: Flow<PagingData<Story>>,
    darkTheme: Boolean = false,
    isOffline: Boolean = false,
    onStoryClick: (Story) -> Unit = {},
) {
    PreviewTheme(darkTheme = darkTheme, now = TEST_NOW) {
        FeedScreen(stories = feed.collectAsLazyPagingItems(), isOffline = isOffline, onStoryClick = onStoryClick)
    }
}

internal val notLoading: LoadState = LoadState.NotLoading(endOfPaginationReached = false)
internal val endReached: LoadState = LoadState.NotLoading(endOfPaginationReached = true)
internal val loading: LoadState = LoadState.Loading
internal val error: LoadState = LoadState.Error(IOException("offline"))

/**
 * Paging data in the states the screen reacts to, as Paging reports them with a RemoteMediator: [refresh] is the
 * combined state the screen picks its shape from, [mediatorRefresh] drives the pull-to-refresh indicator.
 */
internal fun pagingData(
    stories: List<Story> = emptyList(),
    refresh: LoadState = notLoading,
    append: LoadState = endReached,
    mediatorRefresh: LoadState = refresh,
): PagingData<Story> = PagingData.from(
    data = stories,
    sourceLoadStates = LoadStates(refresh = refresh, prepend = endReached, append = append),
    mediatorLoadStates = LoadStates(refresh = mediatorRefresh, prepend = endReached, append = append),
)

internal fun feed(
    stories: List<Story> = emptyList(),
    refresh: LoadState = notLoading,
    append: LoadState = endReached,
    mediatorRefresh: LoadState = refresh,
): Flow<PagingData<Story>> = flowOf(pagingData(stories, refresh, append, mediatorRefresh))
