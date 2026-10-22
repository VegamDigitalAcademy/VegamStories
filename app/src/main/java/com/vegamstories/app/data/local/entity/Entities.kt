package com.vegamstories.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// ---------- Server content (cache; replaced on every refresh) ----------

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "stories",
    indices = [Index("categoryId"), Index("publishedAt")],
)
data class StoryEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val episodeNumber: Int,
    val title: String,
    val summary: String,
    val coverImageUrl: String,
    val pageCount: Int,
    val publishedAt: Long,
    val videoTitle: String?,
    val videoDescription: String?,
    val videoThumbnailUrl: String?,
    val videoUrl: String?,
    /** Total likes from the server; bumped locally right away when this device likes/unlikes. */
    @ColumnInfo(defaultValue = "0") val likeCount: Int,
)

@Entity(
    tableName = "story_pages",
    primaryKeys = ["storyId", "pageIndex"],
    foreignKeys = [
        ForeignKey(
            entity = StoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["storyId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class StoryPageEntity(
    val storyId: String,
    val pageIndex: Int,
    /** The whole page as one image (panels, captions and speech bubbles are drawn into it). */
    val imageUrl: String,
)

// ---------- User state (kept separate so a server refresh never wipes it) ----------

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val storyId: String,
    val addedAt: Long,
)

/**
 * A like/unlike the server hasn't confirmed yet (offline, or the request failed).
 * Sent again by LikeSyncer on the next like or refresh; removed once the server accepts it.
 */
@Entity(tableName = "pending_likes")
data class PendingLikeEntity(
    @PrimaryKey val storyId: String,
    val liked: Boolean,
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val storyId: String,
    val downloadedAt: Long,
)

@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    @PrimaryKey val storyId: String,
    val lastPageIndex: Int,
    val totalPages: Int,
    val isCompleted: Boolean,
    val updatedAt: Long,
)
