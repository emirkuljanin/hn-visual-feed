package com.emirkuljanin.hnfeed.core.data.model

import com.emirkuljanin.hnfeed.core.database.model.StoryEntity
import com.emirkuljanin.hnfeed.core.model.StoryType
import com.emirkuljanin.hnfeed.core.network.model.NetworkItem
import java.time.Instant

/**
 * Maps an API item to a cacheable story, or `null` if it should not be shown in the feed: deleted or dead
 * (flagged/killed) items, types that never belong on the front page, and items missing the fields a card needs.
 */
internal fun NetworkItem.asStoryEntityOrNull(fetchedAt: Instant): StoryEntity? {
    if (deleted || dead) return null
    val storyType = when (type) {
        "story" -> StoryType.STORY
        "job" -> StoryType.JOB
        "poll" -> StoryType.POLL
        else -> return null
    }
    val title = title?.takeIf { it.isNotBlank() } ?: return null
    val author = by ?: return null
    val time = time ?: return null

    return StoryEntity(
        id = id,
        title = title,
        author = author,
        score = score ?: 0,
        postedAt = Instant.ofEpochSecond(time),
        url = url?.takeIf(::isWebUrl),
        commentCount = descendants ?: 0,
        type = storyType,
        fetchedAt = fetchedAt,
    )
}

/** Only web links are kept: anything else (blank, `javascript:`, app schemes) falls back to the HN discussion page. */
private fun isWebUrl(url: String): Boolean =
    url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)
