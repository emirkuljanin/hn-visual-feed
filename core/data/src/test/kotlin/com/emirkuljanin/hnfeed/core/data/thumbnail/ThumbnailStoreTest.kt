package com.emirkuljanin.hnfeed.core.data.thumbnail

import com.emirkuljanin.hnfeed.core.model.ThumbnailSource
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class ThumbnailStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val directory by lazy { File(folder.root, "thumbnails") }

    @Test
    fun write_createsTheDirectory_namesTheFileBySource_andLeavesNoTempFile() = runTest {
        val store = store()

        val preview = store.write(42, ThumbnailSource.ON_DEVICE, byteArrayOf(1, 2))
        val cloud = store.write(42, ThumbnailSource.CLOUD, byteArrayOf(3))

        assertEquals("42-on_device.webp", preview)
        assertEquals("42-cloud.webp", cloud)
        assertContentEquals(byteArrayOf(1, 2), File(store.path(preview)).readBytes())
        assertEquals(setOf(preview, cloud), directory.list()!!.toSet())
    }

    @Test
    fun write_replacesAnExistingFileOfTheSameName() = runTest {
        val store = store()
        store.write(1, ThumbnailSource.ON_DEVICE, byteArrayOf(1))

        val name = store.write(1, ThumbnailSource.ON_DEVICE, byteArrayOf(9))

        assertContentEquals(byteArrayOf(9), File(store.path(name)).readBytes())
    }

    @Test
    fun deleteAllExcept_removesOldUnreferencedFiles_butSparesKeptAndRecentOnes() = runTest {
        val store = store()
        val kept = store.write(1, ThumbnailSource.ON_DEVICE, byteArrayOf(1))
        val orphan = store.write(2, ThumbnailSource.ON_DEVICE, byteArrayOf(2))
        val interrupted = File(directory, "3-cloud.webp.tmp").apply { writeBytes(byteArrayOf(3)) }
        val justWritten = store.write(4, ThumbnailSource.CLOUD, byteArrayOf(4))
        val old = Instant.parse("2026-01-01T00:00:00Z")
        listOf(File(store.path(kept)), File(store.path(orphan)), interrupted).forEach {
            it.setLastModified(old.toEpochMilli())
        }

        val deleted = store.deleteAllExcept(keep = setOf(kept), modifiedBefore = old.plusSeconds(60))

        assertEquals(2, deleted)
        assertTrue(File(store.path(kept)).exists())
        assertFalse(File(store.path(orphan)).exists())
        assertFalse(interrupted.exists())
        assertTrue(File(store.path(justWritten)).exists(), "A file newer than the grace period must survive")
    }

    private fun TestScope.store() = ThumbnailStore(directory, StandardTestDispatcher(testScheduler))
}
