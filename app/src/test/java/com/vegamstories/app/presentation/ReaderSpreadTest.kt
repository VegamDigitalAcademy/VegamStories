package com.vegamstories.app.presentation

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vegamstories.app.R
import com.vegamstories.app.domain.model.ReaderSettings
import com.vegamstories.app.domain.model.StoryDetail
import com.vegamstories.app.domain.model.StoryPage
import com.vegamstories.app.presentation.preview.PreviewTheme
import com.vegamstories.app.presentation.reader.ReaderScreenContent
import com.vegamstories.app.presentation.reader.ReaderUiState
import com.vegamstories.app.testutil.testStory
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The comic reader on a tablet: two pages side by side when it lies on its side, one when upright. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-land")
class ReaderSpreadTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun text(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)

    private val pagesSettled = mutableListOf<Int>()

    private fun show(pages: Int, startPage: Int) = compose.setContent {
        PreviewTheme {
            ReaderScreenContent(
                state = ReaderUiState(
                    isLoading = false,
                    detail = StoryDetail(
                        story = testStory(pageCount = pages),
                        pages = List(pages) { StoryPage(index = it, imageUrl = "https://example.com/p$it.webp") },
                    ),
                    initialPage = startPage,
                    settings = ReaderSettings(autoTurnPages = false, pageTurnHintShown = true),
                ),
                onBack = {},
                onPageChanged = { pagesSettled += it },
                onToggleFavorite = {},
                onToggleDownload = {},
                onOpenNextStory = {},
                openOnCover = false,
            )
        }
    }

    @Test
    fun `on its side, a page opens with the one beside it`() {
        show(pages = 5, startPage = 2)

        // Pages 3 and 4 together, on the 2nd of 3 spreads + the like page.
        compose.onNodeWithText(text(R.string.page_of, 2, 4)).assertIsDisplayed()
        assertEquals("both pages count as read", listOf(3), pagesSettled)
    }

    @Test
    fun `an odd last page stands alone and leads on to the like page`() {
        show(pages = 3, startPage = 2)

        compose.onNodeWithText(text(R.string.the_end)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.continue_action)).performClick()

        compose.onNodeWithText(text(R.string.page_of, 3, 3)).assertIsDisplayed()
        assertEquals(listOf(2, 3), pagesSettled)
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-port")
    fun `upright, pages show one at a time`() {
        show(pages = 5, startPage = 2)

        compose.onNodeWithText(text(R.string.page_of, 3, 6)).assertIsDisplayed()
        assertEquals(listOf(2), pagesSettled)
    }
}
