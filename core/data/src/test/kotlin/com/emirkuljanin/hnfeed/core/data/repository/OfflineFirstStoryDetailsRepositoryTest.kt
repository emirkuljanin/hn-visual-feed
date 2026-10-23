package com.emirkuljanin.hnfeed.core.data.repository

import com.emirkuljanin.hnfeed.core.data.testing.FakeHnNetworkDataSource
import com.emirkuljanin.hnfeed.core.data.testing.InMemoryDatabaseTest
import com.emirkuljanin.hnfeed.core.data.testing.networkStory
import com.emirkuljanin.hnfeed.core.data.thumbnail.ThumbnailStore
import com.emirkuljanin.hnfeed.core.model.Comment
import com.emirkuljanin.hnfeed.core.model.CommentStatus
import com.emirkuljanin.hnfeed.core.network.model.NetworkItem
import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder

internal class OfflineFirstStoryDetailsRepositoryTest : InMemoryDatabaseTest() {

    @get:Rule
    val folder = TemporaryFolder()

    private val network = FakeHnNetworkDataSource()
    private val repository by lazy {
        OfflineFirstStoryDetailsRepository(
            network = network,
            storyDao = storyDao,
            commentDao = database.commentDao(),
            transaction = transaction,
            thumbnailStore = ThumbnailStore(File(folder.root, "thumbnails"), Dispatchers.IO),
            clock = clock,
        )
    }

    @Test
    fun refresh_storesTheStory_allTopLevelSlots_andTheFirstPageOfComments() = runTest {
        val topLevel = (101L..125L).toList()
        story(kids = topLevel, text = "<p>Ask HN body</p>")
        topLevel.forEach { comment(it, parent = STORY) }
        comment(101, parent = STORY, kids = listOf(201, 202))

        repository.refresh(STORY)

        assertEquals("<p>Ask HN body</p>", repository.story(STORY).first()!!.text)
        val comments = repository.comments(STORY).first()
        assertEquals(25, comments.topLevelCount)
        assertEquals(topLevel.take(COMMENTS_PAGE_SIZE), comments.comments.map { it.id })
        assertEquals(2, comments.comments.first().replyCount, "Replies are counted before they are loaded")
        assertEquals(listOf(listOf(STORY), topLevel.take(COMMENTS_PAGE_SIZE)), network.itemRequests)
    }

    @Test
    fun loadMoreComments_loadsTheNextTopLevelPage_thenDoesNothing() = runTest {
        val topLevel = (101L..125L).toList()
        story(kids = topLevel)
        topLevel.forEach { comment(it, parent = STORY) }
        repository.refresh(STORY)

        repository.loadMoreComments(STORY, parentId = STORY)
        repository.loadMoreComments(STORY, parentId = STORY)

        assertEquals(topLevel, topLevelIds())
        assertEquals(topLevel.drop(COMMENTS_PAGE_SIZE), network.itemRequests.last())
        assertEquals(3, network.itemRequests.size, "Nothing is requested once everything is loaded")
    }

    @Test
    fun loadMoreComments_loadsRepliesOnDemand_atAnyDepth() = runTest {
        story(kids = listOf(101))
        comment(101, parent = STORY, kids = listOf(201, 202))
        comment(201, parent = 101, kids = listOf(301))
        comment(202, parent = 101)
        comment(301, parent = 201)
        repository.refresh(STORY)
        assertEquals(listOf(101L), loaded().map { it.id }, "Replies wait until asked for")

        repository.loadMoreComments(STORY, parentId = 101)
        repository.loadMoreComments(STORY, parentId = 201)

        val byId = loaded().associateBy { it.id }
        assertEquals(setOf(101L, 201L, 202L, 301L), byId.keys)
        assertEquals(201L, byId.getValue(301).parentId)
        assertEquals(listOf(1, 0, 0), listOf(201L, 202L, 301L).map { byId.getValue(it).replyCount })
    }

    @Test
    fun refresh_followsTheNewRanking_dropsRemovedComments_andKeepsLoadedOnesBeyondTheFirstPage() = runTest {
        val topLevel = (101L..122L).toList()
        story(kids = topLevel)
        topLevel.forEach { comment(it, parent = STORY) }
        repository.refresh(STORY)
        repository.loadMoreComments(STORY, parentId = STORY)
        // 122 moves to the top, 101 is removed, 102 is edited.
        story(kids = listOf(122L) + (102L..121L))
        comment(102, parent = STORY, text = "Edited")

        repository.refresh(STORY)

        assertEquals(listOf(122L) + (102L..121L), topLevelIds())
        assertEquals("Edited", loaded().single { it.id == 102L }.text)
        assertEquals(21, repository.comments(STORY).first().topLevelCount)
    }

