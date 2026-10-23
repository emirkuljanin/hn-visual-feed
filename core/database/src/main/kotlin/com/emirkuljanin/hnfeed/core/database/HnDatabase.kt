package com.emirkuljanin.hnfeed.core.database

import androidx.room3.AutoMigration
import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.RoomDatabase
import com.emirkuljanin.hnfeed.core.database.dao.StoryDao
import com.emirkuljanin.hnfeed.core.database.dao.ThumbnailDao
import com.emirkuljanin.hnfeed.core.database.dao.TopStoryDao
import com.emirkuljanin.hnfeed.core.database.model.StoryEntity
import com.emirkuljanin.hnfeed.core.database.model.ThumbnailEntity
import com.emirkuljanin.hnfeed.core.database.model.TopStoryEntity
import com.emirkuljanin.hnfeed.core.database.util.InstantConverter

/**
 * Schema changes bump the version and ship a migration (auto-migration where Room can derive it). Exported schemas
 * live in `core/database/schemas` and are tested in `MigrationTest`. There is intentionally no destructive fallback,
 * so a missing migration fails loudly in testing instead of silently wiping users' offline cache.
 *
 * Versions: 1 stories + top-stories snapshot; 2 adds `thumbnails`.
 *
 * Public only so other modules' tests can build an in-memory instance; production code gets the DAOs and
 * [DatabaseTransactionRunner] through Hilt.
 */
@Database(
    entities = [StoryEntity::class, TopStoryEntity::class, ThumbnailEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@ColumnTypeConverters(InstantConverter::class)
abstract class HnDatabase : RoomDatabase() {
    abstract fun storyDao(): StoryDao
    abstract fun topStoryDao(): TopStoryDao
    abstract fun thumbnailDao(): ThumbnailDao
}
