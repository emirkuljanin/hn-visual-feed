package com.emirkuljanin.hnfeed.core.data.repository

import com.emirkuljanin.hnfeed.core.model.Story
import com.emirkuljanin.hnfeed.core.model.StoryComments
import kotlinx.coroutines.flow.Flow

/**
 * A story and its comment tree, offline first: the flows emit what is cached (also offline) and update as
 * [refresh] and [loadMoreComments] write into the database. Both suspend functions are main-safe and fail with an
 * [java.io.IOException] (network, unusable response or storage), writing nothing in that case.
 */
interface StoryDetailsRepository {

    /** The cached story with its thumbnail; `null` until it is cached. */
    fun story(id: Long): Flow<Story?>

    /** The story's loaded comments, at every depth. */
    fun comments(storyId: Long): Flow<StoryComments>

    /** Fetches the story again (score, comment count, text, top-level comments) and its first comments. */
    suspend fun refresh(storyId: Long)

    /**
     * Loads the next comments under [parentId]: the story itself for top-level comments, or a comment for its
     * replies. Does nothing when all of them are loaded.
     */
    suspend fun loadMoreComments(storyId: Long, parentId: Long)
}
