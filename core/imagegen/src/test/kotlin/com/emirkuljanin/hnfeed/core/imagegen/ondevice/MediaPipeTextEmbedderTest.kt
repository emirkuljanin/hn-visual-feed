package com.emirkuljanin.hnfeed.core.imagegen.ondevice

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The JVM has no MediaPipe native library, which makes it the cheap place to test the degradation path: no crash,
 * just `null` (the real model is covered by the instrumented `MediaPipeOnDeviceTest`).
 */
@RunWith(RobolectricTestRunner::class)
class MediaPipeTextEmbedderTest {

    @Test
    fun withoutTheNativeRuntime_embedDegradesToNull_insteadOfThrowing() = runTest {
        val embedder = MediaPipeTextEmbedder(
            context = ApplicationProvider.getApplicationContext<Context>(),
            defaultDispatcher = StandardTestDispatcher(testScheduler),
        )

        assertNull(embedder.embed("loading the model fails here"))
        assertNull(embedder.embed("and stays failed"))
    }
}
