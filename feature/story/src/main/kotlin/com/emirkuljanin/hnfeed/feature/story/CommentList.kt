package com.emirkuljanin.hnfeed.feature.story

import com.emirkuljanin.hnfeed.core.model.Comment
import com.emirkuljanin.hnfeed.core.model.CommentStatus
import com.emirkuljanin.hnfeed.core.model.StoryComments

/** One row of the flattened comment tree. */
sealed interface CommentListItem {
    /** Stable across updates, for the list's item keys. */
    val key: String

    /** Nesting level: 0 for top-level comments. */
    val depth: Int

    data class CommentRow(
        val comment: Comment,
        override val depth: Int,
        /** Written by the story's author. */
        val isByOriginalPoster: Boolean,
        /** `null` without replies. */
        val replies: Replies?,
    ) : CommentListItem {
        override val key: String get() = "comment-${comment.id}"
    }

    /** [count] more comments under [parentId] that are not loaded yet. */
    data class MoreComments(
        val parentId: Long,
        override val depth: Int,
        val count: Int,
        val isTopLevel: Boolean,
        val state: LoadState,
    ) : CommentListItem {
        override val key: String get() = "more-$parentId"
    }
}

data class Replies(val count: Int, val isExpanded: Boolean)

enum class LoadState {
    IDLE,
    LOADING,
    FAILED,
}

/**
 * Flattens the loaded comment tree into list rows, depth first in display order. Top-level comments are always
 * shown; replies only under [expanded] comments. Each level ends with a [CommentListItem.MoreComments] row while it
 * has comments that are not loaded yet. Deleted and flagged comments are shown as placeholders only when they have
 * replies to show, as on Hacker News; loaded replies that are hidden that way are not counted. (Unloaded ones are:
 * whether they are deleted is not known until they load.)
 *
 * [loading] and [failed] hold the parents whose next comments are loading or failed to load; the story id stands
 * for the top level, which also shows as loading while [isRefreshing].
 */
internal fun buildCommentList(
    storyId: Long,
    storyAuthor: String,
    comments: StoryComments,
    expanded: Set<Long>,
    loading: Set<Long>,
    failed: Set<Long>,
    isRefreshing: Boolean,
): List<CommentListItem> {
    val childrenByParent = comments.comments.groupBy { it.parentId }
    val shownReplyCounts = mutableMapOf<Long, Int>()

    // Replies minus loaded ones that are hidden; computed bottom-up, since hiding can cascade up a dead branch.
    fun shownReplyCount(comment: Comment): Int = shownReplyCounts.getOrPut(comment.id) {
        val hidden = childrenByParent[comment.id].orEmpty().count { reply ->
            reply.status != CommentStatus.VISIBLE && shownReplyCount(reply) == 0
        }
        comment.replyCount - hidden
    }

    val rows = mutableListOf<CommentListItem>()

    fun addLevel(parentId: Long, total: Int, depth: Int) {
        val children = childrenByParent[parentId].orEmpty()
        for (comment in children) {
            val replyCount = shownReplyCount(comment)
            if (comment.status != CommentStatus.VISIBLE && replyCount == 0) continue
            val isExpanded = comment.id in expanded && replyCount > 0
            rows += CommentListItem.CommentRow(
                comment = comment,
                depth = depth,
                isByOriginalPoster = comment.author == storyAuthor,
                replies = if (replyCount > 0) Replies(replyCount, isExpanded) else null,
            )
            if (isExpanded) addLevel(comment.id, comment.replyCount, depth + 1)
        }
        val remaining = total - children.size
        if (remaining > 0) {
            val isTopLevel = parentId == storyId
            rows += CommentListItem.MoreComments(
                parentId = parentId,
                depth = depth,
                count = remaining,
                isTopLevel = isTopLevel,
                state = when {
                    parentId in loading || (isTopLevel && isRefreshing) -> LoadState.LOADING
                    parentId in failed -> LoadState.FAILED
                    else -> LoadState.IDLE
                },
            )
        }
    }

    addLevel(storyId, comments.topLevelCount, depth = 0)
    return rows
}
