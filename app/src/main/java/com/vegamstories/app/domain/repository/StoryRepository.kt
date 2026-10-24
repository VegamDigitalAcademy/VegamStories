package com.vegamstories.app.domain.repository

import com.vegamstories.app.domain.model.Category
import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.domain.model.StoryDetail
import kotlinx.coroutines.flow.Flow

/** Story catalogue (content that comes from the server and is cached locally). */
interface StoryRepository {
    fun observeCategories(): Flow<List<Category>>

    /** [categoryId] null = all categories (latest first). */
    fun observeStories(categoryId: String?): Flow<List<Story>>

    /**
     * The story in [language] (null = the app's story language), or in another language it has.
     * Lists ([observeCategories], [observeStories], [searchStories]) show only the app's story language.
     */
    fun observeStoryDetail(storyId: String, language: String? = null): Flow<StoryDetail?>

    fun searchStories(query: String): Flow<List<Story>>

    /**
     * Pulls fresh content from the server into the local database: always when [force]d (the
     * reader asked: pull to refresh, Retry), otherwise only when due (see isContentRefreshDue), as
     * server calls cost money. Succeeds with whether it downloaded.
     */
    suspend fun refresh(force: Boolean): Result<Boolean>

    /** Next episode in the same category, or null if this is the last one. */
    suspend fun getNextStoryId(storyId: String): String?
}
