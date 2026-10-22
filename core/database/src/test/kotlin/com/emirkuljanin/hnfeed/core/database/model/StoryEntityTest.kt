package com.emirkuljanin.hnfeed.core.database.model

import com.emirkuljanin.hnfeed.core.model.Story
import com.emirkuljanin.hnfeed.core.model.StoryType
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class StoryEntityTest {

    @Test
    fun asExternalModel_mapsEveryFieldExceptCacheMetadata() {
        val entity = StoryEntity(
            id = 42,
            title = "Ask HN: Title",
            author = "pg",
            score = 7,
            postedAt = Instant.ofEpochSecond(1_700_000_000),
            url = null,
            commentCount = 3,
            type = StoryType.STORY,
            fetchedAt = Instant.ofEpochSecond(1_800_000_000),
        )

        val expected = Story(
            id = 42,
            title = "Ask HN: Title",
            author = "pg",
            score = 7,
            postedAt = Instant.ofEpochSecond(1_700_000_000),
            url = null,
            commentCount = 3,
            type = StoryType.STORY,
        )
        assertEquals(expected, entity.asExternalModel())
    }
}
