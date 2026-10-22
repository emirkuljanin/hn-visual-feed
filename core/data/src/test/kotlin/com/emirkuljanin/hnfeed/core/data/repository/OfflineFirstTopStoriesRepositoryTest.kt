package com.emirkuljanin.hnfeed.core.data.repository

import androidx.paging.testing.asSnapshot
import com.emirkuljanin.hnfeed.core.data.paging.TopStoriesRemoteMediator
import com.emirkuljanin.hnfeed.core.data.testing.FakeHnNetworkDataSource
import com.emirkuljanin.hnfeed.core.data.testing.InMemoryDatabaseTest
import com.emirkuljanin.hnfeed.core.data.testing.networkStory
import com.emirkuljanin.hnfeed.core.model.StoryType
import java.time.Instant
import javax.inject.Provider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/** End to end through Paging: mediator, database and PagingSource, with only the network faked. */
internal class OfflineFirstTopStoriesRepositoryTest : InMemoryDatabaseTest() {

    private val network = FakeHnNetworkDataSource()
    private val repository by lazy {
        OfflineFirstTopStoriesRepository(
            storyDao = storyDao,
            remoteMediator = Provider { TopStoriesRemoteMediator(network, topStoryDao, storyDao, transaction, clock) },
        )
    }

    @Test
    fun topStories_loadsTheFeedFromTheNetwork_asDomainModels() = runTest {
        network.ranking = listOf(8, 3, 5)
        network.addStories(8, 3, 5)

        val stories = repository.topStories().asSnapshot()

        assertEquals(listOf(8L, 3L, 5L), stories.map { it.id })
        with(stories.first()) {
            assertEquals("Story 8", title)
            assertEquals("author8", author)
            assertEquals(Instant.ofEpochSecond(1_700_000_008), postedAt)
            assertEquals(StoryType.STORY, type)
        }
    }

    @Test
    fun topStories_pagesThroughTheWholeSnapshot_inRankOrder() = runTest {
        val ranking = (45L downTo 1L).toList()
        network.ranking = ranking
        network.addStories(*ranking.toLongArray())

        val stories = repository.topStories().asSnapshot { appendScrollWhile { it.id != 1L } }

        assertEquals(ranking, stories.map { it.id })
    }

    @Test
    fun topStories_keepPaging_pastAPageInWhichNothingIsShowable() = runTest {
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
}
