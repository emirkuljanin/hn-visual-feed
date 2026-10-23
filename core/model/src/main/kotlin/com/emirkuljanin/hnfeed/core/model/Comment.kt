package com.emirkuljanin.hnfeed.core.model

import java.time.Instant

/**
 * A comment that has been loaded, replying to a story (top-level) or to another comment. Replies are loaded on
 * demand: [replyCount] says how many there are, loaded or not.
 */
data class Comment(
    val id: Long,
    /** The story for top-level comments, otherwise the comment this one replies to. */
    val parentId: Long,
    /** `null` for deleted comments. */
    val author: String?,
    /** The body as the API's HTML; `null` for deleted and flagged comments. */
    val text: String?,
    /** `null` for deleted comments. */
    val postedAt: Instant?,
    val replyCount: Int,
    val status: CommentStatus,
)

enum class CommentStatus {
    VISIBLE,

    /** Removed by its author; kept as a placeholder when it has replies. */
    DELETED,

    /** Killed by moderators or flags (HN's "dead"); its text is not stored or shown. */
    FLAGGED,
}

/**
 * The loaded comments of a story: every loaded comment at any depth, grouped by parent and in display order
 * within each parent. [topLevelCount] is how many top-level comments the story has, loaded or not.
 */
data class StoryComments(val topLevelCount: Int, val comments: List<Comment>)
