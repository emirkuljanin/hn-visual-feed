package com.emirkuljanin.hnfeed.core.database.di

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.emirkuljanin.hnfeed.core.common.dispatchers.IoDispatcher
import com.emirkuljanin.hnfeed.core.database.DatabaseTransactionRunner
import com.emirkuljanin.hnfeed.core.database.HnDatabase
import com.emirkuljanin.hnfeed.core.database.RoomDatabaseTransactionRunner
import com.emirkuljanin.hnfeed.core.database.dao.StoryDao
import com.emirkuljanin.hnfeed.core.database.dao.ThumbnailDao
import com.emirkuljanin.hnfeed.core.database.dao.TopStoryDao
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {

    /**
     * The platform SQLite driver: no extra native library in the APK, and it is what Robolectric runs the DAO tests
     * on. A bundled SQLite would buy a fixed SQLite version, which this schema does not need.
     */
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context, @IoDispatcher ioDispatcher: CoroutineDispatcher): HnDatabase =
        Room.databaseBuilder<HnDatabase>(context, "hn-feed.db")
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(ioDispatcher)
            .build()

    @Provides
    fun storyDao(database: HnDatabase): StoryDao = database.storyDao()

    @Provides
    fun topStoryDao(database: HnDatabase): TopStoryDao = database.topStoryDao()

    @Provides
    fun thumbnailDao(database: HnDatabase): ThumbnailDao = database.thumbnailDao()
}

@Module
@InstallIn(SingletonComponent::class)
internal interface DatabaseBindingsModule {

    @Binds
    fun bindsDatabaseTransactionRunner(impl: RoomDatabaseTransactionRunner): DatabaseTransactionRunner
}
