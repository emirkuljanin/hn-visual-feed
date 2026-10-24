package com.emirkuljanin.hnfeed.core.data.repository

import androidx.paging.PagingData
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import androidx.paging.testing.asSnapshot
import com.emirkuljanin.hnfeed.core.data.model.asStoryEntityOrNull
import com.emirkuljanin.hnfeed.core.data.paging.TopStoriesRemoteMediator
import com.emirkuljanin.hnfeed.core.data.testing.FakeHnNetworkDataSource
import com.emirkuljanin.hnfeed.core.data.testing.FakeThumbnailScheduler
import com.emirkuljanin.hnfeed.core.data.testing.InMemoryDatabaseTest
import com.emirkuljanin.hnfeed.core.data.testing.networkStory
import com.emirkuljanin.hnfeed.core.data.thumbnail.ThumbnailStore
import com.emirkuljanin.hnfeed.core.database.model.ThumbnailEntity
import com.emirkuljanin.hnfeed.core.database.model.UpgradeState
import com.emirkuljanin.hnfeed.core.model.Story
import com.emirkuljanin.hnfeed.core.model.StoryType
import com.emirkuljanin.hnfeed.core.model.Thumbnail
import com.emirkuljanin.hnfeed.core.model.ThumbnailSource
import java.io.File
import java.time.Instant
import javax.inject.Provider
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder

/** End to end through Paging: mediator, database and PagingSource, with only the network faked. */
internal class OfflineFirstTopStoriesRepositoryTest : InMemoryDatabaseTest() {

    @get:Rule
    val folder = TemporaryFolder()

    /**
     * Room runs on the test's scheduler. `asSnapshot` decides the feed is settled with a 1 ms debounce in virtual
     * time; with Room's invalidation on its own threads, that could pass after the mediator's write and before the
     * write had reloaded the page, returning an empty or short feed under load. Every test therefore runs on this
     * dispatcher through [runFeedTest]: under a plain `runTest {}` Room's work would run on a scheduler the test does
     * not advance, and the test fails ("Detected use of different schedulers").
     */
    private val testDispatcher = StandardTestDispatcher()
    override val queryContext: CoroutineContext = testDispatcher

    private fun runFeedTest(testBody: suspend TestScope.() -> Unit) = runTest(testDispatcher, testBody = testBody)

    private val network = FakeHnNetworkDataSource()
    private val thumbnailScheduler = FakeThumbnailScheduler()
    private val thumbnailStore by lazy { ThumbnailStore(File(folder.root, "thumbnails"), Dispatchers.IO) }
    private val repository by lazy {
        OfflineFirstTopStoriesRepository(
            storyDao = storyDao,
            remoteMediator = Provider {
                TopStoriesRemoteMediator(network, topStoryDao, storyDao, transaction, clock, thumbnailScheduler)
            },
            thumbnailStore = thumbnailStore,
            thumbnailScheduler = thumbnailScheduler,
        )
    }

    @Test
    fun topStories_loadsTheFeedFromTheNetwork_asDomainModels() = runFeedTest {
        network.ranking = listOf(8, 3, 5)
        network.addStories(8, 3, 5)

        val stories = repository.topStories().asSnapshot()

        assertEquals(listOf(8L, 3L, 5L), stories.map { it.id })
        with(stories.first()) {
            assertEquals("Story 8", title)
            assertEquals("author8", author)
            assertEquals(Instant.ofEpochSecond(1_700_000_008), postedAt)
            assertEquals(StoryType.STORY, type)
            assertNull(thumbnail, "No thumbnail is generated yet")
        }
        assertTrue(thumbnailScheduler.scheduled > 0, "Collecting the feed schedules thumbnail work")
    }

    @Test
    fun topStories_exposeTheThumbnailFile_onceItExists() = runFeedTest {
        network.ranking = listOf(8, 3)
        network.addStories(8, 3)
        repository.topStories().asSnapshot()
        val fileName = thumbnailStore.write(3, ThumbnailSource.CLOUD, byteArrayOf(1))
        database.thumbnailDao().upsert(
            ThumbnailEntity(
                storyId = 3,
                fileName = fileName,
                source = ThumbnailSource.CLOUD,
                upgradeState = UpgradeState.DONE,
                upgradeAttempts = 0,
                updatedAt = now,
            ),
        )

        val stories = repository.topStories().asSnapshot()

        assertNull(stories[0].thumbnail)
        assertEquals(Thumbnail(thumbnailStore.path(fileName), ThumbnailSource.CLOUD), stories[1].thumbnail)
    }

