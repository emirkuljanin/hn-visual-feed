package com.emirkuljanin.hnfeed.core.data.model

import com.emirkuljanin.hnfeed.core.database.model.StoryEntity
import com.emirkuljanin.hnfeed.core.model.StoryType
import com.emirkuljanin.hnfeed.core.network.model.NetworkItem
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NetworkItemMapperTest {

    private val fetchedAt = Instant.parse("2026-10-23T10:00:00Z")
    private val story = NetworkItem(
        id = 8863,
        type = "story",
        by = "dhouston",
        time = 1_175_714_200,
        title = "My YC app: Dropbox - Throw away your USB drive",
        url = "http://www.getdropbox.com/u/2/screencast.html",
        score = 111,
        descendants = 71,
    )

    @Test
    fun mapsAStory() {
        val expected = StoryEntity(
            id = 8863,
            title = "My YC app: Dropbox - Throw away your USB drive",
            author = "dhouston",
            score = 111,
            postedAt = Instant.ofEpochSecond(1_175_714_200),
            url = "http://www.getdropbox.com/u/2/screencast.html",
            commentCount = 71,
            type = StoryType.STORY,
            fetchedAt = fetchedAt,
        )
        assertEquals(expected, story.asStoryEntityOrNull(fetchedAt))
    }

    @Test
    fun mapsJobsAndPolls() {
        assertEquals(StoryType.JOB, story.copy(type = "job").asStoryEntityOrNull(fetchedAt)?.type)
        assertEquals(StoryType.POLL, story.copy(type = "poll").asStoryEntityOrNull(fetchedAt)?.type)
    }

    @Test
    fun defaultsMissingCounts_andTreatsABlankUrlAsNone() {
        val entity = story.copy(score = null, descendants = null, url = " ").asStoryEntityOrNull(fetchedAt)

        assertEquals(0, entity?.score)
        assertEquals(0, entity?.commentCount)
        assertNull(entity?.url)
    }

    @Test
    fun rejectsItemsThatDoNotBelongInTheFeed() {
        val rejected = listOf(
            story.copy(deleted = true),
            story.copy(dead = true),
            story.copy(type = "comment"),
            story.copy(type = "pollopt"),
            story.copy(type = null),
            story.copy(title = null),
            story.copy(title = "  "),
            story.copy(by = null),
            story.copy(time = null),
        )

        rejected.forEach { assertNull(it.asStoryEntityOrNull(fetchedAt), "Expected $it to be rejected") }
    }
}
