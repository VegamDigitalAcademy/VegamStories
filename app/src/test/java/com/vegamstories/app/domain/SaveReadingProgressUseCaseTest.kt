package com.vegamstories.app.domain

import com.vegamstories.app.domain.model.ReadingProgress
import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.domain.repository.LibraryRepository
import com.vegamstories.app.domain.usecase.SaveReadingProgressUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Example of testing a use case with a hand-written fake — no Android or Hilt needed. */
class SaveReadingProgressUseCaseTest {

    private class FakeLibraryRepository : LibraryRepository {
        var saved: Triple<String, Int, Int>? = null
        override fun observeFavorites(): Flow<List<Story>> = emptyFlow()
        override fun observeDownloads(): Flow<List<Story>> = emptyFlow()
        override fun observeReadingHistory(): Flow<List<Story>> = emptyFlow()
        override fun observeContinueReading(categoryId: String?): Flow<Story?> = emptyFlow()
        override suspend fun toggleFavorite(storyId: String) = true
        override suspend fun toggleDownload(storyId: String) = true
        override suspend fun saveProgress(storyId: String, pageIndex: Int, totalPages: Int) {
            saved = Triple(storyId, pageIndex, totalPages)
        }
        override suspend fun clearReadingHistory() = Unit
    }

    private val repo = FakeLibraryRepository()
    private val useCase = SaveReadingProgressUseCase(repo)

    @Test
    fun `video page index is clamped to the last comic page`() = runTest {
        useCase(storyId = "moral-1", pageIndex = 4, totalPages = 4)
        assertEquals(Triple("moral-1", 3, 4), repo.saved)
    }

    @Test
    fun `story without pages is ignored`() = runTest {
        useCase(storyId = "moral-1", pageIndex = 0, totalPages = 0)
        assertNull(repo.saved)
    }

    @Test
    fun `progress fraction counts the current page`() {
        val progress = ReadingProgress("id", lastPageIndex = 1, totalPages = 4, isCompleted = false, updatedAt = 0)
        assertEquals(0.5f, progress.fraction, 0.0001f)
    }
}
