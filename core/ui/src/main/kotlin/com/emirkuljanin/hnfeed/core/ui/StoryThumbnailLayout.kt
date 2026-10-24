package com.emirkuljanin.hnfeed.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.MultiContentMeasurePolicy
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * At least this wide (a phone in landscape, a tablet), a story's thumbnail goes beside its text instead of above it:
 * a full-width 16:9 image would fill the screen and push the title below the fold. 600 dp is where Material's
 * medium window size class starts; cards on phones in portrait are narrower.
 */
private val WideMinWidth = 600.dp

/** The share of the width that a wide layout's thumbnail takes: about as tall as the text beside it. */
private const val WIDE_THUMBNAIL_FRACTION = 1f / 3

private const val THUMBNAIL_ASPECT_RATIO = 16f / 9f

/**
 * A story's 16:9 [thumbnail] above its [details] (feed card, story header), or at the start beside them when the
 * layout is wide: at least [WideMinWidth], or in a short window, where the full-width image would be taller than
 * half the window (a compact phone in landscape, whose cards are under 600 dp once the camera cutout and the side
 * padding are taken off; split screen). Phones in portrait stay narrow: their image is about a quarter of the window.
 * Decided while measuring, from the layout's own width and the window height: both shapes are one
 * composition (nothing is recreated when the width crosses the line), and no list item needs the subcomposition of
 * a `BoxWithConstraints`. The narrow shape measures exactly like `fillMaxWidth().aspectRatio(16f / 9f)` above the
 * details in a column, and the thumbnail is 16:9 in both, so the shared-element transition between screens stays
 * a plain resize.
 */
@Composable
fun StoryThumbnailLayout(
    thumbnail: @Composable () -> Unit,
    details: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val measurePolicy = remember(windowHeight) { StoryThumbnailMeasurePolicy(windowHeight) }
    Layout(contents = listOf(thumbnail, details), modifier = modifier, measurePolicy = measurePolicy)
}

/**
 * Measures [StoryThumbnailLayout], including its intrinsic sizes, so it works in parents that ask for them
 * (`Modifier.height(IntrinsicSize.Min)`, intrinsic-width rows): the default intrinsics would measure the thumbnail
 * box at its own size instead of 16:9, and with an unbounded width.
 */
private class StoryThumbnailMeasurePolicy(private val windowHeight: Int) : MultiContentMeasurePolicy {

    override fun MeasureScope.measure(measurables: List<List<Measurable>>, constraints: Constraints): MeasureResult {
        require(constraints.hasBoundedWidth) { "A story thumbnail layout needs a bounded width" }
        val (thumbnails, detailsList) = measurables
        val width = constraints.maxWidth
        val shape = shape(width)
        val thumbnailPlaceable = thumbnails.single().measure(
            Constraints.fixed(shape.thumbnailWidth, shape.thumbnailHeight),
        )
        val detailsPlaceable = detailsList.single().measure(Constraints(maxWidth = shape.detailsWidth))
        return layout(width, shape.height(detailsPlaceable.height)) {
            thumbnailPlaceable.placeRelative(0, 0)
            when {
                shape.wide -> detailsPlaceable.placeRelative(shape.thumbnailWidth, 0)
                else -> detailsPlaceable.placeRelative(0, shape.thumbnailHeight)
            }
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<List<IntrinsicMeasurable>>, width: Int) =
        intrinsicHeight(measurables, width) { details, detailsWidth -> details.minIntrinsicHeight(detailsWidth) }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<List<IntrinsicMeasurable>>, width: Int) =
        intrinsicHeight(measurables, width) { details, detailsWidth -> details.maxIntrinsicHeight(detailsWidth) }

    /** The details' width: the thumbnail takes whatever width the layout gets. */
    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<List<IntrinsicMeasurable>>, height: Int) =
        measurables[1].single().minIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<List<IntrinsicMeasurable>>, height: Int) =
        measurables[1].single().maxIntrinsicWidth(height)

    private fun IntrinsicMeasureScope.intrinsicHeight(
        measurables: List<List<IntrinsicMeasurable>>,
        width: Int,
        detailsHeight: (IntrinsicMeasurable, Int) -> Int,
    ): Int {
        val details = measurables[1].single()
        // Without a width the thumbnail has no height either.
        if (width == Constraints.Infinity) return detailsHeight(details, width)
        val shape = shape(width)
        return shape.height(detailsHeight(details, shape.detailsWidth))
    }

    private fun Density.shape(width: Int): Shape {
        val wide = width >= WideMinWidth.roundToPx() ||
            // windowHeight is 0 before the window is measured; then only the width decides.
            (windowHeight > 0 && thumbnailHeight(width) * 2 > windowHeight)
        val thumbnailWidth = if (wide) (width * WIDE_THUMBNAIL_FRACTION).roundToInt() else width
        return Shape(
            wide = wide,
            thumbnailWidth = thumbnailWidth,
            thumbnailHeight = thumbnailHeight(thumbnailWidth),
            detailsWidth = if (wide) width - thumbnailWidth else width,
        )
    }
}

/** Where the thumbnail and the details go for a given width. */
private class Shape(val wide: Boolean, val thumbnailWidth: Int, val thumbnailHeight: Int, val detailsWidth: Int) {
    fun height(detailsHeight: Int): Int = if (wide) {
        maxOf(thumbnailHeight, detailsHeight)
    } else {
        thumbnailHeight +
            detailsHeight
    }
}

private fun thumbnailHeight(width: Int): Int = (width / THUMBNAIL_ASPECT_RATIO).roundToInt()
