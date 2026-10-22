package com.emirkuljanin.hnfeed.core.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class StoryTest {

    @Test
    fun discussionUrl_pointsToTheHackerNewsItemPage() {
        val story = Story(
            id = 8863,
            title = "My YC app: Dropbox - Throw away your USB drive",
            author = "dhouston",
            score = 111,
            postedAt = Instant.ofEpochSecond(1_175_714_200),
            url = "http://www.getdropbox.com/u/2/screencast.html",
            commentCount = 71,
            type = StoryType.STORY,
        )

        assertEquals("https://news.ycombinator.com/item?id=8863", story.discussionUrl)
    }
}
