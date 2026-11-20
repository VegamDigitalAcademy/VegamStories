package com.vegamstories.app.presentation

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vegamstories.app.R
import com.vegamstories.app.domain.model.ReadingProgress
import com.vegamstories.app.domain.model.SeriesShelf
import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.presentation.bookshelf.BookshelfContent
import com.vegamstories.app.presentation.bookshelf.BookshelfUiState
import com.vegamstories.app.presentation.preview.PreviewTheme
import com.vegamstories.app.testutil.testStory
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The bookshelf on a phone-sized screen: the shelves, their progress and stickers, and the books. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BookshelfScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun text(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)

    private fun finished(id: String, categoryId: String = "moral"): Story =
        testStory(id).copy(
            categoryId = categoryId,
            progress = ReadingProgress(id, lastPageIndex = 2, totalPages = 3, isCompleted = true, updatedAt = 0L),
        )

    /** Moral: both episodes read. Tenali: 1 of 4. */
    private val shelves = listOf(
        SeriesShelf("moral", "Moral", finished = listOf(finished("moral-1"), finished("moral-2")), total = 2),
        SeriesShelf("tenali", "Tenali", finished = listOf(finished("tenali-1", "tenali")), total = 4),
    )

    // What the screen asked for.
    private val opened = mutableListOf<String>()
    private var backs = 0

    private fun show(state: BookshelfUiState) = compose.setContent {
        PreviewTheme {
            BookshelfContent(state = state, onBack = { backs++ }, onStoryClick = { opened += it })
        }
    }

    @Test
    fun `counts the stories finished and the series complete`() {
        show(BookshelfUiState(isLoading = false, shelves = shelves))

        compose.onNodeWithText(text(R.string.bookshelf_summary, 3, 1)).assertIsDisplayed()
    }

    @Test
    fun `a series read in full gets its sticker, the others their progress`() {
        show(BookshelfUiState(isLoading = false, shelves = shelves))

        compose.onNodeWithText(text(R.string.bookshelf_complete)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.bookshelf_progress, 1, 4)).assertIsDisplayed()
    }

    @Test
    fun `tapping a book opens its story`() {
        show(BookshelfUiState(isLoading = false, shelves = shelves))

        compose.onNodeWithText("Title moral-2").performClick()

        assertEquals(listOf("moral-2"), opened)
    }

    @Test
    fun `with nothing finished, the shelf is empty`() {
        show(BookshelfUiState(isLoading = false, shelves = emptyList()))

        compose.onNodeWithText(text(R.string.bookshelf_empty_title)).assertIsDisplayed()
    }

    @Test
    fun `while loading, shows neither shelves nor the empty message`() {
        show(BookshelfUiState(isLoading = true))

        compose.onNodeWithText(text(R.string.bookshelf_empty_title)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.bookshelf_summary, 0, 0)).assertDoesNotExist()
    }

    @Test
    fun `the back button goes back`() {
        show(BookshelfUiState(isLoading = false, shelves = shelves))

        compose.onNodeWithContentDescription(text(R.string.cd_back)).performClick()

        assertEquals(1, backs)
    }
}
