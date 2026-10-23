package com.emirkuljanin.hnfeed.feature.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.emirkuljanin.hnfeed.core.model.Story
import com.emirkuljanin.hnfeed.core.ui.PreviewTheme
import kotlinx.coroutines.flow.flowOf

/** [onStoryClick] gets the story's id: all the app needs to navigate to it. */
@Composable
fun FeedRoute(
    onStoryClick: (storyId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = hiltViewModel(),
) {
    FeedScreen(
        stories = viewModel.stories.collectAsLazyPagingItems(),
        onStoryClick = { story -> onStoryClick(story.id) },
        modifier = modifier,
    )
}

/**
 * The feed in one of four shapes, decided by what is on screen and Paging's load states:
 *
 * | Stories on screen | Load state                      | Shown                                         |
 * |-------------------|---------------------------------|-----------------------------------------------|
 * | yes               | any                             | list (+ refresh-failed banner, footer)         |
 * | no                | refresh or first append loading | spinner                                        |
 * | no                | refresh or first append failed  | error with retry                               |
 * | no                | done                            | empty, with a refresh button                  |
 *
 * Retry and refresh go straight to Paging.
 */
// TopAppBar and PullToRefreshBox are still @ExperimentalMaterial3Api in the Material 3 version in the Compose BOM.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedScreen(
    stories: LazyPagingItems<Story>,
    onStoryClick: (Story) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    ScrollToTopAfterRemoteRefresh(stories, listState)

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_feed_title)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val loadState = stories.loadState
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)
        when {
            stories.itemCount > 0 -> PullToRefreshBox(
                isRefreshing = loadState.mediator?.refresh is LoadState.Loading,
                onRefresh = stories::refresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
            ) {
                StoryList(
                    stories = stories,
                    onStoryClick = onStoryClick,
                    listState = listState,
                    contentPadding = padding,
                )
            }

            // A first page in which nothing was showable leaves the list empty while Paging appends the next one.
            loadState.refresh is LoadState.Loading || loadState.append is LoadState.Loading ->
                FeedLoading(contentModifier)

            loadState.refresh is LoadState.Error || loadState.append is LoadState.Error ->
                FeedError(onRetry = stories::retry, modifier = contentModifier)

            else -> FeedEmpty(onRefresh = stories::refresh, modifier = contentModifier)
        }
    }
}

/**
 * Once a remote refresh (launch or pull-to-refresh) has landed, show the new top of the feed. Items are keyed by
 * story id, so without this the list would keep following the story that was first on screen to its new rank.
 *
 * Waits for the combined refresh state rather than the mediator's: the mediator finishes as soon as it has
 * written to Room, but the combined state only settles once the new page from Room is on screen, so the scroll
 * cannot be undone by data arriving a frame later. A refresh that fails leaves the position alone.
 */
@Composable
private fun ScrollToTopAfterRemoteRefresh(stories: LazyPagingItems<Story>, listState: LazyListState) {
    LaunchedEffect(stories, listState) {
        var remoteRefreshRunning = false
        snapshotFlow { stories.loadState }.collect { loadState ->
            when {
                loadState.mediator?.refresh is LoadState.Loading -> remoteRefreshRunning = true

                remoteRefreshRunning && loadState.refresh is LoadState.NotLoading -> {
                    remoteRefreshRunning = false
                    listState.requestScrollToItem(0)
                }

                loadState.refresh is LoadState.Error -> remoteRefreshRunning = false
            }
        }
    }
}

@Composable
private fun StoryList(
    stories: LazyPagingItems<Story>,
    onStoryClick: (Story) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    val layoutDirection = LocalLayoutDirection.current
    val loadState = stories.loadState
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection) + 16.dp,
            end = contentPadding.calculateEndPadding(layoutDirection) + 16.dp,
            top = 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (loadState.refresh is LoadState.Error) {
            item(key = "refresh-error", contentType = "refresh-error") {
                RefreshFailedBanner(onRetry = stories::retry)
            }
        }
        items(
            count = stories.itemCount,
            key = stories.itemKey { it.id },
            contentType = stories.itemContentType { "story" },
        ) { index ->
            stories[index]?.let { story -> StoryCard(story = story, onClick = { onStoryClick(story) }) }
        }
        // Until a launch refresh succeeds Paging holds appends back (they report NotLoading, not the refresh), so the
        // footer also reflects the refresh: otherwise the list would just end, with the retry banner far above.
        val moreToLoad = !loadState.append.endOfPaginationReached
        when {
            loadState.append is LoadState.Loading || (moreToLoad && loadState.mediator?.refresh is LoadState.Loading) ->
                item(key = "append-loading", contentType = "append-loading") { AppendLoading() }

            loadState.append is LoadState.Error || (moreToLoad && loadState.refresh is LoadState.Error) ->
                item(key = "append-error", contentType = "append-error") { AppendFailed(onRetry = stories::retry) }
        }
    }
}

@Preview
@Composable
private fun FeedScreenPreview() {
    PreviewTheme {
        FeedScreen(stories = flowOf(PagingData.from(previewStories)).collectAsLazyPagingItems(), onStoryClick = {})
    }
}
