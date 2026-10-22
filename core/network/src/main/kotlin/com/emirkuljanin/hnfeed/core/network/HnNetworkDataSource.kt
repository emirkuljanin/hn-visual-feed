package com.emirkuljanin.hnfeed.core.network

import com.emirkuljanin.hnfeed.core.network.model.NetworkItem

/**
 * Access to the Hacker News API. Calls are main-safe. Every failure surfaces as an [java.io.IOException]: plain
 * connectivity problems as they come, unusable responses as [HnApiException].
 */
interface HnNetworkDataSource {

    /** Ids of the current top stories, best first (the API returns up to 500). */
    suspend fun topStoryIds(): List<Long>

    /**
     * Fetches [ids] concurrently and returns them in the same order. Ids the API has no item for are left out.
     * The API has no batch endpoint, so this is one request per id; any failure fails the whole call.
     */
    suspend fun items(ids: List<Long>): List<NetworkItem>
}
