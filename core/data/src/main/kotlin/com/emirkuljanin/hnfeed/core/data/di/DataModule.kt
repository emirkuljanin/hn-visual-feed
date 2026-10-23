package com.emirkuljanin.hnfeed.core.data.di

import com.emirkuljanin.hnfeed.core.data.repository.OfflineFirstStoryDetailsRepository
import com.emirkuljanin.hnfeed.core.data.repository.OfflineFirstTopStoriesRepository
import com.emirkuljanin.hnfeed.core.data.repository.StoryDetailsRepository
import com.emirkuljanin.hnfeed.core.data.repository.TopStoriesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface DataModule {

    @Binds
    fun bindsTopStoriesRepository(impl: OfflineFirstTopStoriesRepository): TopStoriesRepository

    @Binds
    fun bindsStoryDetailsRepository(impl: OfflineFirstStoryDetailsRepository): StoryDetailsRepository
}
