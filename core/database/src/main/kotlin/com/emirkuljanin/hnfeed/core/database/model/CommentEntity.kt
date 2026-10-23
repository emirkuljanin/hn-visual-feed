package com.emirkuljanin.hnfeed.core.database.model

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.emirkuljanin.hnfeed.core.model.Comment
import com.emirkuljanin.hnfeed.core.model.CommentStatus
import java.time.Instant

/**
 * A comment tree as an adjacency list: every row knows its parent ([parentId], the story id for top-level comments)
 * and its position among its siblings ([rank], the order of the parent's `kids` in the API).
 *
 * A row exists as soon as its parent is loaded, as a stub with only id, parent and rank ([isLoaded] false), so the
 * number of replies is known before any of them is fetched. Loading fills in the content. Rows belong to their story
 * ([storyId]) and are deleted with it.
 */
@Entity(
    tableName = "comments",
    foreignKeys = [
        ForeignKey(
            entity = StoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["story_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("story_id"), Index("parent_id", "rank")],
)
data class CommentEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "story_id") val storyId: Long,
    @ColumnInfo(name = "parent_id") val parentId: Long,
    val rank: Int,
    @ColumnInfo(name = "is_loaded") val isLoaded: Boolean,
    val author: String?,
    val text: String?,
    @ColumnInfo(name = "posted_at") val postedAt: Instant?,
    val deleted: Boolean,
    val dead: Boolean,
)

/** Where a comment sits in its parent's list; enough to load it. */
data class CommentSlot(val id: Long, val rank: Int)

/** A loaded comment with the number of its replies (stub rows included). */
data class LoadedCommentEntity(
    @Embedded val comment: CommentEntity,
    @ColumnInfo(name = "reply_count") val replyCount: Int,
)

fun LoadedCommentEntity.asExternalModel() = Comment(
    id = comment.id,
    parentId = comment.parentId,
    author = comment.author,
    text = comment.text,
    postedAt = comment.postedAt,
    replyCount = replyCount,
    status = when {
        comment.deleted -> CommentStatus.DELETED
        comment.dead -> CommentStatus.FLAGGED
        else -> CommentStatus.VISIBLE
    },
)
