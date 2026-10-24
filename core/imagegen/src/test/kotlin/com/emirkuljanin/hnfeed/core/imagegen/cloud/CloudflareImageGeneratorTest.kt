package com.emirkuljanin.hnfeed.core.imagegen.cloud

import com.emirkuljanin.hnfeed.core.imagegen.ImageRequest
import com.emirkuljanin.hnfeed.core.imagegen.ImageSource
import com.emirkuljanin.hnfeed.core.imagegen.PermanentGenerationException
import com.emirkuljanin.hnfeed.core.imagegen.ProviderMisconfiguredException
import com.emirkuljanin.hnfeed.core.imagegen.ProviderUnavailableException
import com.emirkuljanin.hnfeed.core.imagegen.QuotaExceededException
import com.emirkuljanin.hnfeed.core.imagegen.THUMBNAIL_WIDTH
import com.emirkuljanin.hnfeed.core.imagegen.encode.ThumbnailEncoder
import com.emirkuljanin.hnfeed.core.imagegen.safety.TitleSafetyFilter
import com.emirkuljanin.hnfeed.core.imagegen.testing.decode
import com.emirkuljanin.hnfeed.core.imagegen.testing.isWebp
import com.emirkuljanin.hnfeed.core.imagegen.testing.splitColourJpeg
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.internal.http2.ErrorCode
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CloudflareImageGeneratorTest {

    private val server = MockWebServer()
    private val request = ImageRequest(storyId = 8863, title = "Show HN: A tiny reader")

    @BeforeTest
    fun setUp() = server.start()

    @AfterTest
    fun tearDown() = server.close()

    @Test
    fun generate_callsFluxWithAuthAndPrompt_andReturnsAWebpThumbnail() = runTest {
        server.enqueue(success(splitColourJpeg(1024, 1024)))

        val image = generator().generate(request)

        assertEquals(ImageSource.CLOUD, image.source)
        assertTrue(isWebp(image.webpBytes))
        assertEquals(THUMBNAIL_WIDTH, decode(image.webpBytes).width)
        val recorded = server.takeRequest()
        assertEquals(
            "/client/v4/accounts/account-1/ai/run/@cf/black-forest-labs/flux-1-schnell",
            recorded.url.encodedPath,
        )
        assertEquals("Bearer token-1", recorded.headers["Authorization"])
        val body = recorded.body!!.utf8()
        assertContains(body, "A tiny reader")
        assertContains(body, "\"steps\":4")
        assertFalse("seed" in body, "The model rejects unknown fields such as seed")
    }

    @Test
    fun generate_failsWithAnIOException_carryingCloudflaresReason_onAnHttpError() = runTest {
        // The real response for a prompt Cloudflare's own classifier rejects.
        server.enqueue(
            MockResponse(
                code = 400,
                body = """{"errors":[{"message":"AiError: Input prompt contains NSFW content.","code":8007}],""" +
                    """"success":false,"result":{},"messages":[]}""",
            ),
        )

        // A rejection repeats on every retry, so it is reported as permanent.
        val error = assertFailsWith<PermanentGenerationException> { generator().generate(request) }
        assertEquals("Cloudflare returned HTTP 400: AiError: Input prompt contains NSFW content.", error.message)
    }

    @Test
    fun generate_reportsAuthAndAccountErrorsAsMisconfigured_notAsARejectedPrompt() = runTest {
        // A bad or revoked token (401, or 403 with Cloudflare's "Authentication error"), or a wrong account id (404),
        // fails every request alike; it says nothing about the story.
        server.enqueue(MockResponse(code = 401, body = "{}"))
        server.enqueue(
            MockResponse(
                code = 403,
                body = """{"success":false,"errors":[{"code":10000,"message":"Authentication error"}]}""",
            ),
        )
        server.enqueue(MockResponse(code = 404, body = "{}"))

        listOf(401, 403, 404).forEach { code ->
            assertFailsWith<ProviderMisconfiguredException>("HTTP $code") { generator().generate(request) }
        }
    }

    @Test
    fun generate_reportsTheDailyAllocationAsQuotaExceeded() = runTest {
        server.enqueue(
            MockResponse(
                code = 429,
                body = """{"success":false,"errors":[{"code":3036,"message":"Daily free allocation used up"}]}""",
            ),
        )

        assertFailsWith<QuotaExceededException> { generator().generate(request) }
    }

    @Test
    fun generate_reportsOtherHttp429sAsTheProviderBeingUnavailable() = runTest {
        server.enqueue(
            MockResponse(
                code = 429,
                body = """{"success":false,"errors":[{"code":3040,"message":"Capacity temporarily exceeded"}]}""",
            ),
        )
        server.enqueue(MockResponse(code = 429, body = "{}"))
        server.enqueue(MockResponse(code = 503, body = "{}"))

        repeat(3) { assertFailsWith<ProviderUnavailableException> { generator().generate(request) } }
    }

    @Test
    fun generate_recognisesTheDailyAllocationByItsMessage_whenTheCodeIsMissing() = runTest {
        server.enqueue(
            MockResponse(
                code = 429,
                body = """{"success":false,"errors":[{"message":"You have used up your daily free allocation"}]}""",
            ),
        )

        assertFailsWith<QuotaExceededException> { generator().generate(request) }
    }

    @Test
    fun generate_reportsTimeoutsAndServerErrorsAsTransient() = runTest {
        listOf(500, 408).forEach { code ->
            server.enqueue(MockResponse(code = code, body = "{}"))

            val error = assertFailsWith<IOException> { generator().generate(request) }
            assertFalse(
                error is PermanentGenerationException ||
                    error is QuotaExceededException ||
                    error is ProviderUnavailableException,
                "HTTP $code",
            )
        }
    }

    @Test
    fun generate_reportsAConnectionDroppedBeforeAnyResponse_asTheProviderBeingUnavailable() = runTest {
        // The server hangs up as soon as it accepts (a reset, a captive portal). OkHttp may retry on a new connection.
        repeat(3) { server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build()) }

        assertFailsWith<ProviderUnavailableException> { generator().generate(request) }
    }

    @Test
    fun generate_reportsAnHttp2ConnectionClosedBeforeAnyResponse_asTheProviderBeingUnavailable() = runTest {
        // The server has read the request, then closes the whole connection cleanly (FIN, not RST) while the client
        // waits for the answer. OkHttp fails the open stream with StreamResetException(CANCEL), which is about the
        // connection, not this request.
        MockWebServer().apply { protocols = listOf(Protocol.H2_PRIOR_KNOWLEDGE) }.use { h2Server ->
            h2Server.start()
            repeat(3) {
                h2Server.enqueue(
                    MockResponse.Builder()
                        .onResponseStart(SocketEffect.CloseSocket(closeSocket = false, shutdownOutput = true))
                        .build(),
                )
            }
            val h2Client = OkHttpClient.Builder().protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE)).build()
            val config = configured().copy(baseUrl = h2Server.url("/client/v4/").toString())

            assertFailsWith<ProviderUnavailableException> { generator(config, h2Client).generate(request) }
        }
    }

    @Test
    fun generate_reportsAnUnreachableServer_asTheProviderBeingUnavailable() = runTest {
        val config = configured()
        server.close() // Connection refused.

        assertFailsWith<ProviderUnavailableException> { generator(config).generate(request) }
    }

    @Test
    fun generate_keepsAStreamResetOnAHealthyConnection_transient() = runTest {
        // HTTP/2 (as Cloudflare speaks it): the server resets this request's stream; the connection stays up. That is
        // this request's failure, so it must count against the story, or one such story would block the queue.
        MockWebServer().apply { protocols = listOf(Protocol.H2_PRIOR_KNOWLEDGE) }.use { h2Server ->
            h2Server.start()
            repeat(3) {
                h2Server.enqueue(
                    MockResponse.Builder()
                        .onRequestStart(SocketEffect.CloseStream(ErrorCode.INTERNAL_ERROR.httpCode))
                        .build(),
                )
            }
            val h2Client = OkHttpClient.Builder().protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE)).build()
            val config = configured().copy(baseUrl = h2Server.url("/client/v4/").toString())

            val error = assertFailsWith<IOException> { generator(config, h2Client).generate(request) }
            assertFalse(error is ProviderUnavailableException, error.toString())
        }
    }

    @Test
    fun generate_reportsARefusedStream_asTheProviderBeingUnavailable() = runTest {
        // REFUSED_STREAM turns away a stream before any processing; OkHttp retries it, then gives up.
        MockWebServer().apply { protocols = listOf(Protocol.H2_PRIOR_KNOWLEDGE) }.use { h2Server ->
            h2Server.start()
            repeat(5) {
                h2Server.enqueue(
                    MockResponse.Builder()
                        .onRequestStart(SocketEffect.CloseStream(ErrorCode.REFUSED_STREAM.httpCode))
                        .build(),
                )
            }
            val h2Client = OkHttpClient.Builder().protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE)).build()
            val config = configured().copy(baseUrl = h2Server.url("/client/v4/").toString())

            assertFailsWith<ProviderUnavailableException> { generator(config, h2Client).generate(request) }
        }
    }

    @Test
    fun generate_keepsATimeoutWaitingForTheAnswer_transient() = runTest {
        // Connected and sent: a slow answer may be this request's own (a slow generation), so it counts.
        server.enqueue(MockResponse.Builder().body("{}").headersDelay(5, TimeUnit.SECONDS).build())
        val impatientClient = OkHttpClient.Builder().readTimeout(200, TimeUnit.MILLISECONDS).build()

        val error = assertFailsWith<IOException> { generator(httpClient = impatientClient).generate(request) }
        assertFalse(error is ProviderUnavailableException, error.toString())
    }

    @Test
    fun generate_keepsAResponseCutOffMidway_transient() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .body("{\"success\":true,\"result\":{\"image\":\"" + "A".repeat(64_000) + "\"}}")
                .onResponseBody(SocketEffect.CloseSocket())
                .build(),
        )

        val error = assertFailsWith<IOException> { generator().generate(request) }
        assertFalse(error is ProviderUnavailableException, error.toString())
    }

    @Test
    fun generate_failsWithAnIOException_onAnHttpErrorWithoutAnEnvelope() = runTest {
        server.enqueue(MockResponse(code = 502, body = "<html>bad gateway</html>"))

        val error = assertFailsWith<IOException> { generator().generate(request) }
        assertEquals("Cloudflare returned HTTP 502", error.message)
    }

    @Test
    fun generate_failsWithAnIOException_whenCloudflareReportsFailure() = runTest {
        server.enqueue(MockResponse(body = """{"success":false,"errors":[{"code":3036,"message":"Daily limit"}]}"""))

        val error = assertFailsWith<IOException> { generator().generate(request) }
        assertContains(error.message.orEmpty(), "Daily limit")
    }

    @Test
    fun generate_failsWithAnIOException_whenSuccessHasNoImage() = runTest {
        server.enqueue(MockResponse(body = """{"success":true,"result":{},"errors":[]}"""))

        assertFailsWith<IOException> { generator().generate(request) }
    }

    @Test
    fun generate_failsWithAnIOException_onAMalformedBody() = runTest {
        server.enqueue(MockResponse(body = "<html>gateway error</html>"))

        assertFailsWith<IOException> { generator().generate(request) }
    }

    @Test
    fun generate_failsWithAnIOException_whenTheImageIsNotAnImage() = runTest {
        server.enqueue(success("not an image".toByteArray()))

        assertFailsWith<IOException> { generator().generate(request) }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class) // getCompletionExceptionOrNull
    fun cancellingGenerate_cancelsTheHttpCall() = runTest {
        server.enqueue(MockResponse.Builder().body("{}").headersDelay(30, TimeUnit.SECONDS).build())
        // A read timeout far beyond the threshold, so only real cancellation can finish the call in time.
        val patientClient = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()

        val call = async(Dispatchers.IO) { generator(httpClient = patientClient).generate(request) }
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS), "The request never reached the server")
        val cancelledAt = TimeSource.Monotonic.markNow()
        call.cancel()
        call.join()

        assertIs<CancellationException>(call.getCompletionExceptionOrNull())
        assertTrue(cancelledAt.elapsedNow() < 3.seconds, "Cancellation waited for the response")
    }

    @Test
    fun canGenerate_requiresCredentials_andASafeTitle() = runTest {
        assertTrue(generator().canGenerate(request))
        assertFalse(generator(CloudflareConfig(accountId = "", apiToken = "")).canGenerate(request))
        assertFalse(generator().canGenerate(request.copy(title = "NSFW leak")))
    }

    @Test
    fun config_neverPrintsTheToken() {
        assertFalse("token-1" in configured().toString())
    }

    // Real dispatchers: these tests talk to a real (local) socket.
    private fun generator(config: CloudflareConfig = configured(), httpClient: OkHttpClient = OkHttpClient()) =
        CloudflareImageGenerator(
            config = config,
            httpClient = httpClient,
            safetyFilter = TitleSafetyFilter(),
            encoder = ThumbnailEncoder(Dispatchers.Default),
            ioDispatcher = Dispatchers.IO,
            defaultDispatcher = Dispatchers.Default,
        )

    private fun configured() = CloudflareConfig(
        accountId = "account-1",
        apiToken = "token-1",
        baseUrl = server.url("/client/v4/").toString(),
    )

    private fun success(image: ByteArray) = MockResponse(
        body = """{"success":true,"result":{"image":"${Base64.getEncoder().encodeToString(image)}"},"errors":[]}""",
    )
}
