package com.emirkuljanin.hnfeed.feature.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.emirkuljanin.hnfeed.core.designsystem.theme.HnFeedTheme
import com.emirkuljanin.hnfeed.core.designsystem.time.LocalClock
import com.emirkuljanin.hnfeed.core.model.Story
import com.emirkuljanin.hnfeed.core.model.StoryType
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private val PREVIEW_NOW: Instant = Instant.parse("2026-10-23T12:00:00Z")

/** Theme without dynamic color and a fixed clock, so previews look the same on every machine and day. */
@Composable
internal fun FeedPreviewTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalClock provides Clock.fixed(PREVIEW_NOW, ZoneOffset.UTC)) {
        HnFeedTheme(dynamicColor = false, content = content)
    }
}

internal val previewStories: List<Story> = listOf(
    Story(
        id = 1,
        title = "Show HN: I built a tiny Hacker News reader that generates a picture for every story",
        author = "alice",
        score = 412,
        postedAt = PREVIEW_NOW.minusSeconds(2 * 3_600),
        url = "https://github.com/example/hn-visual-feed",
        commentCount = 128,
        type = StoryType.STORY,
    ),
    Story(
        id = 2,
        title = "Acme (YC W26) is hiring Android engineers in Oslo",
        author = "acme",
        score = 1,
        postedAt = PREVIEW_NOW.minusSeconds(26 * 3_600),
        url = "https://www.ycombinator.com/companies/example/jobs",
        commentCount = 0,
        type = StoryType.JOB,
    ),
)
