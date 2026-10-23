package com.emirkuljanin.hnfeed.core.testing.repository

import com.emirkuljanin.hnfeed.core.data.repository.StoryDetailsRepository
import com.emirkuljanin.hnfeed.core.model.Story
import com.emirkuljanin.hnfeed.core.model.StoryComments
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A repository backed by mutable state: tests set [story] and [comments] directly. [refresh] and
 * [loadMoreComments] record their calls, fail with [failure] when set, and wait for [gate] when set, so a test can
 * look at the in-between state.
 */
class FakeStoryDetailsRepository(story: Story? = null, comments: StoryComments = StoryComments(0, emptyList())) :
    StoryDetailsRepository {

    val story = MutableStateFlow(story)
    val comments = MutableStateFlow(comments)

    var failure: IOException? = null
    var gate: CompletableDeferred<Unit>? = null

    val refreshes = mutableListOf<Long>()
    val loadMoreRequests = mutableListOf<Long>()

    override fun story(id: Long): Flow<Story?> = story

    override fun comments(storyId: Long): Flow<StoryComments> = comments

    override suspend fun refresh(storyId: Long) {
        refreshes += storyId
        gate?.await()
        failure?.let { throw it }
    }

    override suspend fun loadMoreComments(storyId: Long, parentId: Long) {
        loadMoreRequests += parentId
        gate?.await()
        failure?.let { throw it }
    }
}
