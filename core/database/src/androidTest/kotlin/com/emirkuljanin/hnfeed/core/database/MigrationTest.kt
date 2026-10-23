package com.emirkuljanin.hnfeed.core.database

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every schema version must migrate to the current one without losing the offline cache. Room validates the
 * migrated schema against the exported JSON in `core/database/schemas`, so this also catches entity changes that
 * were made without bumping the version or exporting a new schema.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation,
        instrumentation.targetContext.getDatabasePath("migration-test.db"),
        AndroidSQLiteDriver(),
        HnDatabase::class,
    )

    @Test
    fun migrate1To2_keepsTheCachedFeed_andAddsAnEmptyThumbnailsTable() = runTest {
        helper.createDatabase(1).use { connection ->
            connection.execSQL(
                """
                INSERT INTO stories (id, title, author, score, posted_at, url, comment_count, type, fetched_at)
                VALUES (8863, 'My YC app: Dropbox', 'dhouston', 111, 1175714200000, NULL, 71, 'STORY', 0)
                """,
            )
            connection.execSQL("INSERT INTO top_stories (rank, story_id, is_loaded) VALUES (0, 8863, 1)")
        }

        helper.runMigrationsAndValidate(2).use { connection ->
            connection.prepare("SELECT title FROM stories WHERE id = 8863").use { statement ->
                assertTrue(statement.step())
                assertEquals("My YC app: Dropbox", statement.getText(0))
            }
            connection.prepare("SELECT is_loaded FROM top_stories WHERE story_id = 8863").use { statement ->
                assertTrue(statement.step())
                assertEquals(1L, statement.getLong(0))
            }
            connection.prepare("SELECT COUNT(*) FROM thumbnails").use { statement ->
                assertTrue(statement.step())
                assertEquals(0L, statement.getLong(0))
            }
        }
    }
}
