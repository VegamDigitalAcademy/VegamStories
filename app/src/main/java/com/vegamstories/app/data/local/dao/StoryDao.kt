package com.vegamstories.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.vegamstories.app.data.local.entity.CategoryEntity
import com.vegamstories.app.data.local.entity.CategoryNameEntity
import com.vegamstories.app.data.local.entity.CategoryRow
import com.vegamstories.app.data.local.entity.LanguageEntity
import com.vegamstories.app.data.local.entity.StoryEntity
import com.vegamstories.app.data.local.entity.StoryLanguageRow
import com.vegamstories.app.data.local.entity.StoryPageEntity
import com.vegamstories.app.data.local.entity.StoryTranslationEntity
import com.vegamstories.app.data.local.entity.StoryWithStateRow
import kotlinx.coroutines.flow.Flow

/**
 * Shared SELECT: each story in ONE language version, with its category name and the user's
 * favorite, download and progress rows. The version is the one in `:lang` if the story has it,
 * else its first language in the server's order. Queries that want only stories that really are
 * in `:lang` (Home, Search) add [ONLY_IN_LANG]; the library (Favorites, Downloads, History) doesn't,
 * so saved stories never disappear when the language changes.
 * Subqueries refer to the outer tables only in WHERE: older SQLite (Android 7) rejects them in ORDER BY.
 */
internal const val STORY_WITH_STATE_SELECT = """
    SELECT s.*,
        t.language AS language,
        t.title AS title,
        t.summary AS summary,
        t.coverImageUrl AS coverImageUrl,
        t.pageCount AS pageCount,
        (t.body IS NOT NULL) AS hasText,
        t.videoTitle AS videoTitle,
        t.videoDescription AS videoDescription,
        t.videoThumbnailUrl AS videoThumbnailUrl,
        t.videoUrl AS videoUrl,
        COALESCE(
            (SELECT cn.name FROM category_names cn WHERE cn.categoryId = s.categoryId AND cn.language = t.language),
            (SELECT cn.name FROM category_names cn LEFT JOIN languages cl ON cl.code = cn.language
                WHERE cn.categoryId = s.categoryId
                ORDER BY cl.sortOrder IS NULL, cl.sortOrder, cn.language LIMIT 1)
        ) AS categoryName,
        (f.storyId IS NOT NULL) AS isFavorite,
        (d.storyId IS NOT NULL) AS isDownloaded,
        p.lastPageIndex AS progressLastPageIndex,
        p.totalPages AS progressTotalPages,
        p.isCompleted AS progressIsCompleted,
        p.updatedAt AS progressUpdatedAt
    FROM stories s
    JOIN story_translations t ON t.storyId = s.id AND t.language = (
        SELECT bt.language FROM story_translations bt LEFT JOIN languages bl ON bl.code = bt.language
        WHERE bt.storyId = s.id
        ORDER BY (bt.language = :lang) DESC, bl.sortOrder IS NULL, bl.sortOrder, bt.language
        LIMIT 1)
    LEFT JOIN favorites f ON f.storyId = s.id
    LEFT JOIN downloads d ON d.storyId = s.id
    LEFT JOIN reading_progress p ON p.storyId = s.id
"""

/** Keeps only stories that have a version in `:lang`. */
internal const val ONLY_IN_LANG = "t.language = :lang"

@Dao
interface StoryDao {

    /** Categories with stories in [lang], named in [lang] (or another language the category has). */
    @Query(
        """
        SELECT c.id AS id, c.sortOrder AS sortOrder,
            (SELECT cn.name FROM category_names cn LEFT JOIN languages cl ON cl.code = cn.language
                WHERE cn.categoryId = c.id
                ORDER BY (cn.language = :lang) DESC, cl.sortOrder IS NULL, cl.sortOrder, cn.language
                LIMIT 1) AS name
        FROM categories c
        WHERE EXISTS (SELECT 1 FROM stories s JOIN story_translations t ON t.storyId = s.id
            WHERE s.categoryId = c.id AND t.language = :lang)
        ORDER BY c.sortOrder ASC
        """,
    )
    fun observeCategories(lang: String): Flow<List<CategoryRow>>

    @Query("$STORY_WITH_STATE_SELECT WHERE $ONLY_IN_LANG ORDER BY s.publishedAt DESC")
    fun observeAllStories(lang: String): Flow<List<StoryWithStateRow>>

    @Query("$STORY_WITH_STATE_SELECT WHERE $ONLY_IN_LANG AND s.categoryId = :categoryId ORDER BY s.episodeNumber ASC")
    fun observeStoriesByCategory(categoryId: String, lang: String): Flow<List<StoryWithStateRow>>

    /** One story in [lang], or in another language it has. */
    @Query("$STORY_WITH_STATE_SELECT WHERE s.id = :storyId LIMIT 1")
    fun observeStory(storyId: String, lang: String): Flow<StoryWithStateRow?>

    @Query("$STORY_WITH_STATE_SELECT WHERE s.id = :storyId LIMIT 1")
    suspend fun getStoryRow(storyId: String, lang: String): StoryWithStateRow?

