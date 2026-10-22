package com.emirkuljanin.hnfeed.core.network.retrofit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import retrofit2.http.GET
import retrofit2.http.Path

internal const val HN_BASE_URL = "https://hacker-news.firebaseio.com/"

internal val NetworkJson = Json { ignoreUnknownKeys = true }

/** Retrofit definition of the official Hacker News Firebase API. */
internal interface HackerNewsApi {

    @GET("v0/topstories.json")
    suspend fun topStoryIds(): List<Long>

    /**
     * The API answers unknown ids with a literal JSON `null`, which a typed converter cannot express (it only sees
     * the Java type, which loses Kotlin nullability). Returning [JsonElement] lets the data source decode it.
     */
    @GET("v0/item/{id}.json")
    suspend fun item(@Path("id") id: Long): JsonElement
}

internal fun hackerNewsApi(baseUrl: String, callFactory: Call.Factory): HackerNewsApi = Retrofit.Builder()
    .baseUrl(baseUrl)
    .callFactory(callFactory)
    .addConverterFactory(NetworkJson.asConverterFactory("application/json".toMediaType()))
    .build()
    .create()
