package com.emirkuljanin.hnfeed.core.imagegen.di

import com.emirkuljanin.hnfeed.core.imagegen.BuildConfig
import com.emirkuljanin.hnfeed.core.imagegen.ThumbnailGenerator
import com.emirkuljanin.hnfeed.core.imagegen.TieredThumbnailGenerator
import com.emirkuljanin.hnfeed.core.imagegen.cloud.CloudflareConfig
import com.emirkuljanin.hnfeed.core.imagegen.cloud.CloudflareHttpClient
import com.emirkuljanin.hnfeed.core.imagegen.cloud.CloudflareImageGenerator
import com.emirkuljanin.hnfeed.core.imagegen.ondevice.MediaPipeTextEmbedder
import com.emirkuljanin.hnfeed.core.imagegen.ondevice.OnDeviceImageGenerator
import com.emirkuljanin.hnfeed.core.imagegen.ondevice.TextEmbedder
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
internal object ImageGenModule {

    @Provides
    fun cloudflareConfig() = CloudflareConfig(
        accountId = BuildConfig.CLOUDFLARE_ACCOUNT_ID,
        apiToken = BuildConfig.CLOUDFLARE_API_TOKEN,
    )

    /**
     * Separate from the HN API client, with timeouts sized for generation (FLUX schnell answers in about 2 s).
     * Kept short: upgrades run a few at a time in the background, and a hung call should not hold up the queue.
     */
    @Provides
    @Singleton
    @CloudflareHttpClient
    fun cloudflareHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(15))
        .readTimeout(Duration.ofSeconds(30))
        .callTimeout(Duration.ofSeconds(30))
        .build()

    /** Previews are on-device; upgrades try the listed generators in priority order (cloud today). */
    @Provides
    fun thumbnailGenerator(onDevice: OnDeviceImageGenerator, cloud: CloudflareImageGenerator): ThumbnailGenerator =
        TieredThumbnailGenerator(preview = onDevice, upgrades = listOf(cloud))
}

@Module
@InstallIn(SingletonComponent::class)
internal interface ImageGenBindingsModule {

    @Binds
    fun bindsTextEmbedder(impl: MediaPipeTextEmbedder): TextEmbedder
}
