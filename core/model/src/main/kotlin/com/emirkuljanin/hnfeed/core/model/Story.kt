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
    /** The body of text posts (Ask HN, jobs, polls), as the API's HTML. `null` for most link posts. */
    val text: String? = null,
    /** The generated thumbnail, once there is one; the UI shows a placeholder until then. */
    val thumbnail: Thumbnail? = null,
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

/** A generated story thumbnail on local storage. */
data class Thumbnail(val filePath: String, val source: ThumbnailSource)

enum class ThumbnailSource {
    /** Abstract art painted on the device: shown first, always available. */
    ON_DEVICE,

    /** A text-to-image model's illustration, which replaces the on-device art when the quota allows. */
    CLOUD,
}
