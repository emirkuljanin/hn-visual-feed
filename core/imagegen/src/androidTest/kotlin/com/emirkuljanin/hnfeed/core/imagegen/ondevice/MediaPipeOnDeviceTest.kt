package com.emirkuljanin.hnfeed.core.imagegen.ondevice

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emirkuljanin.hnfeed.core.imagegen.ImageRequest
import com.emirkuljanin.hnfeed.core.imagegen.ImageSource
import com.emirkuljanin.hnfeed.core.imagegen.encode.ThumbnailEncoder
import java.io.File
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The real model and native MediaPipe runtime, which Robolectric cannot load: runs on a device or emulator. */
@RunWith(AndroidJUnit4::class)
class MediaPipeOnDeviceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val embedder = MediaPipeTextEmbedder(context, Dispatchers.Default)

    @Test
    fun relatedTitles_embedCloserThanUnrelatedOnes() = runTest {
        val rust = checkNotNull(embedder.embed("Rust support lands in the Linux kernel"))
        val drivers = checkNotNull(embedder.embed("Linux adds its first drivers written in Rust"))
        val bread = checkNotNull(embedder.embed("The best sourdough bread recipe for beginners"))

        assertEquals(100, rust.size)
        val related = cosine(rust, drivers)
        val unrelated = cosine(rust, bread)
        assertTrue("related=$related unrelated=$unrelated", related > unrelated + 0.2f)
    }

    /** Saves real on-device thumbnails to the app's external files dir, to pull and look at with adb. */
    @Test
    fun generatesThumbnailsWithTheRealModel() = runTest {
        val generator = OnDeviceImageGenerator(
            embedder = embedder,
            artist = ProceduralArtist(),
            encoder = ThumbnailEncoder(Dispatchers.Default),
            defaultDispatcher = Dispatchers.Default,
        )
        val outputDir = File(checkNotNull(context.getExternalFilesDir(null)), "thumbnails").apply { mkdirs() }

        SAMPLE_TITLES.forEachIndexed { index, title ->
            val image = generator.generate(ImageRequest(storyId = index.toLong(), title = title))
            assertEquals(ImageSource.ON_DEVICE, image.source)
            File(outputDir, "sample_$index.webp").writeBytes(image.webpBytes)
        }
        SAMPLE_TITLES.indices.forEach { assertTrue(File(outputDir, "sample_$it.webp").length() > 0) }
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        val dot = a.indices.sumOf { (a[it] * b[it]).toDouble() }
        val norms = sqrt(a.sumOf { (it * it).toDouble() }) * sqrt(b.sumOf { (it * it).toDouble() })
        return (dot / norms).toFloat()
    }

    private companion object {
        val SAMPLE_TITLES = listOf(
            "Rust support lands in the Linux kernel",
            "Linux adds its first drivers written in Rust",
            "The best sourdough bread recipe for beginners",
            "Show HN: I built a tiny Hacker News reader",
            "SpaceX launches another batch of Starlink satellites",
            "NASA's new telescope images a distant galaxy",
        )
    }
}
