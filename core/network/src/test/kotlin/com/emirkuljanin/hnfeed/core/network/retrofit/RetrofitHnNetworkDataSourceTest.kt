package com.emirkuljanin.hnfeed.core.network.retrofit

import com.emirkuljanin.hnfeed.core.network.model.NetworkItem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import retrofit2.HttpException

class RetrofitHnNetworkDataSourceTest {

    private val server = MockWebServer()
    private lateinit var dataSource: RetrofitHnNetworkDataSource

    @BeforeTest
    fun setUp() {
        server.start()
        // A real dispatcher: these tests talk to a real (local) socket, so virtual time would not help.
        dataSource = RetrofitHnNetworkDataSource(
            api = hackerNewsApi(server.url("/").toString(), OkHttpClient()),
            ioDispatcher = Dispatchers.IO,
        )
    }

    @AfterTest
    fun tearDown() {
        server.close()
    }

    @Test
    fun topStoryIds_requestsTopStories_andKeepsRankOrder() = runTest {
        server.enqueue(MockResponse(body = "[39, 8863, 121003]"))

        val ids = dataSource.topStoryIds()

        assertEquals(listOf(39L, 8863L, 121003L), ids)
        assertEquals("/v0/topstories.json", server.takeRequest().url.encodedPath)
    }

    @Test
    fun items_decodesAllFields() = runTest {
        server.respondWith(mapOf(8863L to DROPBOX_STORY_JSON))

        val items = dataSource.items(listOf(8863))

        val expected = NetworkItem(
            id = 8863,
            type = "story",
            by = "dhouston",
            time = 1_175_714_200,
            title = "My YC app: Dropbox - Throw away your USB drive",
            url = "http://www.getdropbox.com/u/2/screencast.html",
            score = 111,
            descendants = 71,
            kids = listOf(8952, 9224),
        )
        assertEquals(listOf(expected), items)
    }

    @Test
    fun items_ignoresUnknownFields_andDefaultsMissingOnes() = runTest {
        server.respondWith(mapOf(1L to """{"id":1,"type":"job","title":"Hiring","someNewField":{"nested":true}}"""))

        val item = dataSource.items(listOf(1)).single()

        assertEquals(NetworkItem(id = 1, type = "job", title = "Hiring"), item)
    }

    @Test
    fun items_keepsRequestedOrder_andSkipsIdsTheApiAnswersWithNull() = runTest {
        server.respondWith(
            mapOf(
                3L to """{"id":3,"type":"story"}""",
                2L to "null",
                1L to """{"id":1,"type":"story"}""",
            ),
        )

        val ids = dataSource.items(listOf(3, 2, 1)).map { it.id }

        assertEquals(listOf(3L, 1L), ids)
    }

    @Test
    fun items_failsAsAWhole_whenOneRequestFails() = runTest {
        server.respondWith(mapOf(1L to """{"id":1}"""), failingIds = setOf(2L))

        assertFailsWith<HttpException> { dataSource.items(listOf(1, 2)) }
    }

    /** Requests for several items run concurrently, so responses are routed by path rather than queued in order. */
    private fun MockWebServer.respondWith(items: Map<Long, String>, failingIds: Set<Long> = emptySet()) {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val id = ITEM_PATH.matchEntire(request.url.encodedPath)?.groupValues?.get(1)?.toLong()
                return when {
                    id == null -> MockResponse(code = 404)
                    id in failingIds -> MockResponse(code = 500)
                    else -> MockResponse(body = items[id] ?: "null")
                }
            }
        }
    }

    private companion object {
        val ITEM_PATH = Regex("/v0/item/(\\d+)\\.json")

        // Example item from the official API documentation.
        const val DROPBOX_STORY_JSON = """
            {
              "by": "dhouston",
              "descendants": 71,
              "id": 8863,
              "kids": [8952, 9224],
              "score": 111,
              "time": 1175714200,
              "title": "My YC app: Dropbox - Throw away your USB drive",
              "type": "story",
              "url": "http://www.getdropbox.com/u/2/screencast.html"
            }
        """
    }
}