    @Query(
        "$STORY_WITH_STATE_SELECT WHERE $ONLY_IN_LANG AND (t.title LIKE '%' || :query || '%' " +
            "OR t.summary LIKE '%' || :query || '%' OR EXISTS (SELECT 1 FROM category_names qn " +
            "WHERE qn.categoryId = s.categoryId AND qn.name LIKE '%' || :query || '%')) " +
            "ORDER BY s.publishedAt DESC",
    )
    fun searchStories(query: String, lang: String): Flow<List<StoryWithStateRow>>

    @Query("SELECT * FROM story_pages WHERE storyId = :storyId AND language = :language ORDER BY pageIndex ASC")
    fun observePages(storyId: String, language: String): Flow<List<StoryPageEntity>>

    @Query("SELECT * FROM story_pages WHERE storyId = :storyId AND language = :language ORDER BY pageIndex ASC")
    suspend fun getPages(storyId: String, language: String): List<StoryPageEntity>

    /** The story's text in [language]; null if that version has no text. */
    @Query("SELECT body FROM story_translations WHERE storyId = :storyId AND language = :language")
    fun observeText(storyId: String, language: String): Flow<String?>

    /** The languages a story is in, in the server's order (for the reader's language switch). */
    @Query(
        "SELECT t.language AS code, l.nativeName AS nativeName FROM story_translations t " +
            "LEFT JOIN languages l ON l.code = t.language WHERE t.storyId = :storyId " +
            "ORDER BY l.sortOrder IS NULL, l.sortOrder, t.language",
    )
    fun observeStoryLanguages(storyId: String): Flow<List<StoryLanguageRow>>

    @Query("SELECT * FROM languages ORDER BY sortOrder ASC, code ASC")
    fun observeLanguages(): Flow<List<LanguageEntity>>

    @Query("SELECT * FROM stories WHERE id = :storyId LIMIT 1")
    suspend fun getStory(storyId: String): StoryEntity?

    /** Next episode in the same category that has a version in [lang]. */
    @Query(
        "SELECT s.id FROM stories s JOIN story_translations t ON t.storyId = s.id AND t.language = :lang " +
            "WHERE s.categoryId = :categoryId AND s.episodeNumber > :episodeNumber " +
            "ORDER BY s.episodeNumber ASC LIMIT 1",
    )
    suspend fun getNextStoryId(categoryId: String, episodeNumber: Int, lang: String): String?

    @Upsert
    suspend fun upsertCategories(categories: List<CategoryEntity>)

    @Upsert
    suspend fun upsertCategoryNames(names: List<CategoryNameEntity>)

    @Upsert
    suspend fun upsertStories(stories: List<StoryEntity>)

    @Upsert
    suspend fun upsertTranslations(translations: List<StoryTranslationEntity>)

    @Upsert
    suspend fun upsertPages(pages: List<StoryPageEntity>)

    @Upsert
    suspend fun upsertLanguages(languages: List<LanguageEntity>)

    @Query("DELETE FROM category_names WHERE categoryId IN (:categoryIds)")
    suspend fun deleteCategoryNamesFor(categoryIds: List<String>)

    @Query("DELETE FROM story_translations WHERE storyId IN (:storyIds)")
    suspend fun deleteTranslationsFor(storyIds: List<String>)

    @Query("DELETE FROM story_pages WHERE storyId IN (:storyIds)")
    suspend fun deletePagesFor(storyIds: List<String>)

    @Query("DELETE FROM languages WHERE code NOT IN (:keepCodes)")
    suspend fun deleteLanguagesExcept(keepCodes: List<String>)

    /** Translations and pages go too (ON DELETE CASCADE). Favorites/progress rows stay but are never shown. */
    @Query("DELETE FROM stories WHERE id NOT IN (:keepIds)")
    suspend fun deleteStoriesExcept(keepIds: List<String>)

    /** Category names go too (ON DELETE CASCADE). */
    @Query("DELETE FROM categories WHERE id NOT IN (:keepIds)")
    suspend fun deleteCategoriesExcept(keepIds: List<String>)

    /**
     * Makes the local cache match the server, atomically so the UI never sees a half-written
     * catalogue. Stories unpublished or deleted on the server (and the old dummy data) are removed.
     */
    @Transaction
    suspend fun replaceContent(
        categories: List<CategoryEntity>,
        categoryNames: List<CategoryNameEntity>,
        stories: List<StoryEntity>,
        translations: List<StoryTranslationEntity>,
        pages: List<StoryPageEntity>,
        /** Empty when the server didn't send them: then the saved list is kept. */
        languages: List<LanguageEntity>,
    ) {
        deleteStoriesExcept(stories.map { it.id })
        deleteCategoriesExcept(categories.map { it.id })
        upsertCategories(categories)
        deleteCategoryNamesFor(categories.map { it.id })
        upsertCategoryNames(categoryNames)
        upsertStories(stories)
        deleteTranslationsFor(stories.map { it.id })
        upsertTranslations(translations)
        deletePagesFor(stories.map { it.id })
        upsertPages(pages)
        if (languages.isNotEmpty()) {
            deleteLanguagesExcept(languages.map { it.code })
            upsertLanguages(languages)
        }
    }
}
