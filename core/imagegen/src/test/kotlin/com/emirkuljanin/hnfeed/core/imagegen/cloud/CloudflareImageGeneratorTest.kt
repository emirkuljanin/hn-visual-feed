package com.emirkuljanin.hnfeed.core.imagegen.cloud

import com.emirkuljanin.hnfeed.core.imagegen.ImageRequest
import com.emirkuljanin.hnfeed.core.imagegen.ImageSource
import com.emirkuljanin.hnfeed.core.imagegen.PermanentGenerationException
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
import okhttp3.OkHttpClient
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
