package com.vegamstories.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.vegamstories.app.data.local.entity.CategoryEntity
import com.vegamstories.app.data.local.entity.LanguageEntity
import com.vegamstories.app.data.local.entity.StoryEntity
import com.vegamstories.app.data.local.entity.StoryPageEntity
import com.vegamstories.app.data.local.entity.StoryTextEntity
import com.vegamstories.app.data.local.entity.StoryTextRow
import com.vegamstories.app.data.local.entity.StoryWithStateRow
import kotlinx.coroutines.flow.Flow

/** Shared SELECT that joins a story with its category, favorite, download and progress rows. */
internal const val STORY_WITH_STATE_SELECT = """
    SELECT s.*,
        c.name AS categoryName,
        EXISTS(SELECT 1 FROM story_texts t WHERE t.storyId = s.id) AS hasText,
        (f.storyId IS NOT NULL) AS isFavorite,
        (d.storyId IS NOT NULL) AS isDownloaded,
        p.lastPageIndex AS progressLastPageIndex,
        p.totalPages AS progressTotalPages,
        p.isCompleted AS progressIsCompleted,
        p.updatedAt AS progressUpdatedAt
    FROM stories s
    LEFT JOIN categories c ON c.id = s.categoryId
    LEFT JOIN favorites f ON f.storyId = s.id
    LEFT JOIN downloads d ON d.storyId = s.id
    LEFT JOIN reading_progress p ON p.storyId = s.id
"""

@Dao
interface StoryDao {

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("$STORY_WITH_STATE_SELECT ORDER BY s.publishedAt DESC")
    fun observeAllStories(): Flow<List<StoryWithStateRow>>

    @Query("$STORY_WITH_STATE_SELECT WHERE s.categoryId = :categoryId ORDER BY s.episodeNumber ASC")
    fun observeStoriesByCategory(categoryId: String): Flow<List<StoryWithStateRow>>

    @Query("$STORY_WITH_STATE_SELECT WHERE s.id = :storyId LIMIT 1")
    fun observeStory(storyId: String): Flow<StoryWithStateRow?>

    @Query(
        "$STORY_WITH_STATE_SELECT WHERE s.title LIKE '%' || :query || '%' " +
            "OR s.summary LIKE '%' || :query || '%' OR c.name LIKE '%' || :query || '%' " +
            "ORDER BY s.publishedAt DESC",
    )
    fun searchStories(query: String): Flow<List<StoryWithStateRow>>

    @Query("SELECT * FROM story_pages WHERE storyId = :storyId ORDER BY pageIndex ASC")
    fun observePages(storyId: String): Flow<List<StoryPageEntity>>

    @Query("SELECT * FROM story_pages WHERE storyId = :storyId ORDER BY pageIndex ASC")
    suspend fun getPages(storyId: String): List<StoryPageEntity>

    /** The story's texts in the server's language order; languages not loaded yet go last. */
    @Query(
        "SELECT t.*, l.nativeName AS nativeName, l.sortOrder AS sortOrder FROM story_texts t " +
            "LEFT JOIN languages l ON l.code = t.language WHERE t.storyId = :storyId " +
            "ORDER BY l.sortOrder IS NULL, l.sortOrder, t.language",
    )
    fun observeTexts(storyId: String): Flow<List<StoryTextRow>>

    @Query("SELECT * FROM stories WHERE id = :storyId LIMIT 1")
    suspend fun getStory(storyId: String): StoryEntity?

    @Query(
        "SELECT id FROM stories WHERE categoryId = :categoryId AND episodeNumber > :episodeNumber " +
            "ORDER BY episodeNumber ASC LIMIT 1",
    )
    suspend fun getNextStoryId(categoryId: String, episodeNumber: Int): String?

    @Upsert
    suspend fun upsertCategories(categories: List<CategoryEntity>)

    @Upsert
    suspend fun upsertStories(stories: List<StoryEntity>)

    @Upsert
    suspend fun upsertPages(pages: List<StoryPageEntity>)

    @Query("DELETE FROM story_pages WHERE storyId IN (:storyIds)")
    suspend fun deletePagesFor(storyIds: List<String>)

    @Upsert
    suspend fun upsertTexts(texts: List<StoryTextEntity>)

    @Upsert
    suspend fun upsertLanguages(languages: List<LanguageEntity>)

    @Query("DELETE FROM languages WHERE code NOT IN (:keepCodes)")
    suspend fun deleteLanguagesExcept(keepCodes: List<String>)

    @Query("DELETE FROM story_texts WHERE storyId IN (:storyIds)")
    suspend fun deleteTextsFor(storyIds: List<String>)

    /** Pages and texts go too (ON DELETE CASCADE). Favorites/progress rows stay but are never shown. */
    @Query("DELETE FROM stories WHERE id NOT IN (:keepIds)")
    suspend fun deleteStoriesExcept(keepIds: List<String>)

    @Query("DELETE FROM categories WHERE id NOT IN (:keepIds)")
    suspend fun deleteCategoriesExcept(keepIds: List<String>)

    /**
     * Makes the local cache match the server, atomically so the UI never sees a half-written
     * catalogue. Stories unpublished or deleted on the server (and the old dummy data) are removed.
     */
    @Transaction
    suspend fun replaceContent(
        categories: List<CategoryEntity>,
        stories: List<StoryEntity>,
        pages: List<StoryPageEntity>,
        texts: List<StoryTextEntity>,
        /** Empty when the server didn't send them: then the saved list is kept. */
        languages: List<LanguageEntity>,
    ) {
        deleteStoriesExcept(stories.map { it.id })
        deleteCategoriesExcept(categories.map { it.id })
        upsertCategories(categories)
        upsertStories(stories)
        deletePagesFor(stories.map { it.id })
        upsertPages(pages)
        deleteTextsFor(stories.map { it.id })
        upsertTexts(texts)
        if (languages.isNotEmpty()) {
            deleteLanguagesExcept(languages.map { it.code })
            upsertLanguages(languages)
        }
    }
}
