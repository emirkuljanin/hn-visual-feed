package com.emirkuljanin.hnfeed.core.testing.data

import com.emirkuljanin.hnfeed.core.model.Story
import com.emirkuljanin.hnfeed.core.model.StoryType
import java.time.Instant

/** The fixed "now" that test stories are dated against; screenshot tests use it as their clock. */
val TEST_NOW: Instant = Instant.parse("2026-10-23T12:00:00Z")

fun testStory(
    id: Long,
    title: String = "Story $id",
    author: String = "author$id",
    score: Int = 100,
    postedAt: Instant = TEST_NOW.minusSeconds(3_600),
    url: String? = "https://example.com/story/$id",
    commentCount: Int = 42,
    type: StoryType = StoryType.STORY,
) = Story(
    id = id,
    title = title,
    author = author,
    score = score,
    postedAt = postedAt,
    url = url,
    commentCount = commentCount,
    type = type,
)

/** A realistic mix for UI and screenshot tests: long and short titles, a text post, a job, a poll, ages and counts. */
val testStories: List<Story> = listOf(
    testStory(
        id = 1,
        title = "Show HN: I built a tiny Hacker News reader that generates a picture for every story",
        author = "alice",
        score = 412,
        postedAt = TEST_NOW.minusSeconds(2 * 3_600),
        url = "https://github.com/example/hn-visual-feed",
        commentCount = 128,
    ),
    testStory(
        id = 2,
        title = "Ask HN: What is your favourite under-appreciated Android API?",
        author = "pg",
        score = 1,
        postedAt = TEST_NOW.minusSeconds(5 * 60),
        url = null,
        commentCount = 1,
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
