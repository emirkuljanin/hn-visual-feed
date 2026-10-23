package com.emirkuljanin.hnfeed.core.imagegen.ondevice

/**
 * Turns text into a semantic vector: similar titles give nearby vectors, which is what makes on-device thumbnails
 * of related stories look related.
 */
internal interface TextEmbedder {

    /** Main-safe. `null` if no model is available on this device (the caller then derives art from the text alone). */
    suspend fun embed(text: String): FloatArray?
}
