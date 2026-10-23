package com.emirkuljanin.hnfeed.core.data.di

import android.content.Context
import androidx.work.WorkManager
import com.emirkuljanin.hnfeed.core.data.thumbnail.SharedPreferencesUpgradePause
import com.emirkuljanin.hnfeed.core.data.thumbnail.ThumbnailDirectory
import com.emirkuljanin.hnfeed.core.data.thumbnail.ThumbnailScheduler
import com.emirkuljanin.hnfeed.core.data.thumbnail.UpgradePause
import com.emirkuljanin.hnfeed.core.data.thumbnail.WorkManagerThumbnailScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File

@Module
@InstallIn(SingletonComponent::class)
internal object ThumbnailModule {

    /** App-private files (not cache): thumbnails must survive the system clearing caches, for the offline feed. */
    @Provides
    @ThumbnailDirectory
    fun thumbnailDirectory(@ApplicationContext context: Context): File = File(context.filesDir, "thumbnails")

    /** Initialised on demand with the app's Hilt worker factory (see `HnFeedApplication`). */
    @Provides
    fun workManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
}

@Module
@InstallIn(SingletonComponent::class)
internal interface ThumbnailBindingsModule {

    @Binds
    fun bindsThumbnailScheduler(impl: WorkManagerThumbnailScheduler): ThumbnailScheduler

    @Binds
    fun bindsUpgradePause(impl: SharedPreferencesUpgradePause): UpgradePause
}
