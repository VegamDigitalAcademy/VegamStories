package com.vegamstories.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.vegamstories.app.data.local.entity.BookmarkEntity
import com.vegamstories.app.data.local.entity.DownloadEntity
import com.vegamstories.app.data.local.entity.FavoriteEntity
import com.vegamstories.app.data.local.entity.PendingLikeEntity
import com.vegamstories.app.data.local.entity.ReadingProgressEntity
import com.vegamstories.app.data.local.entity.StoryWithStateRow
import kotlinx.coroutines.flow.Flow

/**
 * The user's library. Story lists show each story in `lang` when it has that version, else in
 * another language it has (see STORY_WITH_STATE_SELECT): saved stories never disappear.
 */
@Dao
interface LibraryDao {

    // ----- Favorites -----
    @Query("$STORY_WITH_STATE_SELECT WHERE f.storyId IS NOT NULL ORDER BY f.addedAt DESC")
    fun observeFavorites(lang: String): Flow<List<StoryWithStateRow>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE storyId = :storyId)")
    suspend fun isFavorite(storyId: String): Boolean

    @Upsert
    suspend fun upsertFavorite(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE storyId = :storyId")
    suspend fun deleteFavorite(storyId: String)

    /**
     * The heart: saves the favorite, moves the like count by one and queues the like for the
     * server (LikeSyncer), all at once. @return the new state.
     */
    @Transaction
    suspend fun toggleFavorite(storyId: String, addedAt: Long): Boolean {
        val liked = !isFavorite(storyId)
        if (liked) upsertFavorite(FavoriteEntity(storyId, addedAt)) else deleteFavorite(storyId)
        adjustLikeCount(storyId, if (liked) 1 else -1)
        upsertPendingLike(PendingLikeEntity(storyId, liked))
        return liked
    }

    // ----- Likes waiting to be sent to the server -----
    @Query("SELECT * FROM pending_likes")
    suspend fun getPendingLikes(): List<PendingLikeEntity>

    @Upsert
    suspend fun upsertPendingLike(entity: PendingLikeEntity)

    /** Only removes it if the user hasn't changed their mind since. @return rows deleted. */
    @Query("DELETE FROM pending_likes WHERE storyId = :storyId AND liked = :liked")
    suspend fun deletePendingLike(storyId: String, liked: Boolean): Int

    @Query("UPDATE stories SET likeCount = MAX(likeCount + :delta, 0) WHERE id = :storyId")
    suspend fun adjustLikeCount(storyId: String, delta: Int)

    @Query("UPDATE stories SET likeCount = :likeCount WHERE id = :storyId")
    suspend fun setLikeCount(storyId: String, likeCount: Int)

    /**
     * The server accepted a like/unlike. Takes the server's count, unless the user tapped the
     * heart again in the meantime (then that newer change is still queued and wins).
     */
    @Transaction
    suspend fun confirmLike(storyId: String, liked: Boolean, serverCount: Int) {
        if (deletePendingLike(storyId, liked) > 0) setLikeCount(storyId, serverCount)
    }

    // ----- Downloads -----
    @Query("$STORY_WITH_STATE_SELECT WHERE d.storyId IS NOT NULL ORDER BY d.downloadedAt DESC")
    fun observeDownloads(lang: String): Flow<List<StoryWithStateRow>>

    @Query("SELECT EXISTS(SELECT 1 FROM downloads WHERE storyId = :storyId)")
    suspend fun isDownloaded(storyId: String): Boolean

    @Upsert
    suspend fun upsertDownload(entity: DownloadEntity)

    @Query("DELETE FROM downloads WHERE storyId = :storyId")
    suspend fun deleteDownload(storyId: String)

    // ----- Reading progress -----
    @Query("$STORY_WITH_STATE_SELECT WHERE p.storyId IS NOT NULL ORDER BY p.updatedAt DESC")
    fun observeHistory(lang: String): Flow<List<StoryWithStateRow>>

    @Query(
        "$STORY_WITH_STATE_SELECT WHERE p.storyId IS NOT NULL AND p.isCompleted = 0 " +
            "AND (:categoryId IS NULL OR s.categoryId = :categoryId) " +
            "ORDER BY p.updatedAt DESC LIMIT 1",
    )
    fun observeContinueReading(categoryId: String?, lang: String): Flow<StoryWithStateRow?>

    @Query("SELECT * FROM reading_progress WHERE storyId = :storyId LIMIT 1")
    suspend fun getProgress(storyId: String): ReadingProgressEntity?

    @Upsert
    suspend fun upsertProgress(entity: ReadingProgressEntity)

    @Query("DELETE FROM reading_progress")
    suspend fun clearProgress()

    // ----- Bookmarks -----
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE storyId = :storyId")
    fun observeBookmarks(storyId: String): Flow<List<BookmarkEntity>>

    @Upsert
    suspend fun upsertBookmark(entity: BookmarkEntity)

    @Delete
    suspend fun deleteBookmarks(entities: List<BookmarkEntity>)
}
