package com.emirkuljanin.hnfeed.core.imagegen.di

import com.emirkuljanin.hnfeed.core.imagegen.BuildConfig
import com.emirkuljanin.hnfeed.core.imagegen.ChainedThumbnailGenerator
import com.emirkuljanin.hnfeed.core.imagegen.ImageGenerator
import com.emirkuljanin.hnfeed.core.imagegen.ThumbnailGenerator
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
     * Kept short because the cloud is tried first for every story: on a dead network the fallback to on-device art
     * must not wait long.
     */
    @Provides
    @Singleton
    @CloudflareHttpClient
    fun cloudflareHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(15))
        .readTimeout(Duration.ofSeconds(30))
        .callTimeout(Duration.ofSeconds(30))
        .build()

    /** Priority order: the first generator that may handle a title and succeeds wins; on-device never refuses. */
    @Provides
    fun imageGenerators(cloud: CloudflareImageGenerator, onDevice: OnDeviceImageGenerator): List<ImageGenerator> =
        listOf(cloud, onDevice)
}

@Module
@InstallIn(SingletonComponent::class)
internal interface ImageGenBindingsModule {

    @Binds
    fun bindsTextEmbedder(impl: MediaPipeTextEmbedder): TextEmbedder

    @Binds
    fun bindsThumbnailGenerator(impl: ChainedThumbnailGenerator): ThumbnailGenerator
}
