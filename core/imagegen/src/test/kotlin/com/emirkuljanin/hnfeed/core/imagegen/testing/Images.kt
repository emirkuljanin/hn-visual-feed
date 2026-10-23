package com.emirkuljanin.hnfeed.core.imagegen.testing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import java.io.ByteArrayOutputStream

/** A [width] x [height] JPEG: left half red, right half blue, so cropping and scaling are observable. */
internal fun splitColourJpeg(width: Int, height: Int): ByteArray {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).apply {
        drawColor(Color.RED)
        clipRect(width / 2, 0, width, height)
        drawColor(Color.BLUE)
    }
    return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }.toByteArray()
}

internal fun decode(bytes: ByteArray): Bitmap =
    checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) { "Not a decodable image" }

/** WebP files start with "RIFF....WEBP". */
internal fun isWebp(bytes: ByteArray): Boolean = bytes.size > 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
    String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
