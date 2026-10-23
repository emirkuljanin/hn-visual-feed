package com.emirkuljanin.hnfeed.feature.story

import com.emirkuljanin.hnfeed.core.model.CommentStatus
import com.emirkuljanin.hnfeed.core.model.StoryComments
import com.emirkuljanin.hnfeed.core.testing.data.testComment
import com.emirkuljanin.hnfeed.core.testing.data.testStoryComments
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CommentListTest {

    @Test
    fun topLevelOnly_whileNothingIsExpanded_withTheRemainderToLoad() {
        val rows = build(testStoryComments)

        // 104 is flagged without replies: hidden. 103 is deleted but has a reply: kept as a placeholder.
        assertEquals(listOf("comment-101", "comment-102", "comment-103", "more-1"), rows.map { it.key })
        val more = assertIs<CommentListItem.MoreComments>(rows.last())
        assertEquals(2, more.count)
        assertTrue(more.isTopLevel)
        assertEquals(LoadState.IDLE, more.state)
    }

    @Test
    fun anExpandedComment_isFollowedByItsLoadedReplies_oneLevelDeeper() {
        val rows = build(testStoryComments, expanded = setOf(101, 201))

        assertEquals(
            listOf("comment-101", "comment-201", "more-201", "comment-202", "comment-102", "comment-103", "more-1"),
            rows.map { it.key },
        )
        assertEquals(listOf(0, 1, 2, 1, 0, 0, 0), rows.map { it.depth })
        val reply = assertIs<CommentListItem.CommentRow>(rows[1])
        assertTrue(reply.isByOriginalPoster, "alice wrote the story")
        assertEquals(Replies(count = 2, isExpanded = true), (rows[0] as CommentListItem.CommentRow).replies)
    }

    @Test
    fun expandingACommentWithNothingLoaded_showsItsLoadingRow() {
        val rows = build(testStoryComments, expanded = setOf(102), loading = setOf(102))

        val more = assertIs<CommentListItem.MoreComments>(rows[rows.indexOfFirst { it.key == "comment-102" } + 1])
        assertEquals(3, more.count)
        assertEquals(1, more.depth)
        assertEquals(LoadState.LOADING, more.state)
    }

    @Test
    fun failedLoads_andRefreshing_showOnTheirRow() {
        assertEquals(LoadState.FAILED, topLevelMore(build(testStoryComments, failed = setOf(1))).state)
        assertEquals(LoadState.LOADING, topLevelMore(build(testStoryComments, isRefreshing = true)).state)
    }

    @Test
    fun aFullyLoadedLevel_hasNoMoreRow() {
        val comments = StoryComments(
            topLevelCount = 1,
            comments = listOf(
                testComment(11, parentId = 1),
                testComment(12, parentId = 1, status = CommentStatus.DELETED),
            ),
        )

        // The deleted comment without replies is hidden but still counts as loaded.
        assertEquals(listOf("comment-11"), build(comments.copy(topLevelCount = 2)).map { it.key })
    }

    @Test
    fun hiddenReplies_areNotCounted_andADeadBranchDisappears() {
        val comments = StoryComments(
            topLevelCount = 3,
            comments = listOf(
                // Its only reply is a deleted leaf: nothing to show.
                testComment(11, parentId = 1, replyCount = 1),
                // Deleted, with only a flagged leaf below: hidden entirely.
                testComment(12, parentId = 1, replyCount = 1, status = CommentStatus.DELETED),
                // Two replies, one of them a hidden leaf; the other is not loaded yet.
                testComment(13, parentId = 1, replyCount = 2),
                testComment(21, parentId = 11, status = CommentStatus.DELETED),
                testComment(22, parentId = 12, status = CommentStatus.FLAGGED),
                testComment(23, parentId = 13, status = CommentStatus.DELETED),
            ),
        )

        val rows = build(comments, expanded = setOf(11, 13))

        assertEquals(listOf("comment-11", "comment-13", "more-13"), rows.map { it.key })
        assertEquals(null, (rows[0] as CommentListItem.CommentRow).replies, "Expanded, but nothing to show")
        assertEquals(Replies(count = 1, isExpanded = true), (rows[1] as CommentListItem.CommentRow).replies)
        assertEquals(1, (rows[2] as CommentListItem.MoreComments).count)
    }

    private fun topLevelMore(rows: List<CommentListItem>) = rows.filterIsInstance<CommentListItem.MoreComments>()
        .single { it.isTopLevel }

    private fun build(
        comments: StoryComments,
        expanded: Set<Long> = emptySet(),
        loading: Set<Long> = emptySet(),
        failed: Set<Long> = emptySet(),
        isRefreshing: Boolean = false,
    ) = buildCommentList(
        storyId = 1,
        storyAuthor = "alice",
        comments = comments,
        expanded = expanded,
        loading = loading,
        failed = failed,
        isRefreshing = isRefreshing,
    )
}
