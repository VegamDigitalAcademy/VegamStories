package com.vegamstories.app.testutil

import com.vegamstories.app.data.local.dao.LibraryDao
import com.vegamstories.app.data.local.entity.DownloadEntity
import com.vegamstories.app.data.local.entity.FavoriteEntity
import com.vegamstories.app.data.local.entity.PendingLikeEntity
import com.vegamstories.app.data.local.entity.ReadingProgressEntity
import com.vegamstories.app.data.local.entity.StoryWithStateRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * In-memory [LibraryDao] for the favorite/like methods. The DAO's own logic (toggleFavorite,
 * confirmLike) runs for real on top of these; the SQL itself isn't tested here.
 * Like counts only change for stories in [likeCounts], as an SQL UPDATE only touches existing rows.
 */
class FakeLibraryDao(likeCounts: Map<String, Int> = emptyMap()) : LibraryDao {
    val favorites = mutableMapOf<String, FavoriteEntity>()
    /** Keyed by storyId, like the table's primary key. */
    val pending = linkedMapOf<String, PendingLikeEntity>()
    val likeCounts = likeCounts.toMutableMap()

    override suspend fun isFavorite(storyId: String) = storyId in favorites
    override suspend fun upsertFavorite(entity: FavoriteEntity) { favorites[entity.storyId] = entity }
    override suspend fun deleteFavorite(storyId: String) { favorites.remove(storyId) }

    override suspend fun getPendingLikes() = pending.values.toList()
    override suspend fun upsertPendingLike(entity: PendingLikeEntity) { pending[entity.storyId] = entity }
    override suspend fun deletePendingLike(storyId: String, liked: Boolean): Int =
        if (pending[storyId]?.liked == liked) { pending.remove(storyId); 1 } else 0

    override suspend fun adjustLikeCount(storyId: String, delta: Int) {
        likeCounts.computeIfPresent(storyId) { _, count -> maxOf(count + delta, 0) }
    }
    override suspend fun setLikeCount(storyId: String, likeCount: Int) {
        likeCounts.computeIfPresent(storyId) { _, _ -> likeCount }
    }

    // Not used by these tests.
    override fun observeFavorites(): Flow<List<StoryWithStateRow>> = emptyFlow()
    override fun observeDownloads(): Flow<List<StoryWithStateRow>> = emptyFlow()
    override suspend fun isDownloaded(storyId: String) = false
    override suspend fun upsertDownload(entity: DownloadEntity) = Unit
    override suspend fun deleteDownload(storyId: String) = Unit
    override fun observeHistory(): Flow<List<StoryWithStateRow>> = emptyFlow()
    override fun observeContinueReading(categoryId: String?): Flow<StoryWithStateRow?> = emptyFlow()
    override suspend fun getProgress(storyId: String): ReadingProgressEntity? = null
    override suspend fun upsertProgress(entity: ReadingProgressEntity) = Unit
    override suspend fun clearProgress() = Unit
}
