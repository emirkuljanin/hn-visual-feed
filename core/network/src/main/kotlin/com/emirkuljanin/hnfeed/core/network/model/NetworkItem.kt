package com.emirkuljanin.hnfeed.core.network.model

import kotlinx.serialization.Serializable

/**
 * An item from the Hacker News API (`/v0/item/{id}.json`): a story, job, poll, poll option or comment.
 * Every field except [id] is optional in the API, so the DTO mirrors that and the data layer decides what is usable.
 *
 * See https://github.com/HackerNews/API#items
 */
@Serializable
data class NetworkItem(
    val id: Long,
    /** One of `story`, `job`, `poll`, `pollopt`, `comment`. */
    val type: String? = null,
    /** Author's username. */
    val by: String? = null,
    /** Creation time, Unix seconds. */
    val time: Long? = null,
    val title: String? = null,
    val url: String? = null,
    /** HTML body of comments, Ask HN posts, polls and jobs. */
    val text: String? = null,
    val score: Int? = null,
    /** Total comment count for stories and polls. */
    val descendants: Int? = null,
    /** Parent item id, for comments. */
    val parent: Long? = null,
    /** Direct child comment ids, in ranked display order. */
    val kids: List<Long> = emptyList(),
    val deleted: Boolean = false,
    val dead: Boolean = false,
)
