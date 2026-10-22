package com.emirkuljanin.hnfeed.core.database

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.emirkuljanin.hnfeed.core.database.model.StoryEntity
import com.emirkuljanin.hnfeed.core.model.StoryType
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Base for DAO tests: a fresh in-memory database per test, backed by Robolectric's real SQLite. */
@RunWith(RobolectricTestRunner::class)
internal abstract class DatabaseTest {

    protected lateinit var database: HnDatabase

    @BeforeTest
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder<HnDatabase>(ApplicationProvider.getApplicationContext<Context>())
            .setDriver(AndroidSQLiteDriver())
            .build()
    }

    @AfterTest
    fun closeDatabase() {
        database.close()
    }
}

internal fun storyEntity(id: Long, score: Int = 1) = StoryEntity(
    id = id,
    title = "Story $id",
    author = "author$id",
    score = score,
    postedAt = Instant.ofEpochSecond(1_700_000_000 + id),
    url = "https://example.com/$id",
    commentCount = 0,
    type = StoryType.STORY,
    fetchedAt = Instant.ofEpochSecond(1_800_000_000),
)
