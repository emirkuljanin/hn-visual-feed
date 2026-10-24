package com.emirkuljanin.hnfeed.feature.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emirkuljanin.hnfeed.core.model.Story
import com.emirkuljanin.hnfeed.core.model.StoryType
import com.emirkuljanin.hnfeed.core.ui.InlineError
import com.emirkuljanin.hnfeed.core.ui.LoadingIndicator
import com.emirkuljanin.hnfeed.core.ui.MessageScreen
import com.emirkuljanin.hnfeed.core.ui.OfflineBanner
import com.emirkuljanin.hnfeed.core.ui.PreviewTheme
import com.emirkuljanin.hnfeed.core.ui.R as CoreUiR
import com.emirkuljanin.hnfeed.core.ui.StoryLabels
import com.emirkuljanin.hnfeed.core.ui.StoryThumbnail
import com.emirkuljanin.hnfeed.core.ui.StoryThumbnailLayout
import com.emirkuljanin.hnfeed.core.ui.storyByline
import com.emirkuljanin.hnfeed.core.ui.storyCounts
import com.emirkuljanin.hnfeed.core.ui.storyThumbnailKey

@Composable
fun StoryRoute(
    storyId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StoryViewModel = hiltViewModel<StoryViewModel, StoryViewModel.Factory>(
        creationCallback = { factory -> factory.create(storyId) },
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    StoryScreen(
        uiState = uiState,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onToggleReplies = viewModel::toggleReplies,
        onLoadMore = viewModel::loadMore,
        // openUri throws when nothing can handle the link (no browser installed).
        onOpenLink = { url -> runCatching { uriHandler.openUri(url) } },
        modifier = modifier,
    )
}

/**
 * A story with its comments. Before anything is cached there is a full-screen spinner or error; once the story is
 * there, a failed refresh only adds a banner above the cached content.
 */
// TopAppBar and PullToRefreshBox are still @ExperimentalMaterial3Api in the Material 3 version in the Compose BOM.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StoryScreen(
    uiState: StoryUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleReplies: (Long) -> Unit,
    onLoadMore: (Long) -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val story = uiState.story
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.feature_story_ic_arrow_back),
                            contentDescription = stringResource(R.string.feature_story_back),
                        )
                    }
                },
                actions = {
                    if (story != null) {
                        IconButton(onClick = { onOpenLink(story.discussionUrl) }) {
                            Icon(
                                painter = painterResource(R.drawable.feature_story_ic_open_in_new),
                                contentDescription = stringResource(R.string.feature_story_open_on_hn),
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)
        val layoutDirection = LocalLayoutDirection.current
        when {
            story != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
            ) {
                // Above the list, not in it: an item inserted above the header would land off-screen, and TalkBack
                // would never announce it.
                val bannerModifier = Modifier.padding(
                    start = padding.calculateStartPadding(layoutDirection) + 16.dp,
                    end = padding.calculateEndPadding(layoutDirection) + 16.dp,
                    top = 8.dp,
                    bottom = 8.dp,
                )
                when {
                    uiState.isOffline -> OfflineBanner(bannerModifier)

                    uiState.refreshFailed -> InlineError(
                        text = stringResource(R.string.feature_story_refresh_failed),
                        onRetry = onRefresh,
                        modifier = bannerModifier,
                    )
                }
                // A pull always shows the indicator. The automatic refresh on opening shows it only over comments:
                // without them the inline "Loading comments" spinner already says it (one spinner, not two).
                var pulled by remember { mutableStateOf(false) }
                LaunchedEffect(uiState.isRefreshing) { if (!uiState.isRefreshing) pulled = false }
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing && (pulled || uiState.comments.isNotEmpty()),
                    onRefresh = {
                        pulled = true
                        onRefresh()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    StoryContent(
                        story = story,
                        uiState = uiState,
                        onToggleReplies = onToggleReplies,
                        onLoadMore = onLoadMore,
                        onOpenLink = onOpenLink,
                        contentPadding = padding,
                    )
                }
            }

            uiState.isRefreshing -> LoadingIndicator(
                description = stringResource(R.string.feature_story_loading),
                modifier = contentModifier,
            )

            else -> {
                val offline = uiState.isOffline
                MessageScreen(
                    title = stringResource(
                        if (offline) CoreUiR.string.core_ui_offline_title else R.string.feature_story_error_title,
                    ),
                    message = stringResource(
                        if (offline) CoreUiR.string.core_ui_offline_message else R.string.feature_story_error_message,
                    ),
                    modifier = contentModifier,
                ) {
                    Button(onClick = onRefresh) { Text(stringResource(CoreUiR.string.core_ui_retry)) }
                }
            }
        }
    }
}

