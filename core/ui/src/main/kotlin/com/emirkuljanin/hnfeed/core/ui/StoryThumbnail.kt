package com.emirkuljanin.hnfeed.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.emirkuljanin.hnfeed.core.model.Thumbnail

/**
 * The generated thumbnail over the placeholder, cross-fading in once decoded. When a preview is upgraded the file
 * path changes; the request then shows the image already on screen (by its memory cache key) until the new one is
 * ready, instead of flashing back to the placeholder.
 */
@Composable
fun StoryThumbnail(thumbnail: Thumbnail?, modifier: Modifier = Modifier) {
    Box(modifier) {
        ThumbnailPlaceholder(Modifier.matchParentSize())
        if (thumbnail != null) {
            val context = LocalPlatformContext.current
            var shownKey by remember { mutableStateOf<MemoryCache.Key?>(null) }
            val request = remember(thumbnail.filePath) {
                ImageRequest.Builder(context)
                    .data(thumbnail.filePath)
                    .placeholderMemoryCacheKey(shownKey)
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { shownKey = it.result.memoryCacheKey },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** Shown until the thumbnail is ready, and kept if it never is. */
@Composable
private fun ThumbnailPlaceholder(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier.background(Brush.linearGradient(listOf(colors.surfaceVariant, colors.secondaryContainer))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.core_ui_ic_image),
            contentDescription = null,
            tint = colors.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(40.dp),
        )
    }
}
