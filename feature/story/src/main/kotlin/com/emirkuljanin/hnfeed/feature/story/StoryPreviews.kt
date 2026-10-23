package com.emirkuljanin.hnfeed.feature.story

import com.emirkuljanin.hnfeed.core.model.Comment
import com.emirkuljanin.hnfeed.core.model.CommentStatus
import com.emirkuljanin.hnfeed.core.model.Story
import com.emirkuljanin.hnfeed.core.model.StoryType
import com.emirkuljanin.hnfeed.core.model.Thumbnail
import com.emirkuljanin.hnfeed.core.model.ThumbnailSource
import com.emirkuljanin.hnfeed.core.ui.PREVIEW_NOW

private val previewStory = Story(
    id = 1,
    title = "Show HN: I built a tiny Hacker News reader that generates a picture for every story",
    author = "alice",
    score = 412,
    postedAt = PREVIEW_NOW.minusSeconds(2 * 3_600),
    url = "https://github.com/example/hn-visual-feed",
    commentCount = 5,
    type = StoryType.STORY,
    thumbnail = Thumbnail(filePath = "/preview/1-cloud.webp", source = ThumbnailSource.CLOUD),
)

private fun previewComment(id: Long, parentId: Long, author: String, text: String, replyCount: Int = 0) = Comment(
    id = id,
    parentId = parentId,
    author = author,
    text = text,
    postedAt = PREVIEW_NOW.minusSeconds(id * 60),
    replyCount = replyCount,
    status = CommentStatus.VISIBLE,
)

internal val previewUiState = StoryUiState(
    story = previewStory,
    comments = listOf(
        CommentListItem.CommentRow(
            comment = previewComment(11, 1, "pg", "<p>Nice idea.</p><p>How do you pick the colours?</p>", 1),
            depth = 0,
            isByOriginalPoster = false,
            replies = Replies(count = 1, isExpanded = true),
        ),
        CommentListItem.CommentRow(
            comment = previewComment(21, 11, "alice", "From a sentence embedding of the title."),
            depth = 1,
            isByOriginalPoster = true,
            replies = null,
        ),
        CommentListItem.CommentRow(
            comment = previewComment(12, 1, "dang", "Interesting use of on-device models.", 3),
            depth = 0,
            isByOriginalPoster = false,
            replies = Replies(count = 3, isExpanded = false),
        ),
        CommentListItem.MoreComments(parentId = 1, depth = 0, count = 2, isTopLevel = true, state = LoadState.LOADING),
    ),
    isRefreshing = false,
)
