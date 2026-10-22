package com.emirkuljanin.hnfeed.core.network.retrofit

import com.emirkuljanin.hnfeed.core.common.dispatchers.IoDispatcher
import com.emirkuljanin.hnfeed.core.network.HnApiException
import com.emirkuljanin.hnfeed.core.network.HnNetworkDataSource
import com.emirkuljanin.hnfeed.core.network.model.NetworkItem
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonNull
import retrofit2.HttpException

/**
 * Switches to [ioDispatcher] even though Retrofit's suspend calls are asynchronous. Retrofit converts response
 * bodies on OkHttp's threads, but it creates each call on the calling thread, which on first use builds the lazily
 * injected OkHttpClient and parses the service method. This class also decodes `JsonElement` into [NetworkItem]
 * itself. How many item requests actually run in parallel is capped by the OkHttp `Dispatcher` (`NetworkModule`).
 */
internal class RetrofitHnNetworkDataSource @Inject constructor(
    private val api: HackerNewsApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : HnNetworkDataSource {

    override suspend fun topStoryIds(): List<Long> = apiCall("top stories") { api.topStoryIds() }

    override suspend fun items(ids: List<Long>): List<NetworkItem> = withContext(ioDispatcher) {
        // Structured concurrency: the first failure cancels the remaining requests and is rethrown to the caller.
        ids.map { id -> async { apiCall("item $id") { item(id) } } }.awaitAll().filterNotNull()
    }

    private suspend fun item(id: Long): NetworkItem? {
        val element = api.item(id)
        if (element is JsonNull) return null
        return NetworkJson.decodeFromJsonElement(NetworkItem.serializer(), element)
    }

    /** Runs [block] on the IO dispatcher and maps Retrofit/serialization failures to [HnApiException]. */
    private suspend fun <T> apiCall(what: String, block: suspend () -> T): T = withContext(ioDispatcher) {
        try {
            block()
        } catch (e: HttpException) {
            throw HnApiException("HTTP ${e.code()} for $what", e)
        } catch (e: SerializationException) {
            throw HnApiException("Malformed response for $what", e)
        }
    }
}
