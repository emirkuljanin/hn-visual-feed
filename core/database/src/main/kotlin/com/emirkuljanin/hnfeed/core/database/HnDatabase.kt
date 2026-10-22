package com.emirkuljanin.hnfeed.core.database

import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.RoomDatabase
import com.emirkuljanin.hnfeed.core.database.dao.StoryDao
import com.emirkuljanin.hnfeed.core.database.dao.TopStoryDao
import com.emirkuljanin.hnfeed.core.database.model.StoryEntity
import com.emirkuljanin.hnfeed.core.database.model.TopStoryEntity
import com.emirkuljanin.hnfeed.core.database.util.InstantConverter

/**
 * Schema changes bump [version] and ship a migration (auto-migration where Room can derive it). Exported schemas
 * live in `core/database/schemas`. There is intentionally no destructive fallback, so a missing migration fails
 * loudly in testing instead of silently wiping users' offline cache.
 */
@Database(
    entities = [StoryEntity::class, TopStoryEntity::class],
    version = 1,
    exportSchema = true,
)
@ColumnTypeConverters(InstantConverter::class)
internal abstract class HnDatabase : RoomDatabase() {
    abstract fun storyDao(): StoryDao
    abstract fun topStoryDao(): TopStoryDao
}
