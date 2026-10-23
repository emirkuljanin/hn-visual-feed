package com.emirkuljanin.hnfeed.feature.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.emirkuljanin.hnfeed.core.model.Story
import com.emirkuljanin.hnfeed.core.ui.OfflineBanner
import com.emirkuljanin.hnfeed.core.ui.PreviewTheme
import kotlinx.coroutines.flow.flowOf

/** [onStoryClick] gets the story's id: all the app needs to navigate to it. */
@Composable
fun FeedRoute(
    onStoryClick: (storyId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FeedScreen(
        stories = viewModel.stories.collectAsLazyPagingItems(),
        isOffline = uiState.isOffline,
        onStoryClick = { story -> onStoryClick(story.id) },
        modifier = modifier,
    )
}

/**
 * The feed in one of four shapes, decided by what is on screen and Paging's load states:
 *
 * | Stories on screen | Load state                      | Shown                                         |
 * |-------------------|---------------------------------|-----------------------------------------------|
 * | yes               | any                             | banner (offline or refresh failed) + list + footer |
 * | no                | refresh or first append loading | spinner                                        |
 * | no                | refresh or first append failed  | error with retry                               |
 * | no                | done                            | empty, with a refresh button                  |
 *
 * Retry and refresh go straight to Paging. While [isOffline] the banner says so instead of reporting a failed
 * refresh, and failed loads are retried by themselves as soon as the connection is back.
 */
// TopAppBar and PullToRefreshBox are still @ExperimentalMaterial3Api in the Material 3 version in the Compose BOM.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedScreen(
    stories: LazyPagingItems<Story>,
    isOffline: Boolean,
    onStoryClick: (Story) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    ScrollToTopAfterRemoteRefresh(stories, listState)
    RetryWhenBackOnline(stories, isOffline)

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_feed_title), Modifier.semantics { heading() }) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val loadState = stories.loadState
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)
        val layoutDirection = LocalLayoutDirection.current
        when {
            stories.itemCount > 0 -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
            ) {
                // Above the list, not in it: an item inserted at the top of a keyed list lands above the first
                // visible item, off-screen, and TalkBack would never announce it.
                val bannerModifier = Modifier.padding(
                    start = padding.calculateStartPadding(layoutDirection) + 16.dp,
                    end = padding.calculateEndPadding(layoutDirection) + 16.dp,
                    top = 8.dp,
                )
                when {
                    isOffline -> OfflineBanner(bannerModifier)
                    loadState.refresh is LoadState.Error -> RefreshFailedBanner(stories::retry, bannerModifier)
                }
                PullToRefreshBox(
                    isRefreshing = loadState.mediator?.refresh is LoadState.Loading,
                    onRefresh = stories::refresh,
                    modifier = Modifier.weight(1f),
                ) {
                    StoryList(
                        stories = stories,
                        onStoryClick = onStoryClick,
                        listState = listState,
                        contentPadding = padding,
                    )
                }
            }

            // A first page in which nothing was showable leaves the list empty while Paging appends the next one.
            loadState.refresh is LoadState.Loading || loadState.append is LoadState.Loading ->
                FeedLoading(contentModifier)

            loadState.refresh is LoadState.Error || loadState.append is LoadState.Error ->
                FeedError(isOffline = isOffline, onRetry = stories::retry, modifier = contentModifier)

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

/**
 * Retries failed loads whenever the screen sees a connection: when it comes back, and also when the feed returns
 * to the screen after the connection came back elsewhere. `retry()` only re-runs loads that failed, so calling it
 * when nothing failed does nothing.
 */
@Composable
private fun RetryWhenBackOnline(stories: LazyPagingItems<Story>, isOffline: Boolean) {
    LaunchedEffect(isOffline) {
        if (!isOffline) stories.retry()
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
        FeedScreen(
            stories = flowOf(PagingData.from(previewStories)).collectAsLazyPagingItems(),
            isOffline = false,
            onStoryClick = {},
        )
    }
}
