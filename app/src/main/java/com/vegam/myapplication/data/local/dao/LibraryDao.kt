package com.vegam.myapplication.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.vegam.myapplication.data.local.entity.DownloadEntity
import com.vegam.myapplication.data.local.entity.FavoriteEntity
import com.vegam.myapplication.data.local.entity.ReadingProgressEntity
import com.vegam.myapplication.data.local.entity.StoryWithStateRow
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    // ----- Favorites -----
    @Query("$STORY_WITH_STATE_SELECT WHERE f.storyId IS NOT NULL ORDER BY f.addedAt DESC")
    fun observeFavorites(): Flow<List<StoryWithStateRow>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE storyId = :storyId)")
    suspend fun isFavorite(storyId: String): Boolean

    @Upsert
    suspend fun upsertFavorite(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE storyId = :storyId")
    suspend fun deleteFavorite(storyId: String)

    // ----- Downloads -----
    @Query("$STORY_WITH_STATE_SELECT WHERE d.storyId IS NOT NULL ORDER BY d.downloadedAt DESC")
    fun observeDownloads(): Flow<List<StoryWithStateRow>>

    @Query("SELECT EXISTS(SELECT 1 FROM downloads WHERE storyId = :storyId)")
    suspend fun isDownloaded(storyId: String): Boolean

    @Upsert
    suspend fun upsertDownload(entity: DownloadEntity)

    @Query("DELETE FROM downloads WHERE storyId = :storyId")
    suspend fun deleteDownload(storyId: String)

    // ----- Reading progress -----
    @Query("$STORY_WITH_STATE_SELECT WHERE p.storyId IS NOT NULL ORDER BY p.updatedAt DESC")
    fun observeHistory(): Flow<List<StoryWithStateRow>>

    @Query(
        "$STORY_WITH_STATE_SELECT WHERE p.storyId IS NOT NULL AND p.isCompleted = 0 " +
            "AND (:categoryId IS NULL OR s.categoryId = :categoryId) " +
            "ORDER BY p.updatedAt DESC LIMIT 1",
    )
    fun observeContinueReading(categoryId: String?): Flow<StoryWithStateRow?>

    @Query("SELECT * FROM reading_progress WHERE storyId = :storyId LIMIT 1")
    suspend fun getProgress(storyId: String): ReadingProgressEntity?

    @Upsert
    suspend fun upsertProgress(entity: ReadingProgressEntity)

    @Query("DELETE FROM reading_progress")
    suspend fun clearProgress()
}
