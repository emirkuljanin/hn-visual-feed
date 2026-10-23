package com.emirkuljanin.hnfeed.feature.feed

import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.graphics.createBitmap
import coil3.Image
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.emirkuljanin.hnfeed.core.designsystem.theme.HnFeedTheme
import com.emirkuljanin.hnfeed.core.designsystem.time.LocalClock
import com.emirkuljanin.hnfeed.core.model.Story
import com.emirkuljanin.hnfeed.core.model.StoryType
import com.emirkuljanin.hnfeed.core.model.Thumbnail
import com.emirkuljanin.hnfeed.core.model.ThumbnailSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random

private val PREVIEW_NOW: Instant = Instant.parse("2026-10-23T12:00:00Z")

/**
 * Theme without dynamic color, a fixed clock and drawn stand-ins for thumbnails, so previews look the same on every
 * machine and day.
 */
@OptIn(ExperimentalCoilApi::class)
@Composable
internal fun FeedPreviewTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalClock provides Clock.fixed(PREVIEW_NOW, ZoneOffset.UTC),
        LocalAsyncImagePreviewHandler provides previewThumbnails,
    ) {
        HnFeedTheme(dynamicColor = false, content = content)
    }
}

/**
 * Stands in for generated thumbnails wherever Coil runs in inspection mode (previews, screenshot and UI tests): a
 * gradient whose colours follow the file path, so no files are needed and every card looks different.
 */
@OptIn(ExperimentalCoilApi::class)
internal val previewThumbnails = AsyncImagePreviewHandler { request ->
    gradientImage(seed = request.data.toString().hashCode())
}

private fun gradientImage(seed: Int): Image {
    val hue = Random(seed).nextFloat() * 360f
    val start = AndroidColor.HSVToColor(floatArrayOf(hue, 0.45f, 0.9f))
    val end = AndroidColor.HSVToColor(floatArrayOf((hue + 70f) % 360f, 0.6f, 0.55f))
    val width = PREVIEW_IMAGE_WIDTH.toFloat()
    val height = PREVIEW_IMAGE_HEIGHT.toFloat()
    val bitmap = createBitmap(PREVIEW_IMAGE_WIDTH, PREVIEW_IMAGE_HEIGHT)
    val paint = Paint().apply { shader = LinearGradient(0f, 0f, width, height, start, end, Shader.TileMode.CLAMP) }
    Canvas(bitmap).drawPaint(paint)
    return bitmap.asImage()
}

private const val PREVIEW_IMAGE_WIDTH = 160
private const val PREVIEW_IMAGE_HEIGHT = 90

internal val previewStories: List<Story> = listOf(
    Story(
        id = 1,
        title = "Show HN: I built a tiny Hacker News reader that generates a picture for every story",
        author = "alice",
        score = 412,
        postedAt = PREVIEW_NOW.minusSeconds(2 * 3_600),
        url = "https://github.com/example/hn-visual-feed",
        commentCount = 128,
        type = StoryType.STORY,
        thumbnail = Thumbnail(filePath = "/preview/1-cloud.webp", source = ThumbnailSource.CLOUD),
    ),
    Story(
        id = 2,
        title = "Acme (YC W26) is hiring Android engineers in Oslo",
        author = "acme",
        score = 1,
        postedAt = PREVIEW_NOW.minusSeconds(26 * 3_600),
        url = "https://www.ycombinator.com/companies/example/jobs",
        commentCount = 0,
        type = StoryType.JOB,
    ),
)
