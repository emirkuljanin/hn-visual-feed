package com.emirkuljanin.hnfeed.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import com.emirkuljanin.hnfeed.core.database.model.TopStoryEntity
import java.time.Instant

@Dao
abstract class TopStoryDao {

    /**
     * Replaces the snapshot with [storyIds] in the given order, all marked as not loaded. Should an id appear twice,
     * it keeps its first (best) rank.
     */
    @Transaction
    open suspend fun replaceSnapshot(storyIds: List<Long>) {
        clear()
        insert(storyIds.distinct().mapIndexed { rank, id -> TopStoryEntity(rank = rank, storyId = id) })
    }

    /** The best-ranked ids whose details have not been fetched yet; empty once the whole snapshot is loaded. */
    @Query("SELECT story_id FROM top_stories WHERE is_loaded = 0 ORDER BY rank LIMIT :limit")
    abstract suspend fun nextUnloadedStoryIds(limit: Int): List<Long>

    @Query("UPDATE top_stories SET is_loaded = 1 WHERE story_id IN (:storyIds)")
    abstract suspend fun markLoaded(storyIds: List<Long>)

    /**
     * When the current snapshot was refreshed: the earliest fetch time among its loaded stories. A refresh writes
     * the first page and later appends only add younger rows, so this is the refresh time. `null` if nothing is
     * loaded yet.
     */
    @Query(
        """
        SELECT MIN(stories.fetched_at) FROM top_stories
        INNER JOIN stories ON stories.id = top_stories.story_id
        WHERE top_stories.is_loaded = 1
        """,
    )
    abstract suspend fun snapshotLoadedAt(): Instant?

    @Query("DELETE FROM top_stories")
    protected abstract suspend fun clear()

    @Insert
    protected abstract suspend fun insert(entries: List<TopStoryEntity>)
}