    @Test
    fun aLoadMoreOverlappingARefresh_isDropped_soRemovedCommentsDoNotComeBack() = runTest {
        val topLevel = (101L..125L).toList()
        story(kids = topLevel)
        topLevel.forEach { comment(it, parent = STORY) }
        repository.refresh(STORY)
        // While the next page is being fetched, a refresh removes 125 and moves 124 to the top.
        var refreshed = false
        network.whileFetchingItems = { ids ->
            if (!refreshed && 125L in ids) {
                refreshed = true
                story(kids = listOf(124L) + (101L..123L))
                repository.refresh(STORY)
            }
        }

        repository.loadMoreComments(STORY, parentId = STORY)

        val comments = repository.comments(STORY).first()
        assertEquals(24, comments.topLevelCount)
        assertTrue(comments.comments.none { it.id == 125L }, "The removed comment must not come back")
        // 101..120 were the first page before; the refresh loaded 124 on top. 121..123 come from the dropped page.
        assertEquals(listOf(124L) + (101L..120L), topLevelIds(), "The stale page was dropped")
        repository.loadMoreComments(STORY, parentId = STORY)
        assertEquals(listOf(124L) + (101L..123L), topLevelIds())
    }

    /**
     * Removal in big threads. The host's SQLite allows far more than 999 bound variables, so this cannot reproduce
     * the old-Android limit itself; the chunking that avoids it is checked by reading the code.
     */
    @Test
    fun aRefresh_removesCommentsThatAreGone_inThreadsWithThousandsOfComments() = runTest {
        val topLevel = (100_001L..101_200L).toList()
        story(kids = topLevel)
        comment(topLevel.first(), parent = STORY)
        repository.refresh(STORY)
        story(kids = topLevel.take(1))

        repository.refresh(STORY)

        assertEquals(1, repository.comments(STORY).first().topLevelCount)
    }

    @Test
    fun commentsTheApiCannotGive_areDropped_soCountsStayHonest() = runTest {
        story(kids = listOf(101, 102, 103))
        comment(101, parent = STORY)
        network.items[103] = networkStory(103) // not a comment
        // 102 is unknown to the API (JSON null).

        repository.refresh(STORY)

        val comments = repository.comments(STORY).first()
        assertEquals(1, comments.topLevelCount)
        assertEquals(listOf(101L), comments.comments.map { it.id })
    }

    @Test
    fun deletedAndFlaggedComments_arePlaceholdersWithoutContent_thatKeepTheirReplies() = runTest {
        story(kids = listOf(101, 102))
        network.items[101] = NetworkItem(id = 101, type = "comment", deleted = true, time = 1, kids = listOf(201))
        comment(102, parent = STORY)
        network.items[102] = network.items.getValue(102).copy(dead = true)

        repository.refresh(STORY)

        val (deleted, flagged) = loaded()
        assertEquals(CommentStatus.DELETED, deleted.status)
        assertNull(deleted.author)
        assertNull(deleted.postedAt)
        assertEquals(1, deleted.replyCount)
        assertEquals(CommentStatus.FLAGGED, flagged.status)
        assertEquals("user102", flagged.author)
        assertNull(flagged.text)
    }

    @Test
    fun aFailedFetch_throws_andWritesNothing() = runTest {
        story(kids = listOf(101))
        comment(101, parent = STORY)
        network.whileFetchingItems = { ids -> if (ids != listOf(STORY)) throw IOException("offline") }

        assertFailsWith<IOException> { repository.refresh(STORY) }

        assertNull(repository.story(STORY).first())
        assertTrue(repository.comments(STORY).first().comments.isEmpty())
    }

    @Test
    fun refresh_failsWhenTheStoryIsGone_andKeepsTheCache() = runTest {
        story(kids = emptyList())
        repository.refresh(STORY)
        network.items[STORY] = network.items.getValue(STORY).copy(dead = true)

        assertFailsWith<IOException> { repository.refresh(STORY) }

        assertEquals("Story $STORY", repository.story(STORY).first()!!.title)
    }

    private fun story(kids: List<Long>, text: String? = null) {
        network.items[STORY] = networkStory(STORY).copy(kids = kids, text = text)
    }

    private fun comment(id: Long, parent: Long, kids: List<Long> = emptyList(), text: String = "Comment $id") {
        network.items[id] = NetworkItem(
            id = id,
            type = "comment",
            by = "user$id",
            time = 1_700_000_000 + id,
            text = text,
            parent = parent,
            kids = kids,
        )
    }

    private suspend fun loaded(): List<Comment> = repository.comments(STORY).first().comments

    private suspend fun topLevelIds() = loaded().filter { it.parentId == STORY }.map { it.id }

    private companion object {
        const val STORY = 1L
    }
}
