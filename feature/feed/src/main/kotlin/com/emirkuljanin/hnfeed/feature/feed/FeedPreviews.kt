package com.emirkuljanin.hnfeed.feature.feed

import com.emirkuljanin.hnfeed.core.model.Story
import com.emirkuljanin.hnfeed.core.model.StoryType
import com.emirkuljanin.hnfeed.core.model.Thumbnail
import com.emirkuljanin.hnfeed.core.model.ThumbnailSource
import com.emirkuljanin.hnfeed.core.ui.PREVIEW_NOW

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
        thumbnail = Thumbnail(filePath = "/preview/1-cloud.webp", source = ThumbnailSource.CLOUD),
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
