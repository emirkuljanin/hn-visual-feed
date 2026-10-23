package com.emirkuljanin.hnfeed.core.data.repository

import android.database.SQLException
import com.emirkuljanin.hnfeed.core.data.model.asCommentEntityOrNull
import com.emirkuljanin.hnfeed.core.data.model.asStoryEntityOrNull
import com.emirkuljanin.hnfeed.core.data.thumbnail.ThumbnailStore
import com.emirkuljanin.hnfeed.core.database.DatabaseTransactionRunner
import com.emirkuljanin.hnfeed.core.database.dao.CommentDao
import com.emirkuljanin.hnfeed.core.database.dao.StoryDao
import com.emirkuljanin.hnfeed.core.database.model.CommentSlot
import com.emirkuljanin.hnfeed.core.database.model.asExternalModel
import com.emirkuljanin.hnfeed.core.model.Story
import com.emirkuljanin.hnfeed.core.model.StoryComments
import com.emirkuljanin.hnfeed.core.network.HnNetworkDataSource
import com.emirkuljanin.hnfeed.core.network.model.NetworkItem
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Comments are fetched one parallel batch (one request each) at a time, like feed pages. */
internal const val COMMENTS_PAGE_SIZE = 20

private const val MAX_BOUND_IDS = 500

/**
 * Comments live in Room as an adjacency list (see `CommentEntity`). Loading a comment also records its replies as
 * unloaded stubs, so every level knows how many replies it has and which to fetch next; nothing below the first
 * page of each level is fetched until the UI asks for it. Network first, then one transaction, so a failure leaves
 * the cache as it was.
 */
internal class OfflineFirstStoryDetailsRepository @Inject constructor(
    private val network: HnNetworkDataSource,
    private val storyDao: StoryDao,
    private val commentDao: CommentDao,
    private val transaction: DatabaseTransactionRunner,
    private val thumbnailStore: ThumbnailStore,
    private val clock: Clock,
) : StoryDetailsRepository {

    override fun story(id: Long): Flow<Story?> =
        storyDao.observeStory(id).map { it?.asExternalModel(thumbnailStore::path) }

    override fun comments(storyId: Long): Flow<StoryComments> = combine(
        commentDao.observeTopLevelCount(storyId),
        commentDao.observeLoaded(storyId),
    ) { topLevelCount, loaded ->
        StoryComments(topLevelCount = topLevelCount, comments = loaded.map { it.asExternalModel() })
    }

    override suspend fun refresh(storyId: Long) {
        val item = network.items(listOf(storyId)).singleOrNull()
        val story = item?.asStoryEntityOrNull(fetchedAt = clock.instant())
            ?: throw IOException("Story $storyId is no longer available")
        // The first page is fetched again even if cached: comments can be edited for a while after posting, and
        // their replies may have changed.
        val firstPage = item.kids.take(COMMENTS_PAGE_SIZE).mapIndexed { rank, id -> CommentSlot(id, rank) }
        val comments = network.items(firstPage.map { it.id })
        write {
            storyDao.upsert(listOf(story))
            replaceChildren(storyId, parentId = storyId, kids = item.kids)
            saveComments(storyId, parentId = storyId, firstPage, comments)
        }
    }

    override suspend fun loadMoreComments(storyId: Long, parentId: Long) {
        val slots = commentDao.unloadedChildren(parentId, limit = COMMENTS_PAGE_SIZE)
        if (slots.isEmpty()) return
        val comments = network.items(slots.map { it.id })
        write {
            // A refresh may have re-ranked or removed these comments while we were fetching. Writing then would bring
            // removed ones back at stale ranks; dropping the result is harmless, the next request loads them again.
            if (commentDao.unloadedChildren(parentId, limit = slots.size) == slots) {
                saveComments(storyId, parentId, slots, comments)
            }
        }
    }

    /** Records [kids] as the children of [parentId], in order: new ones as stubs, gone ones removed. */
    private suspend fun replaceChildren(storyId: Long, parentId: Long, kids: List<Long>) {
        val removed = commentDao.childIds(parentId) - kids.toSet()
        // Chunked: SQLite before 3.32 (Android 11 and older) allows at most 999 bound variables per statement, and
        // big threads ("Ask HN: Who is hiring?") have more top-level comments than that.
        removed.chunked(MAX_BOUND_IDS).forEach { commentDao.delete(it) }
        kids.forEachIndexed { rank, id ->
            commentDao.insertStub(id, storyId, parentId, rank)
            commentDao.move(id, parentId, rank)
        }
    }

    /**
     * Writes the fetched [items] into their [slots] under [parentId], with their replies as stubs. Slots the API
     * returned nothing usable for (unknown ids, other item types) are removed, so the reply counts stay honest.
     */
    private suspend fun saveComments(
        storyId: Long,
        parentId: Long,
        slots: List<CommentSlot>,
        items: List<NetworkItem>,
    ) {
        val itemsById = items.associateBy { it.id }
        val entities = slots.mapNotNull { slot ->
            itemsById[slot.id]?.asCommentEntityOrNull(storyId = storyId, parentId = parentId, rank = slot.rank)
        }
        commentDao.delete(slots.map { it.id } - entities.map { it.id }.toSet())
        commentDao.upsert(entities)
        entities.forEach { comment -> replaceChildren(storyId, comment.id, itemsById.getValue(comment.id).kids) }
    }

    private suspend fun write(block: suspend () -> Unit) {
        try {
            transaction(block)
        } catch (e: SQLException) {
            throw IOException("Could not save comments", e)
        }
    }
}
