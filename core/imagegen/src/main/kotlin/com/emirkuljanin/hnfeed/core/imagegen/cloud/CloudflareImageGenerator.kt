package com.emirkuljanin.hnfeed.core.imagegen.cloud

import com.emirkuljanin.hnfeed.core.common.dispatchers.DefaultDispatcher
import com.emirkuljanin.hnfeed.core.common.dispatchers.IoDispatcher
import com.emirkuljanin.hnfeed.core.imagegen.GeneratedImage
import com.emirkuljanin.hnfeed.core.imagegen.ImageGenerator
import com.emirkuljanin.hnfeed.core.imagegen.ImageRequest
import com.emirkuljanin.hnfeed.core.imagegen.ImageSource
import com.emirkuljanin.hnfeed.core.imagegen.PermanentGenerationException
import com.emirkuljanin.hnfeed.core.imagegen.encode.ThumbnailEncoder
import com.emirkuljanin.hnfeed.core.imagegen.safety.TitleSafetyFilter
import java.io.IOException
import java.util.Base64
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.coroutines.executeAsync

/**
 * Text-to-image in the cloud: FLUX.1 [schnell] on Cloudflare Workers AI. Only used when credentials are configured
 * and the title passes the safety filter. Cloudflare also screens prompts itself and answers HTTP 400 ("Input
 * prompt contains NSFW content") for ones it rejects; like any failure, that falls back to the on-device image.
 * The model accepts no seed, so regenerating a story gives a different (equally valid) picture.
 *
 * Only the HTTP exchange runs on the IO dispatcher; parsing the ~200 KB response, base64 and image work run on the
 * CPU dispatcher.
 *
 * Note: an API token compiled into an app can be extracted. Acceptable for this demo (free tier, own account);
 * a production app would call its own backend, which holds the key and can rate-limit and moderate.
 */
internal class CloudflareImageGenerator @Inject constructor(
    private val config: CloudflareConfig,
    @CloudflareHttpClient private val httpClient: OkHttpClient,
    private val safetyFilter: TitleSafetyFilter,
    private val encoder: ThumbnailEncoder,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ImageGenerator {

    override suspend fun canGenerate(request: ImageRequest): Boolean =
        config.isConfigured && safetyFilter.allowsCloudGeneration(request.title)

    override suspend fun generate(request: ImageRequest): GeneratedImage {
        val responseBody =
            post(json.encodeToString(FluxRequest.serializer(), FluxRequest(CloudPrompt.forTitle(request.title), STEPS)))
        val image = withContext(defaultDispatcher) { decodeImage(responseBody) }
        return GeneratedImage(webpBytes = encoder.fromEncodedImage(image), source = ImageSource.CLOUD)
    }

    private suspend fun post(body: String): String = withContext(ioDispatcher) {
        val call = httpClient.newCall(
            Request.Builder()
                .url("${config.baseUrl}accounts/${config.accountId}/ai/run/$MODEL")
                .header("Authorization", "Bearer ${config.apiToken}")
                .post(body.toRequestBody(JSON))
                .build(),
        )
        // executeAsync cancels the HTTP call when the coroutine is cancelled.
        val (code, responseBody) = call.executeAsync().use { response -> response.code to response.body.string() }
        if (code !in 200..299) {
            // Error bodies use the same envelope; their messages (auth, quota, moderation) are worth keeping.
            val reason = parseOrNull(responseBody)?.errors?.joinToString { it.message }?.takeIf { it.isNotBlank() }
            val message = "Cloudflare returned HTTP $code" + reason?.let { ": $it" }.orEmpty()
            // 4xx (moderation, auth, bad input) repeats on retry; 408/429 and 5xx may not.
            throw if (code in 400..499 && code != 408 && code != 429) {
                PermanentGenerationException(message)
            } else {
                IOException(message)
            }
        }
        responseBody
    }

    private fun decodeImage(responseBody: String): ByteArray {
        val response = parseOrNull(responseBody) ?: throw IOException("Malformed Cloudflare response")
        val base64 = response.result?.image
        if (!response.success || base64 == null) {
            throw IOException("Cloudflare generation failed: ${response.errors.joinToString { it.message }}")
        }
        return try {
            Base64.getDecoder().decode(base64)
        } catch (e: IllegalArgumentException) {
            throw IOException("Image is not valid base64", e)
        }
    }

    private fun parseOrNull(body: String): FluxResponse? = try {
        json.decodeFromString(FluxResponse.serializer(), body)
    } catch (e: SerializationException) {
        null
    }

    /** The model's input schema rejects unknown fields (a `seed` gives HTTP 400). */
    @Serializable
    private data class FluxRequest(val prompt: String, val steps: Int)

    @Serializable
    private data class FluxResponse(
        val success: Boolean = false,
        val result: FluxResult? = null,
        val errors: List<CloudflareError> = emptyList(),
    )

    @Serializable
    private data class FluxResult(val image: String? = null)

    @Serializable
    private data class CloudflareError(val message: String = "")

    private companion object {
        const val MODEL = "@cf/black-forest-labs/flux-1-schnell"

        /** The model's default; its maximum is 8. Each step costs extra neurons for little gain at thumbnail size. */
        const val STEPS = 4
        val JSON = "application/json".toMediaType()
        val json = Json { ignoreUnknownKeys = true }
    }
}
