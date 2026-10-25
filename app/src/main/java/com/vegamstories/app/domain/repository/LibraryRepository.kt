package com.vegamstories.app.domain.repository

import com.vegamstories.app.domain.model.Bookmark
import com.vegamstories.app.domain.model.BookmarkItem
import com.vegamstories.app.domain.model.Story
import kotlinx.coroutines.flow.Flow

/** The user's own state: favorites (likes, also counted on the server), downloads and reading history. */
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

    /** Every bookmark, newest first, with its story. Stories no longer in the catalogue are left out. */
    fun observeBookmarks(): Flow<List<BookmarkItem>>

    /** The bookmarks in one story, in every language and format. */
    fun observeBookmarks(storyId: String): Flow<List<Bookmark>>

    suspend fun addBookmark(bookmark: Bookmark)

    suspend fun removeBookmarks(bookmarks: List<Bookmark>)
}
