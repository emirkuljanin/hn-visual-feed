package com.emirkuljanin.hnfeed.core.common.dispatchers.di

import com.emirkuljanin.hnfeed.core.common.dispatchers.DefaultDispatcher
import com.emirkuljanin.hnfeed.core.common.dispatchers.IoDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// Public (unlike the implementations) so Hilt tests in other modules can replace it with @TestInstallIn.
@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {

    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
