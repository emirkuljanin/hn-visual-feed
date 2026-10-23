package com.emirkuljanin.hnfeed.core.testing.data

import com.emirkuljanin.hnfeed.core.model.Story
import com.emirkuljanin.hnfeed.core.model.StoryType
import com.emirkuljanin.hnfeed.core.model.Thumbnail
import com.emirkuljanin.hnfeed.core.model.ThumbnailSource
import java.time.Instant

/** The fixed "now" that test stories are dated against; screenshot tests use it as their clock. */
val TEST_NOW: Instant = Instant.parse("2026-10-23T12:00:00Z")

/** A thumbnail at a path that does not exist; UI tests draw it with Coil's preview handler instead. */
fun testThumbnail(storyId: Long, source: ThumbnailSource) =
    Thumbnail(filePath = "/test/thumbnails/$storyId-${source.name.lowercase()}.webp", source = source)

fun testStory(
    id: Long,
    title: String = "Story $id",
    author: String = "author$id",
    score: Int = 100,
    postedAt: Instant = TEST_NOW.minusSeconds(3_600),
    url: String? = "https://example.com/story/$id",
    commentCount: Int = 42,
    type: StoryType = StoryType.STORY,
    thumbnail: Thumbnail? = null,
) = Story(
    id = id,
    title = title,
    author = author,
    score = score,
    postedAt = postedAt,
    url = url,
    commentCount = commentCount,
    type = type,
    thumbnail = thumbnail,
)

/**
 * A realistic mix for UI and screenshot tests: long and short titles, a text post, a job, a poll, ages and counts,
 * cloud and on-device thumbnails, and one story whose thumbnail is not ready yet.
 */
val testStories: List<Story> = listOf(
    testStory(
        id = 1,
        title = "Show HN: I built a tiny Hacker News reader that generates a picture for every story",
        author = "alice",
        score = 412,
        postedAt = TEST_NOW.minusSeconds(2 * 3_600),
        url = "https://github.com/example/hn-visual-feed",
        commentCount = 128,
        thumbnail = testThumbnail(1, ThumbnailSource.CLOUD),
    ),
    testStory(
        id = 2,
        title = "Ask HN: What is your favourite under-appreciated Android API?",
        author = "pg",
        score = 1,
        postedAt = TEST_NOW.minusSeconds(5 * 60),
        url = null,
        commentCount = 1,
        thumbnail = testThumbnail(2, ThumbnailSource.ON_DEVICE),
    ),
    testStory(
        id = 3,
        title = "Acme (YC W26) is hiring Android engineers in Oslo",
        author = "acme",
        score = 1,
        postedAt = TEST_NOW.minusSeconds(26 * 3_600),
        url = "https://www.ycombinator.com/companies/example/jobs",
        commentCount = 0,
        type = StoryType.JOB,
        thumbnail = testThumbnail(3, ThumbnailSource.CLOUD),
    ),
    testStory(
        id = 4,
        title = "Poll: Which pagination strategy do you use?",
        author = "dang",
        score = 87,
        postedAt = TEST_NOW.minusSeconds(3 * 24 * 3_600),
        url = null,
        commentCount = 64,
        type = StoryType.POLL,
    ),
)
