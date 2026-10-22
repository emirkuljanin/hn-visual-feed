package com.emirkuljanin.hnfeed.feature.feed

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.emirkuljanin.hnfeed.core.designsystem.time.LocalClock
import com.emirkuljanin.hnfeed.core.model.Story
import com.emirkuljanin.hnfeed.core.model.StoryType
import java.net.URI
import java.time.Instant

@Composable
internal fun StoryCard(story: Story, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val openLabel = stringResource(R.string.feature_feed_open_story)
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.semantics { onClick(label = openLabel, action = null) },
    ) {
        ThumbnailPlaceholder(
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
                text = stringResource(R.string.feature_feed_byline, story.author, relativeAge(story.postedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    R.string.feature_feed_counts,
                    pluralStringResource(R.plurals.feature_feed_points, story.score, story.score),
                    pluralStringResource(R.plurals.feature_feed_comments, story.commentCount, story.commentCount),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Domain of the linked article and a badge for jobs and polls; nothing for a plain text post. */
@Composable
private fun StoryLabels(story: Story) {
    val domain = remember(story.url) { story.url?.let(::displayDomain) }
    val typeLabel = when (story.type) {
        StoryType.STORY -> null
        StoryType.JOB -> stringResource(R.string.feature_feed_type_job)
        StoryType.POLL -> stringResource(R.string.feature_feed_type_poll)
    }
    if (domain == null && typeLabel == null) return

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (typeLabel != null) {
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small) {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        if (domain != null) {
            Text(
                text = domain,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Where the generated thumbnail will go; shown until it is ready. */
@Composable
private fun ThumbnailPlaceholder(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier.background(Brush.linearGradient(listOf(colors.surfaceVariant, colors.secondaryContainer))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.feature_feed_ic_image),
            contentDescription = null,
            tint = colors.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(40.dp),
        )
    }
}

/**
 * "5 minutes ago", "2 days ago", localized by the platform, relative to [LocalClock]. Post times slightly ahead of
 * the device clock (clock skew) read as "0 minutes ago" rather than "In 2 minutes".
 */
@Composable
private fun relativeAge(postedAt: Instant): String {
    val now = LocalClock.current.millis()
    val posted = minOf(postedAt.toEpochMilli(), now)
    return DateUtils.getRelativeTimeSpanString(posted, now, DateUtils.MINUTE_IN_MILLIS).toString()
}

/** "https://www.github.com/x" -> "github.com"; `null` for URLs that do not parse. */
internal fun displayDomain(url: String): String? = runCatching { URI(url).host }
    .getOrNull()
    ?.removePrefix("www.")
    ?.takeIf { it.isNotBlank() }

@Preview
@Composable
private fun StoryCardPreview() {
    FeedPreviewTheme {
        StoryCard(story = previewStories.first(), onClick = {})
    }
}
