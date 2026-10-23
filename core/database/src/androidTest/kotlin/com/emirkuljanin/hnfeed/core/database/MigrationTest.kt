package com.emirkuljanin.hnfeed.core.database

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
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
    private val databaseName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation,
        instrumentation.targetContext.getDatabasePath(databaseName),
        AndroidSQLiteDriver(),
        HnDatabase::class,
    )

    /** The helper does not delete the file between tests, and creating a version fails if it already exists. */
    @Before
    fun deleteDatabase() {
        instrumentation.targetContext.deleteDatabase(databaseName)
    }

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

    @Test
    fun migrate2To3_keepsStoriesAndThumbnails_addsStoryTextAndAnEmptyCommentsTable() = runTest {
        helper.createDatabase(2).use { connection ->
            connection.execSQL(
                """
                INSERT INTO stories (id, title, author, score, posted_at, url, comment_count, type, fetched_at)
                VALUES (8863, 'My YC app: Dropbox', 'dhouston', 111, 1175714200000, NULL, 71, 'STORY', 0)
                """,
            )
            connection.execSQL(
                """
                INSERT INTO thumbnails (story_id, file_name, source, upgrade_state, upgrade_attempts, updated_at)
                VALUES (8863, '8863-cloud.webp', 'CLOUD', 'DONE', 0, 0)
                """,
            )
        }

        helper.runMigrationsAndValidate(3).use { connection ->
            connection.prepare("SELECT title, text FROM stories WHERE id = 8863").use { statement ->
                assertTrue(statement.step())
                assertEquals("My YC app: Dropbox", statement.getText(0))
                assertTrue(statement.isNull(1))
            }
            connection.prepare("SELECT file_name FROM thumbnails WHERE story_id = 8863").use { statement ->
                assertTrue(statement.step())
                assertEquals("8863-cloud.webp", statement.getText(0))
            }
            connection.prepare("SELECT COUNT(*) FROM comments").use { statement ->
                assertTrue(statement.step())
                assertEquals(0L, statement.getLong(0))
            }
        }
    }
}
