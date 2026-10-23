package com.emirkuljanin.hnfeed.core.ui

import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.core.graphics.createBitmap
import coil3.Image
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.emirkuljanin.hnfeed.core.designsystem.theme.HnFeedTheme
import com.emirkuljanin.hnfeed.core.designsystem.time.LocalClock
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random

/** "Now" in previews and screenshot tests. */
val PREVIEW_NOW: Instant = Instant.parse("2026-10-23T12:00:00Z")

/**
 * The app theme for previews, screenshot and UI tests: no dynamic color, a fixed clock ([now]) and drawn stand-ins
 * for thumbnails (Coil's preview handler, which needs inspection mode, so tests get it too), so output is the same
 * on every machine and day.
 */
@OptIn(ExperimentalCoilApi::class)
@Composable
fun PreviewTheme(darkTheme: Boolean = false, now: Instant = PREVIEW_NOW, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalClock provides Clock.fixed(now, ZoneOffset.UTC),
        LocalInspectionMode provides true,
        LocalAsyncImagePreviewHandler provides previewThumbnails,
    ) {
        HnFeedTheme(darkTheme = darkTheme, dynamicColor = false, content = content)
    }
}

/** A gradient whose colours follow the file path, so no files are needed and every card looks different. */
@OptIn(ExperimentalCoilApi::class)
private val previewThumbnails = AsyncImagePreviewHandler { request ->
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
