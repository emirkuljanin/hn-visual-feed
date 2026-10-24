package com.emirkuljanin.hnfeed.core.database.model

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
import com.emirkuljanin.hnfeed.core.model.Story
import com.emirkuljanin.hnfeed.core.model.Thumbnail
import com.emirkuljanin.hnfeed.core.model.ThumbnailSource
import java.time.Instant

/**
 * A story's current thumbnail. A row exists once the on-device preview is written ([fileName] is never empty);
 * [upgradeState] tracks whether a better (cloud) image should still be attempted.
 *
 * Rows go away with their story (cascade), e.g. when a dead story is removed or old stories are evicted; image files
 * no row references are swept up separately.
 */
@Entity(
    tableName = "thumbnails",
    foreignKeys = [
        ForeignKey(
            entity = StoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["story_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ThumbnailEntity(
    @PrimaryKey @ColumnInfo(name = "story_id") val storyId: Long,
    /** File name inside the thumbnails directory (not a path, so the directory can move). */
    @ColumnInfo(name = "file_name") val fileName: String,
    val source: ThumbnailSource,
    @ColumnInfo(name = "upgrade_state") val upgradeState: UpgradeState,
    /** Transient upgrade failures so far; after a few the upgrade is given up. */
    @ColumnInfo(name = "upgrade_attempts") val upgradeAttempts: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

enum class UpgradeState {
    /** A better image should still be attempted. */
    PENDING,

    /** Upgraded: the current image is the best available. */
    DONE,

    /** No upgrade applies (no cloud credentials, or the title is kept off the cloud). */
    NOT_APPLICABLE,

    /** The provider refused for good (moderation, credentials). */
    REJECTED,

    /** Failed transiently too often. */
    GAVE_UP,
}

/** A story that needs a thumbnail (preview or upgrade), with what the worker needs to make it. */
data class ThumbnailWork(
    @ColumnInfo(name = "story_id") val storyId: Long,
    val title: String,
    /** The current image, if any (for an upgrade, the preview it replaces). */
    @ColumnInfo(name = "file_name") val fileName: String?,
    @ColumnInfo(name = "upgrade_attempts") val upgradeAttempts: Int,
    /**
     * When the row was last written (`null` for a story without a thumbnail). For a pending upgrade with attempts,
     * that is when its last failure was counted: every other upgrade write leaves the pending state.
     */
    @ColumnInfo(name = "updated_at") val updatedAt: Instant?,
)

/** A feed row: the story and, once generated, its thumbnail. */
data class FeedStoryEntity(
    @Embedded val story: StoryEntity,
    @ColumnInfo(name = "thumbnail_file") val thumbnailFile: String?,
    @ColumnInfo(name = "thumbnail_source") val thumbnailSource: ThumbnailSource?,
)

/** [thumbnailPath] resolves a stored file name to an absolute path (the directory belongs to the data layer). */
fun FeedStoryEntity.asExternalModel(thumbnailPath: (fileName: String) -> String): Story {
    val thumbnail = if (thumbnailFile != null && thumbnailSource != null) {
        Thumbnail(filePath = thumbnailPath(thumbnailFile), source = thumbnailSource)
    } else {
        null
    }
    return story.asExternalModel().copy(thumbnail = thumbnail)
}
