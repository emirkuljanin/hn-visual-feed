package com.emirkuljanin.hnfeed.core.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.emirkuljanin.hnfeed.core.database.model.CommentEntity
import com.emirkuljanin.hnfeed.core.database.model.CommentSlot
import com.emirkuljanin.hnfeed.core.database.model.LoadedCommentEntity
import kotlinx.coroutines.flow.Flow

/** See [CommentEntity] for the tree layout. Multi-statement updates run in the data layer's transactions. */
@Dao
interface CommentDao {

    /** Adds a not-yet-loaded comment; keeps an existing row (and its content) as it is. */
    @Query(
        """
        INSERT OR IGNORE INTO comments (id, story_id, parent_id, rank, is_loaded, deleted, dead)
        VALUES (:id, :storyId, :parentId, :rank, 0, 0, 0)
        """,
    )
    suspend fun insertStub(id: Long, storyId: Long, parentId: Long, rank: Int)

    /** Updates where a comment sits, e.g. after the parent's replies were re-ranked; its content is untouched. */
    @Query("UPDATE comments SET parent_id = :parentId, rank = :rank WHERE id = :id")
    suspend fun move(id: Long, parentId: Long, rank: Int)

    @Query("SELECT id FROM comments WHERE parent_id = :parentId")
    suspend fun childIds(parentId: Long): List<Long>

    /**
     * Removes comments, e.g. replies that are no longer among their parent's. Their own replies stay behind
     * unreachable (nothing displays a subtree whose root is gone) until the story is deleted. Callers keep [ids]
     * well below SQLite's bound-variable limit.
     */
    @Query("DELETE FROM comments WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    /** Writes loaded comments (content included), replacing their stubs in place. */
    @Upsert
    suspend fun upsert(comments: List<CommentEntity>)

    /** The first [limit] children of [parentId], loaded or not, in display order. */
    @Query("SELECT id, rank FROM comments WHERE parent_id = :parentId ORDER BY rank LIMIT :limit")
    suspend fun children(parentId: Long, limit: Int): List<CommentSlot>

    /** The next [limit] children of [parentId] still to load, in display order. */
    @Query("SELECT id, rank FROM comments WHERE parent_id = :parentId AND is_loaded = 0 ORDER BY rank LIMIT :limit")
    suspend fun unloadedChildren(parentId: Long, limit: Int): List<CommentSlot>

    /** Every loaded comment of [storyId] with its reply count, grouped by parent, in display order. */
    @Query(
        """
        SELECT comments.*, (SELECT COUNT(*) FROM comments AS replies WHERE replies.parent_id = comments.id)
            AS reply_count
        FROM comments
        WHERE story_id = :storyId AND is_loaded = 1
        ORDER BY parent_id, rank
        """,
    )
    fun observeLoaded(storyId: Long): Flow<List<LoadedCommentEntity>>

    /** How many top-level comments [storyId] has, loaded or not. */
    @Query("SELECT COUNT(*) FROM comments WHERE parent_id = :storyId")
    fun observeTopLevelCount(storyId: Long): Flow<Int>
}
