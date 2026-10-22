package com.emirkuljanin.hnfeed.core.network.di

import com.emirkuljanin.hnfeed.core.network.HnNetworkDataSource
import com.emirkuljanin.hnfeed.core.network.retrofit.HN_BASE_URL
import com.emirkuljanin.hnfeed.core.network.retrofit.HackerNewsApi
import com.emirkuljanin.hnfeed.core.network.retrofit.RetrofitHnNetworkDataSource
import com.emirkuljanin.hnfeed.core.network.retrofit.hackerNewsApi
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.Call
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

// A feed page is ~20 parallel item requests to one host; OkHttp's default of 5 per host would serialise most of them.
private const val MAX_REQUESTS_PER_HOST = 10

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {

    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply { maxRequestsPerHost = MAX_REQUESTS_PER_HOST })
        .build()

    /**
     * [Lazy] defers building the OkHttpClient (its TLS setup is noticeably slow) from injection, which runs on the
     * main thread, to the first request, which the data source makes on the IO dispatcher.
     */
    @Provides
    @Singleton
    fun hackerNewsApi(okHttpClient: Lazy<OkHttpClient>): HackerNewsApi =
        hackerNewsApi(HN_BASE_URL, Call.Factory { request -> okHttpClient.get().newCall(request) })
}

@Module
@InstallIn(SingletonComponent::class)
internal interface NetworkBindingsModule {

    @Binds
    fun bindsHnNetworkDataSource(impl: RetrofitHnNetworkDataSource): HnNetworkDataSource
}