    @Test
    fun topStories_pagesThroughTheWholeSnapshot_inRankOrder() = runFeedTest {
        val ranking = (45L downTo 1L).toList()
        network.ranking = ranking
        network.addStories(*ranking.toLongArray())

        val stories = repository.topStories().asSnapshot { appendScrollWhile { it.id != 1L } }

        assertEquals(ranking, stories.map { it.id })
    }

    @Test
    fun topStories_keepPaging_pastAPageInWhichNothingIsShowable() = runFeedTest {
        val ranking = (1L..45L).toList()
        network.ranking = ranking
        network.addStories(*ranking.toLongArray())
        // The whole second page is unshowable: half dead, half unknown to the API (JSON null).
        val (dead, missing) = (21L..40L).partition { it % 2 == 0L }
        dead.forEach { network.items[it] = networkStory(it).copy(dead = true) }
        missing.forEach { network.items.remove(it) }

        val stories = repository.topStories().asSnapshot { appendScrollWhile { it.id != 45L } }

        assertEquals((1L..20L) + (41L..45L), stories.map { it.id })
    }

    /**
     * The feed screen leaves composition while a story is open, and its `LazyListState` comes back as a bare index.
     * Opening a story writes its row, which invalidates the feed: the reloaded list must keep every story at its index.
     */
    @Test
    fun topStories_keepEachStoryAtItsIndex_whenAWriteInvalidatesTheFeed() = runFeedTest {
        val ranking = (101L..160L).toList()
        cacheLoadedSnapshot(ranking)
        val presenter = present(repository.topStories())
        presenter.scrollTo(38)
        val opened = checkNotNull(presenter[38])

        // What opening the story does: its details refresh updates the cached row.
        storyDao.upsert(listOfNotNull(networkStory(opened.id).copy(score = 999).asStoryEntityOrNull(now)))
        presenter.awaitUntil { presenter.snapshot().items.any { it.id == opened.id && it.score == 999 } }

        assertEquals(opened.id, presenter.snapshot().getOrNull(38)?.id)
    }

    /** After process death a new Pager starts at the top, but the restored index must still reach its story. */
    @Test
    fun topStories_reachEveryCachedIndex_fromANewPager() = runFeedTest {
        val ranking = (101L..160L).toList()
        cacheLoadedSnapshot(ranking)
        val presenter = present(repository.topStories())
        presenter.awaitUntil { presenter.size > 0 }

        assertEquals(ranking.size, presenter.size, "The list spans every cached story from the first page on")
        presenter[42]
        presenter.awaitUntil { presenter.snapshot().getOrNull(42) != null }
        assertEquals(ranking[42], presenter.snapshot()[42]?.id)
    }

    /** A fresh, fully loaded snapshot, as left by an earlier session: the mediator neither refreshes nor appends. */
    private suspend fun cacheLoadedSnapshot(ranking: List<Long>) {
        topStoryDao.replaceSnapshot(ranking)
        storyDao.upsert(ranking.mapNotNull { networkStory(it).asStoryEntityOrNull(now) })
        topStoryDao.markLoaded(ranking)
    }

    /** Presents the feed the way `collectAsLazyPagingItems` does, on the test's scheduler. */
    private fun TestScope.present(feed: Flow<PagingData<Story>>): PagingDataPresenter<Story> {
        val presenter = object : PagingDataPresenter<Story>(StandardTestDispatcher(testScheduler)) {
            override suspend fun presentPagingDataEvent(event: PagingDataEvent<Story>) = Unit
        }
        backgroundScope.launch { feed.collectLatest(presenter::collectFrom) }
        return presenter
    }

    /** Reads items towards [index] like a list scrolling down, until the item at [index] is loaded. */
    private suspend fun PagingDataPresenter<Story>.scrollTo(index: Int) {
        awaitUntil { size > 0 }
        while (snapshot().getOrNull(index) == null) {
            val sizeBefore = size
            get(minOf(index, size - 1))
            awaitUntil { snapshot().getOrNull(index) != null || size != sizeBefore }
        }
    }

    /** Waits, letting the scheduler run Room's loads, until the presented pages satisfy [condition]. */
    private suspend fun PagingDataPresenter<Story>.awaitUntil(condition: () -> Boolean) {
        onPagesUpdatedFlow.onStart { emit(Unit) }.first { condition() }
    }
}
