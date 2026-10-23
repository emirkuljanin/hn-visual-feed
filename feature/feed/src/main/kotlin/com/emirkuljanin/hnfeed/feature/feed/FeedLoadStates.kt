package com.emirkuljanin.hnfeed.feature.feed

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.emirkuljanin.hnfeed.core.ui.InlineError
import com.emirkuljanin.hnfeed.core.ui.LoadingIndicator
import com.emirkuljanin.hnfeed.core.ui.MessageScreen
import com.emirkuljanin.hnfeed.core.ui.PreviewTheme
import com.emirkuljanin.hnfeed.core.ui.R as CoreUiR

/** First load with nothing cached yet. */
@Composable
internal fun FeedLoading(modifier: Modifier = Modifier) {
    LoadingIndicator(description = stringResource(R.string.feature_feed_loading), modifier = modifier.fillMaxSize())
}

/** First load failed and there is nothing cached to show instead. */
@Composable
internal fun FeedError(isOffline: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    MessageScreen(
        title = stringResource(
            if (isOffline) CoreUiR.string.core_ui_offline_title else R.string.feature_feed_error_title,
        ),
        message = stringResource(
            if (isOffline) CoreUiR.string.core_ui_offline_message else R.string.feature_feed_error_message,
        ),
        modifier = modifier,
    ) {
        Button(onClick = onRetry) { Text(stringResource(CoreUiR.string.core_ui_retry)) }
    }
}

/** Loaded successfully, but nothing to show. */
@Composable
internal fun FeedEmpty(onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    MessageScreen(
        title = stringResource(R.string.feature_feed_empty_title),
        message = stringResource(R.string.feature_feed_empty_message),
        modifier = modifier,
    ) {
        OutlinedButton(onClick = onRefresh) { Text(stringResource(R.string.feature_feed_refresh)) }
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
    LoadingIndicator(
        description = stringResource(R.string.feature_feed_loading),
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
    )
}

@Composable
internal fun AppendFailed(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    InlineError(text = stringResource(R.string.feature_feed_append_failed), onRetry = onRetry, modifier = modifier)
}

@Preview
@Composable
private fun FeedErrorPreview() {
    PreviewTheme { FeedError(isOffline = false, onRetry = {}) }
}

@Preview
@Composable
private fun RefreshFailedBannerPreview() {
    PreviewTheme { RefreshFailedBanner(onRetry = {}) }
}
