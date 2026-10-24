package com.emirkuljanin.hnfeed.core.imagegen

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class ThumbnailGeneratorTest {

    private val request = ImageRequest(storyId = 1, title = "A story")
    private val onDevice = FakeGenerator(ImageSource.ON_DEVICE)

    @Test
    fun preview_comesFromTheOnDeviceGenerator_withoutTouchingUpgrades() = runTest {
        val cloud = FakeGenerator(ImageSource.CLOUD)

        val image = TieredThumbnailGenerator(onDevice, listOf(cloud)).generatePreview(request)

        assertEquals(ImageSource.ON_DEVICE, image.source)
        assertEquals(0, cloud.calls)
    }

    @Test
    fun upgrade_returnsTheFirstAllowedGeneratorsImage() = runTest {
        val outcome = TieredThumbnailGenerator(
            onDevice,
            listOf(FakeGenerator(ImageSource.CLOUD)),
        ).generateUpgrade(request)

        assertEquals(ImageSource.CLOUD, assertIs<UpgradeOutcome.Upgraded>(outcome).image.source)
        assertEquals(0, onDevice.calls)
    }

    @Test
    fun upgrade_isNotApplicable_whenNoGeneratorMayHandleTheTitle() = runTest {
        val cloud = FakeGenerator(ImageSource.CLOUD, allowed = false) // unsafe title, or no credentials

        val outcome = TieredThumbnailGenerator(onDevice, listOf(cloud)).generateUpgrade(request)

        assertEquals(UpgradeOutcome.NotApplicable, outcome)
        assertEquals(0, cloud.calls)
    }

    @Test
    fun upgrade_mapsFailuresToOutcomes() = runTest {
        suspend fun outcomeFor(failure: Exception) =
            TieredThumbnailGenerator(onDevice, listOf(FakeGenerator(ImageSource.CLOUD, failure = failure)))
                .generateUpgrade(request)

        assertIs<UpgradeOutcome.QuotaExhausted>(outcomeFor(QuotaExceededException("HTTP 429")))
        assertIs<UpgradeOutcome.Rejected>(outcomeFor(PermanentGenerationException("HTTP 400: NSFW")))
        assertIs<UpgradeOutcome.Misconfigured>(outcomeFor(ProviderMisconfiguredException("HTTP 401")))
        assertIs<UpgradeOutcome.RetryLater>(outcomeFor(IOException("HTTP 503")))
        assertIs<UpgradeOutcome.RetryLater>(outcomeFor(SocketTimeoutException("timeout")))
        assertIs<UpgradeOutcome.Unavailable>(outcomeFor(ProviderUnavailableException("HTTP 429: capacity")))
        assertIs<UpgradeOutcome.Unavailable>(outcomeFor(UnknownHostException("api.cloudflare.com")))
        assertIs<UpgradeOutcome.Unavailable>(outcomeFor(ConnectException("refused")))
        // Android's message for a connect timeout; the request never left the device.
        assertIs<UpgradeOutcome.Unavailable>(
            outcomeFor(SocketTimeoutException("failed to connect to api.cloudflare.com (port 443) after 15000ms")),
        )
        assertIs<UpgradeOutcome.Unavailable>(outcomeFor(SSLHandshakeException("captive portal certificate")))
    }

    @Test
    fun upgrade_prefersTheMostHopefulOutcome_whenSeveralGeneratorsFail() = runTest {
        val generators = listOf(
            FakeGenerator(ImageSource.CLOUD, failure = IOException("offline")),
            FakeGenerator(ImageSource.CLOUD, failure = PermanentGenerationException("rejected")),
        )

        assertIs<UpgradeOutcome.RetryLater>(TieredThumbnailGenerator(onDevice, generators).generateUpgrade(request))
    }

    @Test
    fun upgrade_doesNotSwallowCancellation() = runTest {
        val cancelled = FakeGenerator(ImageSource.CLOUD, failure = CancellationException("cancelled"))

        assertFailsWith<CancellationException> {
            TieredThumbnailGenerator(onDevice, listOf(cancelled)).generateUpgrade(request)
        }
    }

    private class FakeGenerator(
        private val source: ImageSource,
        private val allowed: Boolean = true,
        private val failure: Exception? = null,
    ) : ImageGenerator {
        var calls = 0
            private set

        override suspend fun canGenerate(request: ImageRequest) = allowed

        override suspend fun generate(request: ImageRequest): GeneratedImage {
            calls++
            failure?.let { throw it }
            return GeneratedImage(webpBytes = byteArrayOf(1), source = source)
        }
    }
}
