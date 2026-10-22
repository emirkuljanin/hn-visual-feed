package com.emirkuljanin.hnfeed.feature.feed

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.emirkuljanin.hnfeed.core.designsystem.theme.HnFeedTheme
import com.emirkuljanin.hnfeed.core.model.Story
import com.emirkuljanin.hnfeed.core.model.StoryType
import java.time.Instant
import kotlinx.coroutines.flow.flowOf

@Composable
fun FeedRoute(modifier: Modifier = Modifier, viewModel: FeedViewModel = hiltViewModel()) {
    FeedScreen(stories = viewModel.stories.collectAsLazyPagingItems(), modifier = modifier)
}

// A plain list for now: it proves the data pipeline end to end. Cards, images and load/error states replace it.
// TopAppBar is still @ExperimentalMaterial3Api in the Material 3 version shipped with the current Compose BOM.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedScreen(stories: LazyPagingItems<Story>, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.feature_feed_title)) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(padding),
            contentPadding = padding,
        ) {
            items(count = stories.itemCount, key = stories.itemKey { it.id }) { index ->
                stories[index]?.let { StoryRow(it) }
            }
        }
    }
}

@Composable
private fun StoryRow(story: Story, modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier,
        headlineContent = { Text(story.title) },
        supportingContent = {
            val points = pluralStringResource(R.plurals.feature_feed_points, story.score, story.score)
            val comments = pluralStringResource(R.plurals.feature_feed_comments, story.commentCount, story.commentCount)
            Text(stringResource(R.string.feature_feed_story_meta, points, story.author, comments))
        },
    )
}

@Preview
@Composable
private fun FeedScreenPreview() {
    val stories = List(5) { index ->
        Story(
            id = index.toLong(),
            title = "Show HN: Story number $index",
            author = "author$index",
            score = 100 - index * 10,
            postedAt = Instant.parse("2026-10-23T10:00:00Z"),
            url = null,
            commentCount = index * 3,
            type = StoryType.STORY,
        )
    }
    HnFeedTheme(dynamicColor = false) {
        FeedScreen(stories = flowOf(PagingData.from(stories)).collectAsLazyPagingItems())
    }
}
