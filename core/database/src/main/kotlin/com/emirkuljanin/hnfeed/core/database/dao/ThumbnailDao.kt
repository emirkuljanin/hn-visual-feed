package com.emirkuljanin.hnfeed.core.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.emirkuljanin.hnfeed.core.database.model.ThumbnailEntity
import com.emirkuljanin.hnfeed.core.database.model.ThumbnailWork
import com.emirkuljanin.hnfeed.core.database.model.UpgradeState
import java.time.Instant

/**
 * Work queues for thumbnail generation. Both only cover stories that are in the visible feed (loaded entries of the
 * current snapshot), best rank first, so generation follows what the user sees and the cloud quota goes to the top.
 */
@Dao
interface ThumbnailDao {

    /** Feed stories without any thumbnail yet: they need an on-device preview. */
    @Query(
        """
        SELECT stories.id AS story_id, stories.title, NULL AS file_name, 0 AS upgrade_attempts
        FROM top_stories
        INNER JOIN stories ON stories.id = top_stories.story_id
        LEFT JOIN thumbnails ON thumbnails.story_id = stories.id
        WHERE top_stories.is_loaded = 1 AND thumbnails.story_id IS NULL
        ORDER BY top_stories.rank
        LIMIT :limit
        """,
    )
    suspend fun storiesNeedingPreview(limit: Int): List<ThumbnailWork>

    /** Feed stories whose thumbnail should still be upgraded. */
    @Query(
        """
        SELECT stories.id AS story_id, stories.title, thumbnails.file_name, thumbnails.upgrade_attempts
        FROM top_stories
        INNER JOIN stories ON stories.id = top_stories.story_id
        INNER JOIN thumbnails ON thumbnails.story_id = stories.id
        WHERE top_stories.is_loaded = 1 AND thumbnails.upgrade_state = :pending
        ORDER BY top_stories.rank
        LIMIT :limit
        """,
    )
    suspend fun storiesNeedingUpgrade(limit: Int, pending: UpgradeState = UpgradeState.PENDING): List<ThumbnailWork>

    @Upsert
    suspend fun upsert(thumbnail: ThumbnailEntity)

    @Query("SELECT * FROM thumbnails WHERE story_id = :storyId")
    suspend fun get(storyId: Long): ThumbnailEntity?

    @Query(
        """
        UPDATE thumbnails SET upgrade_state = :state, upgrade_attempts = :attempts, updated_at = :updatedAt
        WHERE story_id = :storyId
        """,
    )
    suspend fun updateUpgrade(storyId: Long, state: UpgradeState, attempts: Int, updatedAt: Instant)

    /** Every file a row still points at; anything else in the thumbnails directory is an orphan. */
    @Query("SELECT file_name FROM thumbnails")
    suspend fun fileNames(): List<String>
}
