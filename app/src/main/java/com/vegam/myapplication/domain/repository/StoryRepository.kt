package com.vegam.myapplication.domain.repository

import com.vegam.myapplication.domain.model.Category
import com.vegam.myapplication.domain.model.Story
import com.vegam.myapplication.domain.model.StoryDetail
import kotlinx.coroutines.flow.Flow

/** Story catalogue (content that comes from the server and is cached locally). */
interface StoryRepository {
    fun observeCategories(): Flow<List<Category>>

    /** [categoryId] null = all categories (latest first). */
    fun observeStories(categoryId: String?): Flow<List<Story>>

    fun observeStoryDetail(storyId: String): Flow<StoryDetail?>

    fun searchStories(query: String): Flow<List<Story>>

    /** Pulls fresh content from the server into the local database. */
    suspend fun refresh(): Result<Unit>

    /** Next episode in the same category, or null if this is the last one. */
    suspend fun getNextStoryId(storyId: String): String?
}
