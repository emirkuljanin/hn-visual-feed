package com.emirkuljanin.hnfeed.core.data.thumbnail

import com.emirkuljanin.hnfeed.core.common.dispatchers.IoDispatcher
import com.emirkuljanin.hnfeed.core.model.ThumbnailSource
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** The directory generated thumbnails live in (app-private storage, kept across launches; see the data module). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class ThumbnailDirectory

/**
 * Thumbnail files on disk. Each version of a story's image has its own name (`<id>-on_device.webp`,
 * `<id>-cloud.webp`), so an upgrade never overwrites a file the UI may be reading, and image caches keyed by path
 * never show a stale picture. Writes are atomic (temp file synced to disk, then renamed), so neither a crash nor a
 * power loss can leave a half-written image behind. Main-safe: all I/O runs on the IO dispatcher.
 */
internal class ThumbnailStore @Inject constructor(
    @ThumbnailDirectory private val directory: File,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    fun path(fileName: String): String = File(directory, fileName).absolutePath

    /** Writes [webp] for [storyId] and returns the file name to store in the database. */
    suspend fun write(storyId: Long, source: ThumbnailSource, webp: ByteArray): String = withContext(ioDispatcher) {
        // mkdirs() is false when a parallel write created the directory first, so check the result, not the call.
        if (!directory.mkdirs() && !directory.isDirectory) throw IOException("Cannot create $directory")
        val fileName = "$storyId-${source.name.lowercase()}.webp"
        val target = File(directory, fileName)
        val temp = File(directory, "$fileName$TEMP_SUFFIX")
        FileOutputStream(temp).use { out ->
            out.write(webp)
            out.fd.sync()
        }
        if (!temp.renameTo(target)) {
            // renameTo does not replace an existing file on every platform.
            target.delete()
            if (!temp.renameTo(target)) {
                temp.delete()
                throw IOException("Cannot move thumbnail into place: $target")
            }
        }
        fileName
    }

    suspend fun delete(fileName: String) {
        withContext(ioDispatcher) { File(directory, fileName).delete() }
    }

    /**
     * Deletes files not in [keep] (orphans, interrupted writes) that were last modified before [modifiedBefore];
     * returns how many. The age limit protects a file that a concurrent writer has just written but whose database
     * row is not committed yet.
     */
    suspend fun deleteAllExcept(keep: Set<String>, modifiedBefore: Instant): Int = withContext(ioDispatcher) {
        val orphans = directory.listFiles().orEmpty().filter {
            it.isFile && it.name !in keep && it.lastModified() < modifiedBefore.toEpochMilli()
        }
        orphans.count { it.delete() }
    }

    private companion object {
        const val TEMP_SUFFIX = ".tmp"
    }
}
