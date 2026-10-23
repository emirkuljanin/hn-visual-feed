package com.emirkuljanin.hnfeed.feature.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** First load with nothing cached yet. */
@Composable
internal fun FeedLoading(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.feature_feed_loading)
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = description })
    }
}

/** First load failed and there is nothing cached to show instead. */
@Composable
internal fun FeedError(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    FeedMessage(
        title = stringResource(R.string.feature_feed_error_title),
        message = stringResource(R.string.feature_feed_error_message),
        modifier = modifier,
    ) {
        Button(onClick = onRetry) { Text(stringResource(R.string.feature_feed_retry)) }
    }
}

/** Loaded successfully, but nothing to show. */
@Composable
internal fun FeedEmpty(onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    FeedMessage(
        title = stringResource(R.string.feature_feed_empty_title),
        message = stringResource(R.string.feature_feed_empty_message),
        modifier = modifier,
    ) {
        OutlinedButton(onClick = onRefresh) { Text(stringResource(R.string.feature_feed_refresh)) }
    }
}

@Composable
private fun FeedMessage(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action()
    }
}

/** A refresh failed while cached stories are on screen: say so without hiding them. */
@Composable
internal fun RefreshFailedBanner(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    InlineError(text = stringResource(R.string.feature_feed_refresh_failed), onRetry = onRetry, modifier = modifier)
}

/** End-of-list footer while the next page loads or after it failed to. */
@Composable
internal fun AppendLoading(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.feature_feed_loading)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = description })
    }
}

@Composable
internal fun AppendFailed(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    InlineError(text = stringResource(R.string.feature_feed_append_failed), onRetry = onRetry, modifier = modifier)
}

@Composable
private fun InlineError(text: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onRetry) { Text(stringResource(R.string.feature_feed_retry)) }
        }
    }
}

@Preview
@Composable
private fun FeedErrorPreview() {
    FeedPreviewTheme { FeedError(onRetry = {}) }
}

@Preview
@Composable
private fun RefreshFailedBannerPreview() {
    FeedPreviewTheme { RefreshFailedBanner(onRetry = {}) }
}
