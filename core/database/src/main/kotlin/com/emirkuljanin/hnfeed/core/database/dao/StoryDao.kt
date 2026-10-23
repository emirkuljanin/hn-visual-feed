package com.emirkuljanin.hnfeed.core.database.dao

import androidx.paging.PagingSource
import androidx.room3.Dao
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.Query
import androidx.room3.Upsert
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter
import com.emirkuljanin.hnfeed.core.database.model.FeedStoryEntity
import com.emirkuljanin.hnfeed.core.database.model.StoryEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
interface StoryDao {

    /**
     * Inserts or updates stories. `@Upsert` updates existing rows in place (no delete + insert, unlike REPLACE), so a
     * refreshed story keeps its thumbnail instead of losing it to the cascade.
     */
    @Upsert
    suspend fun upsert(stories: List<StoryEntity>)

    /**
     * Removes cached stories, for ids the API now returns as missing, deleted or dead. The data layer calls this in
     * the same transaction as marking those ids loaded, so a killed story cached from an older snapshot cannot
     * resurface in the feed with its stale title and score. Their thumbnails go with them (cascade).
     */
    @Query("DELETE FROM stories WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    /** Evicts stories that left the snapshot and were last fetched before [cutoff]; returns how many. */
    @Query(
        """
        DELETE FROM stories
        WHERE fetched_at < :cutoff AND id NOT IN (SELECT story_id FROM top_stories)
        """,
    )
    suspend fun deleteStale(cutoff: Instant): Int

    /**
     * The feed: loaded entries of the current snapshot, best rank first, each with its thumbnail once generated.
     *
     * Paging loads entries strictly in rank order, so the loaded entries are always the top N of the snapshot and
     * new pages only ever appear below what the user has seen. Unloaded entries stay hidden even when an older
     * snapshot left their story cached, because showing them would put stale, out-of-order rows in the feed.
     * Loaded entries without a story row (items that were missing, deleted or dead, see [delete]) are dropped by
     * the join. Writing a thumbnail invalidates this source too, which is how cards swap in their images.
     */
    @Query(
        """
        SELECT stories.*, thumbnails.file_name AS thumbnail_file, thumbnails.source AS thumbnail_source
        FROM top_stories
        INNER JOIN stories ON stories.id = top_stories.story_id
        LEFT JOIN thumbnails ON thumbnails.story_id = stories.id
        WHERE top_stories.is_loaded = 1
        ORDER BY top_stories.rank
        """,
    )
    fun topStoriesPagingSource(): PagingSource<Int, FeedStoryEntity>

    /** One story with its thumbnail, for the detail screen; `null` until it is cached. */
    @Query(
        """
        SELECT stories.*, thumbnails.file_name AS thumbnail_file, thumbnails.source AS thumbnail_source
        FROM stories
        LEFT JOIN thumbnails ON thumbnails.story_id = stories.id
        WHERE stories.id = :id
        """,
    )
    fun observeStory(id: Long): Flow<FeedStoryEntity?>
}
