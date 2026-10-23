package com.emirkuljanin.hnfeed.core.imagegen.ondevice

import com.emirkuljanin.hnfeed.core.common.dispatchers.DefaultDispatcher
import com.emirkuljanin.hnfeed.core.imagegen.GeneratedImage
import com.emirkuljanin.hnfeed.core.imagegen.ImageGenerator
import com.emirkuljanin.hnfeed.core.imagegen.ImageRequest
import com.emirkuljanin.hnfeed.core.imagegen.ImageSource
import com.emirkuljanin.hnfeed.core.imagegen.encode.ThumbnailEncoder
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The always-available generator: embeds the title on device and paints procedural art from the embedding.
 * Works offline, costs nothing, and cannot produce unsafe imagery, which is why it is the last resort for every
 * title and the only path for titles the safety filter keeps away from the cloud.
 */
internal class OnDeviceImageGenerator @Inject constructor(
    private val embedder: TextEmbedder,
    private val artist: ProceduralArtist,
    private val encoder: ThumbnailEncoder,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ImageGenerator {

    override suspend fun canGenerate(request: ImageRequest): Boolean = true

    override suspend fun generate(request: ImageRequest): GeneratedImage {
        val vector = embedder.embed(request.title) ?: ProceduralArtist.vectorFromText(request.title)
        val art = withContext(defaultDispatcher) { artist.draw(vector) }
        val webp = encoder.fromBitmap(art)
        return GeneratedImage(webpBytes = webp, source = ImageSource.ON_DEVICE)
    }
}
