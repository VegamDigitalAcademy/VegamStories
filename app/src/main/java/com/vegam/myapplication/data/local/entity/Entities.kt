package com.vegam.myapplication.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

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
    /** Stored as JSON through [com.vegam.myapplication.data.local.Converters]. */
    val panels: List<PanelLocal>,
)

@Serializable
data class PanelLocal(
    val imageUrl: String,
    val caption: String? = null,
    val dialogues: List<String> = emptyList(),
)

// ---------- User state (kept separate so a server refresh never wipes it) ----------

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val storyId: String,
    val addedAt: Long,
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
