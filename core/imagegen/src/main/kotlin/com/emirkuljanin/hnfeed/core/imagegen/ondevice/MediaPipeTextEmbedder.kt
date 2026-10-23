package com.emirkuljanin.hnfeed.core.imagegen.ondevice

import android.content.Context
import android.util.Log
import com.emirkuljanin.hnfeed.core.common.dispatchers.DefaultDispatcher
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder as MediaPipeEmbedder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Google's Universal Sentence Encoder (6 MB TFLite, bundled in assets) run through MediaPipe Tasks. Inference runs
 * fully on device (MediaPipe itself may send anonymous usage statistics to Google; see the README).
 *
 * The model is loaded on first use (not at injection) and kept for the process. MediaPipe tasks are not
 * documented as thread-safe, so calls are serialised; inference takes milliseconds per title, far below the cost
 * of drawing and encoding the image. Failures to load or run the model that surface in Java (unsupported ABI,
 * classes stripped by a misconfigured release build, runtime errors) are logged and degrade to `null` instead of
 * throwing, and the caller draws from the text alone. A crash inside MediaPipe's native code is not catchable from
 * Java; the release-build check in the app guards against that.
 */
@Singleton
internal class MediaPipeTextEmbedder @Inject constructor(
    @ApplicationContext private val context: Context,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : TextEmbedder {

    private val mutex = Mutex()
    private var embedder: MediaPipeEmbedder? = null
    private var unavailable = false

    override suspend fun embed(text: String): FloatArray? = withContext(defaultDispatcher) {
        mutex.withLock {
            val embedder = embedderOrNull() ?: return@withLock null
            try {
                embedder.embed(text).embeddingResult().embeddings().first().floatEmbedding()
            } catch (e: RuntimeException) {
                Log.w(TAG, "Embedding failed; using text-hash art for this title", e)
                null
            } catch (e: LinkageError) {
                // A JNI lookup failing on first inference will fail every time: stop using the model.
                disable(e)
                null
            }
        }
    }

    private fun embedderOrNull(): MediaPipeEmbedder? {
        if (unavailable) return null
        embedder?.let { return it }
        return try {
            MediaPipeEmbedder.createFromOptions(
                context,
                MediaPipeEmbedder.TextEmbedderOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build())
                    .setL2Normalize(true)
                    .build(),
            ).also { embedder = it }
        } catch (e: RuntimeException) {
            disable(e)
        } catch (e: LinkageError) {
            // UnsatisfiedLinkError (no native library for this ABI), NoSuchFieldError/NoSuchMethodError (JNI lookups
            // failing because R8 stripped classes).
            disable(e)
        }
    }

    private fun disable(cause: Throwable): MediaPipeEmbedder? {
        unavailable = true
        Log.w(TAG, "Text embedder unavailable; on-device thumbnails will use text-hash art", cause)
        return null
    }

    private companion object {
        const val TAG = "MediaPipeTextEmbedder"
        const val MODEL_ASSET = "universal_sentence_encoder.tflite"
    }
}
