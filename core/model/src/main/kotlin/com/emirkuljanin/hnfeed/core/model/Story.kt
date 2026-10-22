package com.emirkuljanin.hnfeed.core.model

import java.time.Instant

/** A Hacker News front-page item, as shown in the feed. */
data class Story(
    val id: Long,
    val title: String,
    val author: String,
    val score: Int,
    val postedAt: Instant,
    /** The linked article. `null` for text posts such as Ask HN and most job ads. */
    val url: String?,
    val commentCount: Int,
    val type: StoryType,
) {
    /** The story's discussion page on Hacker News. */
    val discussionUrl: String
        get() = "https://news.ycombinator.com/item?id=$id"
}

/** The item types that can appear in the top stories list. Comments and poll options never do. */
enum class StoryType {
    STORY,
    JOB,
    POLL,
}
