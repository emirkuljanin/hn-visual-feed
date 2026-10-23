package com.emirkuljanin.hnfeed.core.imagegen.cloud

import javax.inject.Qualifier

/** Cloudflare Workers AI account and token; blank when not configured (see the module's build file). */
internal data class CloudflareConfig(
    val accountId: String,
    val apiToken: String,
    val baseUrl: String = "https://api.cloudflare.com/client/v4/",
) {
    val isConfigured: Boolean get() = accountId.isNotBlank() && apiToken.isNotBlank()

    override fun toString() = "CloudflareConfig(accountId=$accountId, apiToken=<redacted>, baseUrl=$baseUrl)"
}

/** The OkHttpClient for image generation: generation takes seconds, so it has its own, longer timeouts. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class CloudflareHttpClient
