package com.emirkuljanin.hnfeed.core.database.model

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * One entry of the top-stories snapshot: the ranked id list fetched on refresh, which paging then walks through.
 * Storing the snapshot keeps pages stable while the live ranking keeps changing (no duplicates or skipped stories).
 *
 * There is deliberately no foreign key to `stories`: entries exist before their story details are fetched.
 */
@Entity(
    tableName = "top_stories",
    indices = [Index(value = ["story_id"], unique = true)],
)
data class TopStoryEntity(
    @PrimaryKey val rank: Int,
    @ColumnInfo(name = "story_id") val storyId: Long,
    /** Whether details were fetched for this entry, including items that turned out deleted or dead. */
    @ColumnInfo(name = "is_loaded") val isLoaded: Boolean = false,
)
