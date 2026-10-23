package com.emirkuljanin.hnfeed.core.imagegen.encode

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import androidx.core.graphics.createBitmap
import com.emirkuljanin.hnfeed.core.common.dispatchers.DefaultDispatcher
import com.emirkuljanin.hnfeed.core.imagegen.THUMBNAIL_HEIGHT
import com.emirkuljanin.hnfeed.core.imagegen.THUMBNAIL_WIDTH
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Normalises every generator's output to the same thumbnail: [THUMBNAIL_WIDTH] x [THUMBNAIL_HEIGHT] WebP.
 * One format and size keeps storage small and the feed uniform. Main-safe: decoding and compressing run on the
 * injected CPU dispatcher.
 */
internal class ThumbnailEncoder @Inject constructor(
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {

    /**
     * Decodes [encoded] (any format the platform reads), centre-crops it to 16:9, scales it down and re-encodes.
     * Re-encoding also validates the bytes: an error page or truncated download fails here, not in the UI.
     */
    suspend fun fromEncodedImage(encoded: ByteArray): ByteArray = withContext(defaultDispatcher) {
        val source = decodeSubsampled(encoded)
        try {
            val thumbnail = cropAndScale(source)
            try {
                compress(thumbnail)
            } finally {
                thumbnail.recycle()
            }
        } finally {
            source.recycle()
        }
    }

    /** Encodes a bitmap that is already thumbnail-sized (on-device art), and recycles it. */
    suspend fun fromBitmap(bitmap: Bitmap): ByteArray = withContext(defaultDispatcher) {
        try {
            require(bitmap.width == THUMBNAIL_WIDTH && bitmap.height == THUMBNAIL_HEIGHT) {
                "Expected ${THUMBNAIL_WIDTH}x$THUMBNAIL_HEIGHT, got ${bitmap.width}x${bitmap.height}"
            }
            compress(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Decodes at the largest power-of-two reduction that still covers the thumbnail: a 1024 px model output decodes
     * at 512 px, a quarter of the memory, and the final downscale aliases less.
     */
    private fun decodeSubsampled(encoded: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(encoded, 0, encoded.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("Response is not a decodable image (${encoded.size} bytes)")
        }
        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= THUMBNAIL_WIDTH &&
            bounds.outHeight / (sampleSize * 2) >= THUMBNAIL_HEIGHT
        ) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeByteArray(
            encoded,
            0,
            encoded.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: throw IOException("Could not decode image (${encoded.size} bytes)")
    }

    private fun cropAndScale(source: Bitmap): Bitmap {
        val targetRatio = THUMBNAIL_WIDTH.toFloat() / THUMBNAIL_HEIGHT
        val crop = if (source.width.toFloat() / source.height > targetRatio) {
            val width = (source.height * targetRatio).toInt()
            Rect((source.width - width) / 2, 0, (source.width + width) / 2, source.height)
        } else {
            val height = (source.width / targetRatio).toInt()
            Rect(0, (source.height - height) / 2, source.width, (source.height + height) / 2)
        }
        val thumbnail = createBitmap(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
        Canvas(thumbnail).drawBitmap(
            source,
            crop,
            Rect(0, 0, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        return thumbnail
    }

    private fun compress(bitmap: Bitmap): ByteArray {
        val output = ByteArrayOutputStream()
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        if (!bitmap.compress(format, WEBP_QUALITY, output)) throw IOException("Could not encode thumbnail")
        return output.toByteArray()
    }

    private companion object {
        const val WEBP_QUALITY = 80
    }
}
