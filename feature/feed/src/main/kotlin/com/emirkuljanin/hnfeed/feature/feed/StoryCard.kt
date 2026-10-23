package com.emirkuljanin.hnfeed.feature.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.emirkuljanin.hnfeed.core.model.Story
import com.emirkuljanin.hnfeed.core.ui.PreviewTheme
import com.emirkuljanin.hnfeed.core.ui.StoryLabels
import com.emirkuljanin.hnfeed.core.ui.StoryThumbnail
import com.emirkuljanin.hnfeed.core.ui.storyByline
import com.emirkuljanin.hnfeed.core.ui.storyCounts

@Composable
internal fun StoryCard(story: Story, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val openLabel = stringResource(R.string.feature_feed_open_story)
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.semantics { onClick(label = openLabel, action = null) },
    ) {
        StoryThumbnail(
            thumbnail = story.thumbnail,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        )
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StoryLabels(story)
            Text(
                text = story.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = storyByline(story),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = storyCounts(story),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview
@Composable
private fun StoryCardPreview() {
    PreviewTheme {
        StoryCard(story = previewStories.first(), onClick = {})
    }
}
