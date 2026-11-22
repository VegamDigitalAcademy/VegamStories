package com.vegamstories.app.presentation

import com.vegamstories.app.domain.model.Category
import com.vegamstories.app.domain.model.ReadingProgress
import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.domain.usecase.GetBookshelfUseCase
import com.vegamstories.app.presentation.bookshelf.BookshelfViewModel
import com.vegamstories.app.testutil.FakeLibraryRepository
import com.vegamstories.app.testutil.FakeStoryRepository
import com.vegamstories.app.testutil.MainDispatcherRule
import com.vegamstories.app.testutil.testStory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The bookshelf's view model: loading, then a shelf per series that fills as stories are finished. */
@OptIn(ExperimentalCoroutinesApi::class)
class BookshelfViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val stories = FakeStoryRepository().apply {
        categories.value = listOf(Category("moral", "Moral", 0))
        stories.value = listOf(testStory("moral-2"), testStory("moral-1"))
    }
    private val library = FakeLibraryRepository()
    // Made in the test, once the main dispatcher is set.
    private val viewModel by lazy { BookshelfViewModel(GetBookshelfUseCase(library, stories)) }

    private fun finished(id: String, episode: Int): Story = testStory(id).copy(
        episodeNumber = episode,
        progress = ReadingProgress(id, lastPageIndex = 2, totalPages = 3, isCompleted = true, updatedAt = 0L),
    )

    /** The state is worked out only while the screen watches it. */
    private fun TestScope.watch() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    @Test
    fun `loading until the shelves are worked out`() = runTest {
        assertTrue(viewModel.uiState.value.isLoading)

        watch()

        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `stories only opened don't go on the shelf`() = runTest {
        library.history.value = listOf(testStory("moral-1", opened = true))
        watch()

        assertTrue(viewModel.uiState.value.shelves.isEmpty())
    }

    @Test
    fun `finishing stories fills the series' shelf, then completes it`() = runTest {
        watch()

        library.history.value = listOf(finished("moral-1", episode = 1))
        assertEquals(1, viewModel.uiState.value.finishedCount)
        assertEquals(0, viewModel.uiState.value.completeSeries)

        library.history.value = listOf(finished("moral-2", episode = 2), finished("moral-1", episode = 1))
        assertEquals(2, viewModel.uiState.value.finishedCount)
        assertEquals(1, viewModel.uiState.value.completeSeries)
        assertEquals(listOf("moral-1", "moral-2"), viewModel.uiState.value.shelves.single().finished.map { it.id })
    }
}
