package com.vegamstories.app.presentation

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vegamstories.app.R
import com.vegamstories.app.domain.model.Bookmark
import com.vegamstories.app.domain.model.ReaderScrollMode
import com.vegamstories.app.domain.model.ReaderSettings
import com.vegamstories.app.domain.model.StoryDetail
import com.vegamstories.app.domain.model.StoryFormat
import com.vegamstories.app.domain.model.StoryPage
import com.vegamstories.app.domain.model.StoryText
import com.vegamstories.app.presentation.preview.PreviewTheme
import com.vegamstories.app.presentation.reader.ReaderScreenContent
import com.vegamstories.app.presentation.reader.ReaderUiState
import com.vegamstories.app.testutil.APP_LANGUAGE
import com.vegamstories.app.testutil.testStory
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * The reader on a phone-sized screen: the comic pages, the page bars, the top bar, the ribbon,
 * the tabs and the like page. Pages don't turn by themselves here, and the first-time corner
 * lift is off, unless a test is about them.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun text(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)

    private val settings = ReaderSettings(autoTurnPages = false, pageTurnHintShown = true)

    /** A story of [pages] comic pages, open on [startPage]; the like page comes after the last. */
    private fun reading(pages: Int = 3, startPage: Int = 0, text: String? = null) = ReaderUiState(
        isLoading = false,
        detail = StoryDetail(
            story = testStory(pageCount = pages, hasText = text != null),
            pages = List(pages) { StoryPage(index = it, imageUrl = "https://example.com/p$it.webp") },
            text = text?.let { StoryText(APP_LANGUAGE, it) },
        ),
        initialPage = startPage,
        settings = settings,
    )

    // What the reader asked for.
    private val pagesSettled = mutableListOf<Int>()
    private val nextOpened = mutableListOf<String>()
    private val scrollModes = mutableListOf<ReaderScrollMode>()
    private val formatsPicked = mutableListOf<StoryFormat>()
    private val bookmarksAdded = mutableListOf<Triple<StoryFormat, Int, Int>>()
    private val bookmarksRemoved = mutableListOf<List<Bookmark>>()
    private var backs = 0
    private var hearts = 0
    private var downloads = 0
    private var turnHints = 0

    private fun show(state: ReaderUiState) = compose.setContent {
        PreviewTheme {
            ReaderScreenContent(
                state = state,
                onBack = { backs++ },
                onPageChanged = { pagesSettled += it },
                onToggleFavorite = { hearts++ },
                onToggleDownload = { downloads++ },
                onOpenNextStory = { nextOpened += it },
                openOnCover = false,
                onScrollModeChange = { scrollModes += it },
                onFormatSelected = { formatsPicked += it },
                onAddBookmark = { format, position, offset -> bookmarksAdded += Triple(format, position, offset) },
                onRemoveBookmarks = { bookmarksRemoved += it },
                onTurnHintShown = { turnHints++ },
            )
        }
    }

    @Test
    fun `a story that isn't there says so and offers the way back`() {
        show(ReaderUiState(isLoading = false, detail = null))

        compose.onNodeWithText(text(R.string.story_not_found)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.back_to_stories)).performClick()

        assertEquals(1, backs)
    }

    @Test
    fun `opens on the page where the reader left off`() {
        show(reading(pages = 3, startPage = 1))

        compose.onNodeWithText("Title moral-1").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.page_of, 2, 4)).assertIsDisplayed() // 3 pages + the like page
        assertEquals(listOf(1), pagesSettled)
    }

    @Test
    fun `the last page's Continue turns to the like page`() {
        show(reading(pages = 1))

        compose.onNodeWithText(text(R.string.the_end)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.continue_action)).performClick()

        compose.onNodeWithText(text(R.string.page_of, 2, 2)).assertIsDisplayed()
        assertEquals(listOf(0, 1), pagesSettled)
    }

    @Test
    fun `the like page offers the next episode`() {
        show(reading(pages = 3, startPage = 3).copy(nextStory = testStory("moral-2")))

        compose.onNodeWithText(text(R.string.like_prompt_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.skip_to_next_story)).performClick()

        assertEquals(listOf("moral-2"), nextOpened)
    }

    @Test
    fun `the top bar goes back, likes and downloads`() {
        show(reading())

        compose.onNodeWithContentDescription(text(R.string.cd_back)).performClick()
        compose.onNodeWithContentDescription(text(R.string.cd_like)).performClick()
        compose.onNodeWithContentDescription(text(R.string.cd_download)).performClick()

        assertEquals(listOf(1, 1, 1), listOf(backs, hearts, downloads))
    }

    @Test
    fun `the ribbon bookmarks the page`() {
        show(reading(pages = 1))

        compose.onNodeWithContentDescription(text(R.string.bookmark_page)).assertIsOff().performClick()

        assertEquals(listOf(Triple(StoryFormat.COMIC, 0, 0)), bookmarksAdded)
    }

    @Test
    fun `the ribbon on a bookmarked page clears its bookmark`() {
        val bookmark = Bookmark("moral-1", APP_LANGUAGE, StoryFormat.COMIC, position = 0)
        show(reading(pages = 1).copy(bookmarks = listOf(bookmark)))

        compose.onNodeWithContentDescription(text(R.string.bookmark_page)).assertIsOn().performClick()

        assertEquals(listOf(listOf(bookmark)), bookmarksRemoved)
    }

    @Test
    fun `a story with text has Comic and Read tabs`() {
        show(reading(text = "Once upon a time.\n\nThe end."))

        compose.onNodeWithText(text(R.string.reader_tab_comic)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.reader_tab_read)).performClick()

        assertEquals(listOf(StoryFormat.TEXT), formatsPicked)
    }

    @Test
    fun `a comic-only story has no tabs`() {
        show(reading())

        compose.onNodeWithText(text(R.string.reader_tab_comic)).assertDoesNotExist()
    }

    @Test
    fun `the Read tab shows the story's text`() {
        show(reading(text = "Once upon a time there was a crow.\n\nThe end.").copy(selectedFormat = StoryFormat.TEXT))

        compose.onNodeWithText("Once upon a time there was a crow.", substring = true).assertIsDisplayed()
    }

    @Test
    fun `reading options change the scrolling`() {
        show(reading())

        compose.onNodeWithContentDescription(text(R.string.cd_reader_options)).performClick()
        compose.onNodeWithText(text(R.string.reader_scroll_vertical)).performClick()

        assertEquals(listOf(ReaderScrollMode.VERTICAL), scrollModes)
    }

    @Test
    fun `a first-time reader sees the corner lift once`() {
        show(reading().copy(settings = settings.copy(pageTurnHintShown = false)))
        compose.mainClock.advanceTimeBy(3_000)

        assertEquals(1, turnHints)
    }

    @Test
    fun `the corner doesn't lift again once shown`() {
        show(reading())
        compose.mainClock.advanceTimeBy(3_000)

        assertEquals(0, turnHints)
    }
}
