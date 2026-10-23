package com.emirkuljanin.hnfeed.core.database.dao

import com.emirkuljanin.hnfeed.core.database.DatabaseTest
import com.emirkuljanin.hnfeed.core.database.model.CommentEntity
import com.emirkuljanin.hnfeed.core.database.model.CommentSlot
import com.emirkuljanin.hnfeed.core.database.model.asExternalModel
import com.emirkuljanin.hnfeed.core.database.storyEntity
import com.emirkuljanin.hnfeed.core.model.CommentStatus
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

internal class CommentDaoTest : DatabaseTest() {

    private val dao get() = database.commentDao()

    @BeforeTest
    fun insertStory() = runTest {
        database.storyDao().upsert(listOf(storyEntity(STORY)))
    }

    @Test
    fun insertStub_addsAnUnloadedRow_butNeverOverwritesALoadedOne() = runTest {
        dao.upsert(listOf(loaded(11, parent = STORY, rank = 0, text = "Loaded")))

        dao.insertStub(11, STORY, STORY, rank = 5)
        dao.insertStub(12, STORY, STORY, rank = 1)

        assertEquals(listOf(CommentSlot(12, 1)), dao.unloadedChildren(STORY, limit = 10))
        assertEquals("Loaded", dao.observeLoaded(STORY).first().single().comment.text)
    }

    @Test
    fun move_reRanksWithoutTouchingContent() = runTest {
        dao.upsert(listOf(loaded(11, parent = STORY, rank = 0, text = "Kept")))

        dao.move(11, STORY, rank = 3)

        val row = dao.observeLoaded(STORY).first().single().comment
        assertEquals(3, row.rank)
        assertEquals("Kept", row.text)
    }

    @Test
    fun children_andUnloadedChildren_comeInRankOrder_limited() = runTest {
        dao.insertStub(13, STORY, STORY, rank = 2)
        dao.insertStub(11, STORY, STORY, rank = 0)
        dao.upsert(listOf(loaded(12, parent = STORY, rank = 1)))

        assertEquals(listOf(11L, 12L), dao.children(STORY, limit = 2).map { it.id })
        assertEquals(listOf(11L, 13L), dao.unloadedChildren(STORY, limit = 10).map { it.id })
    }

    @Test
    fun childIds_areTheDirectChildrenOnly() = runTest {
        dao.upsert(listOf(loaded(11, parent = STORY, rank = 0)))
        dao.insertStub(12, STORY, STORY, rank = 1)
        dao.insertStub(21, STORY, 11, rank = 0)

        assertEquals(setOf(11L, 12L), dao.childIds(STORY).toSet())
        assertEquals(listOf(21L), dao.childIds(11))
    }

    @Test
    fun delete_removesOnlyTheGivenComments() = runTest {
        dao.insertStub(11, STORY, STORY, rank = 0)
        dao.insertStub(12, STORY, STORY, rank = 1)

        dao.delete(listOf(12))

        assertEquals(listOf(11L), dao.childIds(STORY))
    }

    @Test
    fun observeLoaded_givesEveryLoadedCommentWithItsReplyCount_groupedByParentInRankOrder() = runTest {
        dao.upsert(
            listOf(
                loaded(12, parent = STORY, rank = 1),
                loaded(11, parent = STORY, rank = 0),
                loaded(21, parent = 11, rank = 0, deleted = true),
            ),
        )
        dao.insertStub(22, STORY, 11, rank = 1)

        val rows = dao.observeLoaded(STORY).first()

        assertEquals(listOf(11L, 12L, 21L), rows.map { it.comment.id })
        assertEquals(listOf(2, 0, 0), rows.map { it.replyCount })
        assertEquals(CommentStatus.DELETED, rows.last().asExternalModel().status)
    }

    @Test
    fun observeTopLevelCount_countsLoadedAndUnloadedTopLevelComments() = runTest {
        dao.upsert(listOf(loaded(11, parent = STORY, rank = 0)))
        dao.insertStub(12, STORY, STORY, rank = 1)
        dao.insertStub(21, STORY, 11, rank = 0)

        assertEquals(2, dao.observeTopLevelCount(STORY).first())
    }

    @Test
    fun deletingTheStory_deletesItsComments() = runTest {
        dao.upsert(listOf(loaded(11, parent = STORY, rank = 0)))
        dao.insertStub(21, STORY, 11, rank = 0)

        database.storyDao().delete(listOf(STORY))

        assertTrue(dao.children(STORY, limit = 10).isEmpty())
        assertTrue(dao.children(11, limit = 10).isEmpty())
    }

    private fun loaded(id: Long, parent: Long, rank: Int, text: String = "Comment $id", deleted: Boolean = false) =
        CommentEntity(
            id = id,
            storyId = STORY,
            parentId = parent,
            rank = rank,
            isLoaded = true,
            author = if (deleted) null else "user$id",
            text = if (deleted) null else text,
            postedAt = if (deleted) null else Instant.ofEpochSecond(1_800_000_000 + id),
            deleted = deleted,
            dead = false,
        )

    private companion object {
        const val STORY = 1L
    }
}
