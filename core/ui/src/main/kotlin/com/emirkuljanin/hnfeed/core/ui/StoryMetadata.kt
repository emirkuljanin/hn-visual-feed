package com.emirkuljanin.hnfeed.core.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emirkuljanin.hnfeed.core.designsystem.time.LocalClock
import com.emirkuljanin.hnfeed.core.model.Story
import com.emirkuljanin.hnfeed.core.model.StoryType
import java.net.URI
import java.time.Instant

/** Domain of the linked article and a badge for jobs and polls; nothing for a plain text post. */
@Composable
fun StoryLabels(story: Story, modifier: Modifier = Modifier) {
    val domain = remember(story.url) { story.url?.let(::displayDomain) }
    val typeLabel = when (story.type) {
        StoryType.STORY -> null
        StoryType.JOB -> stringResource(R.string.core_ui_type_job)
        StoryType.POLL -> stringResource(R.string.core_ui_type_poll)
    }
    if (domain == null && typeLabel == null) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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

/** "by pg · 3 hours ago". */
@Composable
fun storyByline(story: Story): String =
    stringResource(R.string.core_ui_byline, story.author, relativeAge(story.postedAt))

/** "120 points · 45 comments". */
@Composable
fun storyCounts(story: Story): String = stringResource(
    R.string.core_ui_counts,
    pluralStringResource(R.plurals.core_ui_points, story.score, story.score),
    pluralStringResource(R.plurals.core_ui_comments, story.commentCount, story.commentCount),
)

/**
 * "5 minutes ago", "2 days ago", localized by the platform, relative to [LocalClock]. Times slightly ahead of the
 * device clock (clock skew) read as "0 minutes ago" rather than "In 2 minutes".
 */
@Composable
fun relativeAge(time: Instant): String {
    val now = LocalClock.current.millis()
    val then = minOf(time.toEpochMilli(), now)
    return DateUtils.getRelativeTimeSpanString(then, now, DateUtils.MINUTE_IN_MILLIS).toString()
}

/** "https://www.github.com/x" -> "github.com"; `null` for URLs that do not parse. */
fun displayDomain(url: String): String? = runCatching { URI(url).host }
    .getOrNull()
    ?.removePrefix("www.")
    ?.takeIf { it.isNotBlank() }
