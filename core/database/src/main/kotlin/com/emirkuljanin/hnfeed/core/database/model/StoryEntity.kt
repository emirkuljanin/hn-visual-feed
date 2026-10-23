package com.emirkuljanin.hnfeed.core.database.model

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.emirkuljanin.hnfeed.core.model.Story
import com.emirkuljanin.hnfeed.core.model.StoryType
import java.time.Instant

/**
 * Cached details of a story. Rows outlive the top-stories snapshot they were fetched for, so the feed still has
 * content offline; [fetchedAt] lets old rows be evicted.
 */
@Entity(tableName = "stories")
data class StoryEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val author: String,
    val score: Int,
    @ColumnInfo(name = "posted_at") val postedAt: Instant,
    val url: String?,
    @ColumnInfo(name = "comment_count") val commentCount: Int,
    val type: StoryType,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Instant,
    /** The body of text posts, as HTML. */
    val text: String? = null,
)

fun StoryEntity.asExternalModel() = Story(
    id = id,
    title = title,
    author = author,
    score = score,
    postedAt = postedAt,
    url = url,
    commentCount = commentCount,
    type = type,
    text = text,
)
