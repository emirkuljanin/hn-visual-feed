package com.emirkuljanin.hnfeed.core.testing.data

import com.emirkuljanin.hnfeed.core.model.Comment
import com.emirkuljanin.hnfeed.core.model.CommentStatus
import com.emirkuljanin.hnfeed.core.model.StoryComments

fun testComment(
    id: Long,
    parentId: Long,
    author: String = "user$id",
    text: String = "Comment $id",
    replyCount: Int = 0,
    status: CommentStatus = CommentStatus.VISIBLE,
    minutesAgo: Long = 30,
) = Comment(
    id = id,
    parentId = parentId,
    author = author.takeUnless { status == CommentStatus.DELETED },
    text = text.takeIf { status == CommentStatus.VISIBLE },
    postedAt = TEST_NOW.minusSeconds(minutesAgo * 60).takeUnless { status == CommentStatus.DELETED },
    replyCount = replyCount,
    status = status,
)

/**
 * A discussion on `testStories[0]` (by "alice") for UI and screenshot tests: HTML with a link and a code span, a
 * reply by the original poster, a deleted comment kept for its reply, a flagged one, replies not loaded yet and
 * more top-level comments to load.
 */
val testStoryComments: StoryComments = run {
    val story = testStories.first()
    StoryComments(
        topLevelCount = 6,
        comments = listOf(
            testComment(
                id = 101,
                parentId = story.id,
                author = "pg",
                text = "<p>Generating the art on device first is a nice touch.</p>" +
                    "<p>See <a href=\"https://example.com/notes\">my notes</a> on <i>embeddings</i>.</p>",
                replyCount = 2,
                minutesAgo = 90,
            ),
            testComment(id = 102, parentId = story.id, text = "How big is the APK?", replyCount = 3, minutesAgo = 75),
            testComment(id = 103, parentId = story.id, replyCount = 1, status = CommentStatus.DELETED),
            testComment(id = 104, parentId = story.id, status = CommentStatus.FLAGGED, replyCount = 0),
            testComment(
                id = 201,
                parentId = 101,
                author = "alice",
                text = "Thanks! The cloud images come later.",
                replyCount = 1,
            ),
            testComment(id = 202, parentId = 101, text = "Agreed.", minutesAgo = 10),
            testComment(id = 203, parentId = 103, text = "Replying to a deleted comment.", minutesAgo = 5),
        ),
    )
}