@Composable
private fun StoryContent(
    story: Story,
    uiState: StoryUiState,
    onToggleReplies: (Long) -> Unit,
    onLoadMore: (Long) -> Unit,
    onOpenLink: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val layoutDirection = LocalLayoutDirection.current
    // The Scaffold's side padding holds the display cutout and side navigation bar in landscape; the top is the bar's.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection),
            end = contentPadding.calculateEndPadding(layoutDirection),
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
    ) {
        item(key = "header", contentType = "header") {
            StoryHeader(story = story, onOpenLink = onOpenLink)
        }
        // Jobs have no comments on Hacker News.
        if (story.type != StoryType.JOB) {
            item(key = "comments-title", contentType = "comments-title") {
                Text(
                    text = stringResource(R.string.feature_story_comments_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
                        .semantics { heading() },
                )
            }
            commentItems(uiState, onToggleReplies, onLoadMore, onOpenLink)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.commentItems(
    uiState: StoryUiState,
    onToggleReplies: (Long) -> Unit,
    onLoadMore: (Long) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    when {
        uiState.comments.isNotEmpty() -> items(
            items = uiState.comments,
            key = { it.key },
            contentType = { it::class },
        ) { item ->
            when (item) {
                is CommentListItem.CommentRow -> CommentRow(item, onToggleReplies, onOpenLink)
                is CommentListItem.MoreComments -> MoreCommentsRow(item, onLoadMore)
            }
        }

        uiState.isRefreshing -> item(key = "comments-loading", contentType = "comments-loading") {
            LoadingIndicator(
                description = stringResource(R.string.feature_story_loading_comments),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            )
        }

        else -> item(key = "no-comments", contentType = "no-comments") {
            // After a failed refresh the banner at the top offers the retry.
            val message = when {
                uiState.refreshFailed -> R.string.feature_story_comments_unavailable
                else -> R.string.feature_story_no_comments
            }
            Text(
                text = stringResource(message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

/** In a wide layout (landscape, tablets) the thumbnail goes beside the text, so the story opens on its title. */
@Composable
private fun StoryHeader(story: Story, onOpenLink: (String) -> Unit) {
    Column {
        StoryThumbnailLayout(
            thumbnail = { StoryThumbnail(thumbnail = story.thumbnail, sharedElementKey = storyThumbnailKey(story.id)) },
            details = { StoryHeaderDetails(story, onOpenLink) },
        )
        HorizontalDivider()
    }
}

@Composable
private fun StoryHeaderDetails(story: Story, onOpenLink: (String) -> Unit) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StoryLabels(story)
        Text(
            text = story.title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = storyByline(story),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = storyCounts(story),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        story.text?.let { text ->
            HtmlText(html = text, onOpenLink = onOpenLink, style = MaterialTheme.typography.bodyLarge)
        }
        story.url?.let { url ->
            FilledTonalButton(onClick = { onOpenLink(url) }) {
                Icon(
                    painter = painterResource(R.drawable.feature_story_ic_open_in_new),
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(stringResource(R.string.feature_story_read_article))
            }
        }
    }
}

@Preview
@Composable
private fun StoryScreenPreview() {
    PreviewTheme {
        StoryScreen(
            uiState = previewUiState,
            onBack = {},
            onRefresh = {},
            onToggleReplies = {},
            onLoadMore = {},
            onOpenLink = {},
        )
    }
}

@Preview
@Composable
private fun StoryScreenErrorPreview() {
    PreviewTheme {
        StoryScreen(
            uiState = StoryUiState(isRefreshing = false, refreshFailed = true),
            onBack = {},
            onRefresh = {},
            onToggleReplies = {},
            onLoadMore = {},
            onOpenLink = {},
        )
    }
}
