package com.emirkuljanin.hnfeed.core.imagegen.cloud

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.emirkuljanin.hnfeed.core.imagegen.BuildConfig
import com.emirkuljanin.hnfeed.core.imagegen.ImageRequest
import com.emirkuljanin.hnfeed.core.imagegen.ImageSource
import com.emirkuljanin.hnfeed.core.imagegen.THUMBNAIL_WIDTH
import com.emirkuljanin.hnfeed.core.imagegen.di.ImageGenModule
import com.emirkuljanin.hnfeed.core.imagegen.encode.ThumbnailEncoder
import com.emirkuljanin.hnfeed.core.imagegen.safety.TitleSafetyFilter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * One real generation against Cloudflare Workers AI through the production client and HTTP settings. It costs
 * quota (~58 neurons), so it only runs when asked for and credentials exist:
 *
 * `./gradlew :core:imagegen:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.liveCloudflare=true`
 */
@RunWith(AndroidJUnit4::class)
class CloudflareLiveTest {

    private val config = CloudflareConfig(BuildConfig.CLOUDFLARE_ACCOUNT_ID, BuildConfig.CLOUDFLARE_API_TOKEN)

    @Test
    fun generatesARealThumbnail() {
        // Assumptions outside runTest, so JUnit reports a skip rather than a wrapped failure.
        val requested = InstrumentationRegistry.getArguments().getString("liveCloudflare") == "true"
        assumeTrue("Live Cloudflare test not requested", requested)
        assumeTrue("No Cloudflare credentials configured", config.isConfigured)
        runTest { generate() }
    }

    private suspend fun generate() {
        val generator = CloudflareImageGenerator(
            config = config,
            httpClient = ImageGenModule.cloudflareHttpClient(),
            safetyFilter = TitleSafetyFilter(),
            encoder = ThumbnailEncoder(Dispatchers.Default),
            ioDispatcher = Dispatchers.IO,
            defaultDispatcher = Dispatchers.Default,
        )

        val image = generator.generate(ImageRequest(storyId = 1, title = "Rust support lands in the Linux kernel"))

        assertEquals(ImageSource.CLOUD, image.source)
        assertEquals(THUMBNAIL_WIDTH, BitmapFactory.decodeByteArray(image.webpBytes, 0, image.webpBytes.size).width)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val outputDir = File(checkNotNull(context.getExternalFilesDir(null)), "thumbnails").apply { mkdirs() }
        File(outputDir, "cloud_sample.webp").writeBytes(image.webpBytes)
    }
}
