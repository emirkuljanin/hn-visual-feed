package com.emirkuljanin.hnfeed.core.common.time.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

/** Classes inject [Clock] instead of calling `Instant.now()`, so tests can fix the time. */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {

    @Provides
    fun clock(): Clock = Clock.systemUTC()
}
