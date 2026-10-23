package com.emirkuljanin.hnfeed.feature.story

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emirkuljanin.hnfeed.core.model.CommentStatus
import com.emirkuljanin.hnfeed.core.ui.R as CoreUiR
import com.emirkuljanin.hnfeed.core.ui.relativeAge

@Composable
internal fun CommentRow(
    row: CommentListItem.CommentRow,
    onToggleReplies: (Long) -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val comment = row.comment
    ThreadIndent(depth = row.depth, modifier = modifier) {
        Column(modifier = Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CommentHeader(row)
            when (comment.status) {
                CommentStatus.VISIBLE -> comment.text?.let { text ->
                    HtmlText(html = text, onOpenLink = onOpenLink, style = MaterialTheme.typography.bodyMedium)
                }

                CommentStatus.DELETED, CommentStatus.FLAGGED -> Text(
                    text = stringResource(
                        if (comment.status == CommentStatus.DELETED) {
                            R.string.feature_story_deleted
                        } else {
                            R.string.feature_story_flagged
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            row.replies?.let { replies ->
                TextButton(
                    onClick = { onToggleReplies(comment.id) },
                    contentPadding = CompactButtonPadding,
                    modifier = Modifier.offset(x = -ButtonTextInset),
                ) {
                    Text(
                        if (replies.isExpanded) {
                            stringResource(R.string.feature_story_hide_replies)
                        } else {
                            pluralStringResource(R.plurals.feature_story_show_replies, replies.count, replies.count)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentHeader(row: CommentListItem.CommentRow) {
    val comment = row.comment
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        comment.author?.let { author ->
            Text(text = author, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        if (row.isByOriginalPoster) {
            val description = stringResource(R.string.feature_story_original_poster_description)
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.semantics { contentDescription = description },
            ) {
                Text(
                    text = stringResource(R.string.feature_story_original_poster),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
        comment.postedAt?.let { postedAt ->
            Text(
                text = relativeAge(postedAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * More comments to load under a parent. Replies load on tap; top-level comments load by themselves as soon as the
 * row is shown (when the user scrolls to the end of the list), like the feed.
 */
@Composable
internal fun MoreCommentsRow(
    row: CommentListItem.MoreComments,
    onLoadMore: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    if (row.isTopLevel && row.state == LoadState.IDLE) {
        LaunchedEffect(row.parentId, row.state) { currentOnLoadMore(row.parentId) }
    }
    val moreLabel = if (row.isTopLevel) R.plurals.feature_story_more_comments else R.plurals.feature_story_more_replies
    val failedLabel =
        if (row.isTopLevel) R.string.feature_story_comments_failed else R.string.feature_story_replies_failed
    ThreadIndent(depth = row.depth, modifier = modifier) {
        Box(modifier = Modifier.height(48.dp), contentAlignment = Alignment.CenterStart) {
            when (row.state) {
                LoadState.LOADING -> {
                    val description = stringResource(R.string.feature_story_loading_comments)
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .size(20.dp)
                            .semantics { contentDescription = description },
                    )
                }

                LoadState.IDLE -> TextButton(
                    onClick = { onLoadMore(row.parentId) },
                    contentPadding = CompactButtonPadding,
                    modifier = Modifier.offset(x = -ButtonTextInset),
                ) {
                    Text(pluralStringResource(moreLabel, row.count, row.count))
                }

                LoadState.FAILED -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(failedLabel),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = { onLoadMore(row.parentId) }) {
                        Text(stringResource(CoreUiR.string.core_ui_retry))
                    }
                }
            }
        }
    }
}

/**
 * Indents a row by its depth, with a line marking the thread for replies. Indentation stops growing after
 * [MAX_INDENTED_DEPTH] levels so deep threads stay readable on a phone.
 */
@Composable
private fun ThreadIndent(depth: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val indent: Dp = IndentStep * minOf(depth, MAX_INDENTED_DEPTH)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(start = 16.dp + indent, end = 16.dp),
    ) {
        if (depth > 0) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Spacer(Modifier.width(12.dp))
        }
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

private val IndentStep = 12.dp
private const val MAX_INDENTED_DEPTH = 6

/** Text buttons are shifted left by their inner padding, so their label lines up with the comment text. */
private val ButtonTextInset = 12.dp
private val CompactButtonPadding = PaddingValues(horizontal = ButtonTextInset, vertical = 0.dp)
