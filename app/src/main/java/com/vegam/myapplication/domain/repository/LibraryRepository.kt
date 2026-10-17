package com.vegam.myapplication.domain.repository

import com.vegam.myapplication.domain.model.Story
import kotlinx.coroutines.flow.Flow

/** The user's own state: favorites, downloads and reading history. Lives only on the device. */
interface LibraryRepository {
    fun observeFavorites(): Flow<List<Story>>

    fun observeDownloads(): Flow<List<Story>>

    fun observeReadingHistory(): Flow<List<Story>>

    /** Most recently opened, unfinished story (optionally within one category). */
    fun observeContinueReading(categoryId: String?): Flow<Story?>

    /** @return the new favorite state. */
    suspend fun toggleFavorite(storyId: String): Boolean

    /** @return the new downloaded state. */
    suspend fun toggleDownload(storyId: String): Boolean

    suspend fun saveProgress(storyId: String, pageIndex: Int, totalPages: Int)

    suspend fun clearReadingHistory()
}
