package com.emirkuljanin.hnfeed.core.data.di

import com.emirkuljanin.hnfeed.core.data.repository.OfflineFirstStoryDetailsRepository
import com.emirkuljanin.hnfeed.core.data.repository.OfflineFirstTopStoriesRepository
import com.emirkuljanin.hnfeed.core.data.repository.StoryDetailsRepository
import com.emirkuljanin.hnfeed.core.data.repository.TopStoriesRepository
import com.emirkuljanin.hnfeed.core.data.util.ConnectivityManagerNetworkMonitor
import com.emirkuljanin.hnfeed.core.data.util.NetworkMonitor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Public only so app-level UI tests can replace it (`@TestInstallIn(replaces = [DataModule::class])`) with fakes;
 * its implementations stay internal.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    internal abstract fun bindsTopStoriesRepository(impl: OfflineFirstTopStoriesRepository): TopStoriesRepository

    @Binds
    internal abstract fun bindsStoryDetailsRepository(impl: OfflineFirstStoryDetailsRepository): StoryDetailsRepository

    @Binds
    internal abstract fun bindsNetworkMonitor(impl: ConnectivityManagerNetworkMonitor): NetworkMonitor
}
