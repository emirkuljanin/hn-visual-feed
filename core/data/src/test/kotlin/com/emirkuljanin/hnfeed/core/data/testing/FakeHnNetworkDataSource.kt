package com.emirkuljanin.hnfeed.core.data.testing

import com.emirkuljanin.hnfeed.core.network.HnNetworkDataSource
import com.emirkuljanin.hnfeed.core.network.model.NetworkItem
import java.io.IOException

/** In-memory stand-in for the Hacker News API that records what was requested. */
internal class FakeHnNetworkDataSource : HnNetworkDataSource {

    /** The current top-stories ranking, best first. */
    var ranking: List<Long> = emptyList()

    /** Items the API knows; ids missing here behave like the API's JSON `null`. */
    val items = mutableMapOf<Long, NetworkItem>()

    var rankingFailure: IOException? = null
    var itemsFailure: IOException? = null

    /** Runs after an items request is made and before it returns: lets a test change the world mid-fetch. */
    var whileFetchingItems: (suspend (List<Long>) -> Unit)? = null

    var rankingRequests = 0
        private set
    val itemRequests = mutableListOf<List<Long>>()

    fun addStories(vararg ids: Long) {
        ids.forEach { items[it] = networkStory(it) }
    }

    override suspend fun topStoryIds(): List<Long> {
        rankingFailure?.let { throw it }
        rankingRequests++
        return ranking
    }

    override suspend fun items(ids: List<Long>): List<NetworkItem> {
        itemsFailure?.let { throw it }
        itemRequests += ids
        whileFetchingItems?.invoke(ids)
        return ids.mapNotNull { items[it] }
    }
}

internal fun networkStory(id: Long, type: String = "story") = NetworkItem(
    id = id,
    type = type,
    by = "author$id",
    time = 1_700_000_000 + id,
    title = "Story $id",
    url = "https://example.com/$id",
    score = id.toInt(),
    descendants = 2,
)
