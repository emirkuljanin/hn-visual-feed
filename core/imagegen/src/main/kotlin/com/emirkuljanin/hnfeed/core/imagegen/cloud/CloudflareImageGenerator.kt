package com.emirkuljanin.hnfeed.core.imagegen.cloud

import com.emirkuljanin.hnfeed.core.common.dispatchers.DefaultDispatcher
import com.emirkuljanin.hnfeed.core.common.dispatchers.IoDispatcher
import com.emirkuljanin.hnfeed.core.imagegen.GeneratedImage
import com.emirkuljanin.hnfeed.core.imagegen.ImageGenerator
import com.emirkuljanin.hnfeed.core.imagegen.ImageRequest
import com.emirkuljanin.hnfeed.core.imagegen.ImageSource
import com.emirkuljanin.hnfeed.core.imagegen.PermanentGenerationException
import com.emirkuljanin.hnfeed.core.imagegen.ProviderMisconfiguredException
import com.emirkuljanin.hnfeed.core.imagegen.ProviderUnavailableException
import com.emirkuljanin.hnfeed.core.imagegen.QuotaExceededException
import com.emirkuljanin.hnfeed.core.imagegen.encode.ThumbnailEncoder
import com.emirkuljanin.hnfeed.core.imagegen.safety.TitleSafetyFilter
import java.io.IOException
import java.io.InterruptedIOException
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
import okhttp3.internal.http2.ErrorCode
import okhttp3.internal.http2.StreamResetException

/**
 * Text-to-image in the cloud: FLUX.1 [schnell] on Cloudflare Workers AI. Only used when credentials are configured
 * and the title passes the safety filter. Cloudflare also screens prompts itself and answers HTTP 400 ("Input
 * prompt contains NSFW content") for ones it rejects, reported as permanent: the story keeps its on-device image.
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

    /** [httpClient] (same pool and dispatcher) plus a network interceptor that marks calls once they are connected. */
    private val client: OkHttpClient by lazy {
        httpClient.newBuilder()
            .addNetworkInterceptor { chain ->
                chain.request().tag(ConnectionMark::class.java)?.connected = true
                chain.proceed(chain.request())
            }
            .build()
    }

    override suspend fun canGenerate(request: ImageRequest): Boolean =
        config.isConfigured && safetyFilter.allowsCloudGeneration(request.title)

    override suspend fun generate(request: ImageRequest): GeneratedImage {
        val responseBody =
            post(json.encodeToString(FluxRequest.serializer(), FluxRequest(CloudPrompt.forTitle(request.title), STEPS)))
        val image = withContext(defaultDispatcher) { decodeImage(responseBody) }
        return GeneratedImage(webpBytes = encoder.fromEncodedImage(image), source = ImageSource.CLOUD)
    }

    private suspend fun post(body: String): String = withContext(ioDispatcher) {
        val mark = ConnectionMark()
        val call = client.newCall(
            Request.Builder()
                .url("${config.baseUrl}accounts/${config.accountId}/ai/run/$MODEL")
                .header("Authorization", "Bearer ${config.apiToken}")
                .post(body.toRequestBody(JSON))
                .tag(ConnectionMark::class.java, mark)
                .build(),
        )
        // executeAsync cancels the HTTP call when the coroutine is cancelled.
        val response = try {
            call.executeAsync()
        } catch (e: IOException) {
            throw failureBeforeResponse(e, connected = mark.connected)
        }
        val (code, responseBody) = response.use { it.code to it.body.string() }
        if (code !in 200..299) {
            // Error bodies use the same envelope; their messages (auth, quota, moderation) are worth keeping.
            val errors = parseOrNull(responseBody)?.errors.orEmpty()
            val reason = errors.joinToString { it.message }.takeIf { it.isNotBlank() }
            val message = "Cloudflare returned HTTP $code" + reason?.let { ": $it" }.orEmpty()
            // 429 is either the daily free allocation (3036) or "capacity temporarily exceeded" (3040); 502-504 mean
            // the service is down for everyone. 401/403/404 are the token or account id, wrong for every request.
            // Other 4xx (moderation, bad input) repeat on retry of this request; 408 and 500 may not.
            throw when {
                code == 429 && errors.any { it.isDailyLimit() } -> QuotaExceededException(message)
                code == 429 || code in 502..504 -> ProviderUnavailableException(message)
                code == 401 || code == 403 || code == 404 -> ProviderMisconfiguredException(message)
                code in 400..499 && code != 408 -> PermanentGenerationException(message)
                else -> IOException(message)
            }
        }
        responseBody
    }

    /**
     * No answer arrived. Without a connection (DNS, refused, connect timeout, TLS handshake, e.g. behind a captive
     * portal) the request never reached Cloudflare, and a connection dropped before any answer (socket reset, end of
     * stream) says nothing about it either: both are the provider being unavailable, not counted against the story.
     *
     * Two failures on a live connection are this request's own and stay transient (counted, so a story that always
     * fails gives up instead of blocking the queue): a timeout waiting for the image, and an HTTP/2 reset of this
     * request's stream other than REFUSED_STREAM or CANCEL. Those two are about the connection, not the request:
     * REFUSED_STREAM is the server turning away new streams (e.g. while it shuts the connection down), and CANCEL is
     * also how OkHttp fails open streams when the server closes the whole connection cleanly (FIN), so both stay
     * unavailable. OkHttp exposes stream resets only as `StreamResetException` from its internal package; it is a
     * public class, and should it move, this stops compiling rather than silently misclassifying.
     *
     * [connected] is per call, not per attempt: if OkHttp retries after a failure on a live connection and the retry
     * times out connecting, that timeout is counted once against the story (a refused connection stays unavailable).
     * Rare, and bounded by the attempt limit.
     */
    private fun failureBeforeResponse(e: IOException, connected: Boolean): IOException {
        val streamReset = e is StreamResetException &&
            e.errorCode != ErrorCode.REFUSED_STREAM &&
            e.errorCode != ErrorCode.CANCEL
        val ownFailure = connected && (e is InterruptedIOException || streamReset)
        return if (ownFailure) e else ProviderUnavailableException("Could not reach Cloudflare: $e", e)
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

    /** Set by the network interceptor: the call got a connection (and TLS) to Cloudflare. */
    private class ConnectionMark {
        @Volatile
        var connected = false
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
    private data class CloudflareError(val code: Int = 0, val message: String = "") {
        /** By code, or by message in case an error arrives without the documented code. */
        fun isDailyLimit() = code == ACCOUNT_LIMITED || message.contains("daily free allocation", ignoreCase = true)
    }

    private companion object {
        const val MODEL = "@cf/black-forest-labs/flux-1-schnell"

        /** "You have used up your daily free allocation" (developers.cloudflare.com/workers-ai/platform/errors). */
        const val ACCOUNT_LIMITED = 3036

        /** The model's default; its maximum is 8. Each step costs extra neurons for little gain at thumbnail size. */
        const val STEPS = 4
        val JSON = "application/json".toMediaType()
        val json = Json { ignoreUnknownKeys = true }
    }
}
