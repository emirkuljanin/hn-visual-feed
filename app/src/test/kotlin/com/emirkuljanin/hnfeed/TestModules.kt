package com.emirkuljanin.hnfeed

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.emirkuljanin.hnfeed.core.common.time.di.ClockModule
import com.emirkuljanin.hnfeed.core.data.di.DataModule
import com.emirkuljanin.hnfeed.core.data.repository.StoryDetailsRepository
import com.emirkuljanin.hnfeed.core.data.repository.TopStoriesRepository
import com.emirkuljanin.hnfeed.core.data.util.NetworkMonitor
import com.emirkuljanin.hnfeed.core.model.Story
import com.emirkuljanin.hnfeed.core.model.Thumbnail
import com.emirkuljanin.hnfeed.core.model.ThumbnailSource
import com.emirkuljanin.hnfeed.core.testing.data.TEST_NOW
import com.emirkuljanin.hnfeed.core.testing.data.testStories
import com.emirkuljanin.hnfeed.core.testing.data.testStoryComments
import com.emirkuljanin.hnfeed.core.testing.repository.FakeStoryDetailsRepository
import com.emirkuljanin.hnfeed.core.testing.repository.FakeTopStoriesRepository
import com.emirkuljanin.hnfeed.core.testing.util.TestNetworkMonitor
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.io.File
import java.time.Clock
import java.time.ZoneOffset
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * The data layer as fakes for app-level UI tests: the real app, screens, ViewModels and navigation, with fixed
 * stories and comments. The fakes are singletons, so tests can inject them to drive or inspect them.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataModule::class])
object TestDataModule {

    /** The test stories with real image files, so Coil really loads and caches them as in the app. */
    @Provides
    @Singleton
    @TestStories
    fun stories(@ApplicationContext context: Context): List<Story> {
        val directory = File(context.cacheDir, "test-thumbnails").apply { mkdirs() }
        return testStories.mapIndexed { index, story ->
            val file = File(directory, "${story.id}.png")
            if (!file.exists()) {
                file.outputStream().use {
                    gradient(hue = index * 90f).compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            story.copy(thumbnail = Thumbnail(file.absolutePath, ThumbnailSource.CLOUD))
        }
    }

    @Provides
    @Singleton
    fun fakeTopStories(@TestStories stories: List<Story>) = FakeTopStoriesRepository(stories)

    @Provides
    fun topStoriesRepository(fake: FakeTopStoriesRepository): TopStoriesRepository = fake

    /** Every story id gets the first test story and its discussion: enough to exercise the screen. */
    @Provides
    @Singleton
    fun fakeStoryDetails(@TestStories stories: List<Story>) =
        FakeStoryDetailsRepository(story = stories.first(), comments = testStoryComments)

    @Provides
    fun storyDetailsRepository(fake: FakeStoryDetailsRepository): StoryDetailsRepository = fake

    @Provides
    @Singleton
    fun testNetworkMonitor() = TestNetworkMonitor()

    @Provides
    fun networkMonitor(monitor: TestNetworkMonitor): NetworkMonitor = monitor

    private fun gradient(hue: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(512, 288, Bitmap.Config.ARGB_8888)
        val start = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.5f, 0.9f))
        val end = android.graphics.Color.HSVToColor(floatArrayOf((hue + 60f) % 360f, 0.6f, 0.5f))
        val paint = Paint().apply { shader = LinearGradient(0f, 0f, 512f, 288f, start, end, Shader.TileMode.CLAMP) }
        Canvas(bitmap).drawPaint(paint)
        return bitmap
    }
}

/** Qualifies the shared story list, so a plain `List<Story>` binding cannot collide with it. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TestStories

/** The same fixed "now" as the test data, so relative times ("2 hours ago") do not change from day to day. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ClockModule::class])
object TestClockModule {

    @Provides
    fun clock(): Clock = Clock.fixed(TEST_NOW, ZoneOffset.UTC)
}
