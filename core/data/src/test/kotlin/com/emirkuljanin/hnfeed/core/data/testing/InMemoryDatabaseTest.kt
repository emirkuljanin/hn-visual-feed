package com.emirkuljanin.hnfeed.core.data.testing

import android.content.Context
import androidx.paging.PagingSource.LoadParams
import androidx.paging.PagingSource.LoadResult
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.emirkuljanin.hnfeed.core.database.DatabaseTransactionRunner
import com.emirkuljanin.hnfeed.core.database.HnDatabase
import com.emirkuljanin.hnfeed.core.database.RoomDatabaseTransactionRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Base for data-layer tests that need the real schema and SQL: a fresh in-memory database per test (Robolectric's
 * real SQLite), the production transaction runner and a fixed clock. The network is always the
 * [FakeHnNetworkDataSource].
 */
@RunWith(RobolectricTestRunner::class)
internal abstract class InMemoryDatabaseTest {

    protected lateinit var database: HnDatabase
    protected val storyDao get() = database.storyDao()
    protected val topStoryDao get() = database.topStoryDao()
    protected val transaction: DatabaseTransactionRunner by lazy { RoomDatabaseTransactionRunner(database) }
    protected val now: Instant = Instant.parse("2026-10-23T10:00:00Z")
    protected val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

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

    /** Ids currently visible in the feed, read through the same query the UI uses. */
    protected suspend fun feedIds(): List<Long> {
        val result = storyDao.topStoriesPagingSource().load(LoadParams.Refresh(key = null, loadSize = 500, false))
        return (result as LoadResult.Page).data.map { it.story.id }
    }
}
