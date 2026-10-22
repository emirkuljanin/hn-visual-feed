package com.emirkuljanin.hnfeed.core.database.dao

import androidx.paging.PagingSource
import androidx.room3.Dao
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.Query
import androidx.room3.Upsert
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter
import com.emirkuljanin.hnfeed.core.database.model.StoryEntity

@Dao
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
interface StoryDao {

    @Upsert
    suspend fun upsert(stories: List<StoryEntity>)

    /**
     * Removes cached stories, for ids the API now returns as missing, deleted or dead. The data layer calls this in
     * the same transaction as marking those ids loaded, so a killed story cached from an older snapshot cannot
     * resurface in the feed with its stale title and score.
     */
    @Query("DELETE FROM stories WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    /**
     * The feed: loaded entries of the current snapshot, best rank first.
     *
     * Paging loads entries strictly in rank order, so the loaded entries are always the top N of the snapshot and
     * new pages only ever appear below what the user has seen. Unloaded entries stay hidden even when an older
     * snapshot left their story cached, because showing them would put stale, out-of-order rows in the feed.
     * Loaded entries without a story row (items that were missing, deleted or dead, see [delete]) are dropped by
     * the join.
     */
    @Query(
        """
        SELECT stories.* FROM top_stories
        INNER JOIN stories ON stories.id = top_stories.story_id
        WHERE top_stories.is_loaded = 1
        ORDER BY top_stories.rank
        """,
    )
    fun topStoriesPagingSource(): PagingSource<Int, StoryEntity>
}
