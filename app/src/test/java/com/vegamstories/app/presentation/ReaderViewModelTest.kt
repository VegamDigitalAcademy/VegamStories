package com.vegamstories.app.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vegamstories.app.domain.model.Bookmark
import com.vegamstories.app.domain.model.PageBackground
import com.vegamstories.app.domain.model.ReaderScrollMode
import com.vegamstories.app.domain.model.ReaderSettings
import com.vegamstories.app.domain.model.ReaderTextSize
import com.vegamstories.app.domain.model.ReadingProgress
import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.domain.model.StoryDetail
import com.vegamstories.app.domain.model.StoryFormat
import com.vegamstories.app.domain.model.StoryPage
import com.vegamstories.app.domain.model.StoryText
import com.vegamstories.app.domain.usecase.AddBookmarkUseCase
import com.vegamstories.app.domain.usecase.GetNextStoryUseCase
import com.vegamstories.app.domain.usecase.GetReaderSettingsUseCase
import com.vegamstories.app.domain.usecase.GetStoryBookmarksUseCase
import com.vegamstories.app.domain.usecase.GetStoryDetailUseCase
import com.vegamstories.app.domain.usecase.RemoveBookmarksUseCase
import com.vegamstories.app.domain.usecase.SaveReadingProgressUseCase
import com.vegamstories.app.domain.usecase.ToggleDownloadUseCase
import com.vegamstories.app.domain.usecase.ToggleFavoriteUseCase
import com.vegamstories.app.domain.usecase.UpdateReaderSettingsUseCase
import com.vegamstories.app.presentation.navigation.ReaderRoute
import com.vegamstories.app.presentation.navigation.toReaderRoute
import com.vegamstories.app.presentation.reader.ReaderEvent
import com.vegamstories.app.presentation.reader.ReaderViewModel
import com.vegamstories.app.testutil.APP_LANGUAGE
import com.vegamstories.app.testutil.FakeLibraryRepository
import com.vegamstories.app.testutil.FakeSettingsRepository
import com.vegamstories.app.testutil.FakeStoryRepository
import com.vegamstories.app.testutil.MainDispatcherRule
import com.vegamstories.app.testutil.testStory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The reader's view model with in-memory repositories: where a story opens, saving progress,
 * tabs, languages, the heart, downloads, bookmarks and the reading options. Robolectric only
 * because the route is read from Android's saved state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ReaderViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val stories = FakeStoryRepository()
    private val library = FakeLibraryRepository()
    private val settings = FakeSettingsRepository()

    /** "moral-1": 4 comic pages, optionally [text] and reading [progress]. */
    private fun story(progress: ReadingProgress? = null, text: Boolean = false) =
        testStory("moral-1", pageCount = 4, hasText = text).copy(progress = progress)

    private fun detail(story: Story) = StoryDetail(
        story = story,
        pages = List(story.pageCount) { StoryPage(index = it, imageUrl = "https://example.com/${story.id}/$it.webp") },
        text = if (story.hasText) StoryText(story.language, "Once upon a time.\n\nThe end.") else null,
    )

    private fun saved(vararg stories: Story) {
        this.stories.details.value = stories.map(::detail)
    }

    private fun progress(page: Int, completed: Boolean = false) =
        ReadingProgress("moral-1", lastPageIndex = page, totalPages = 4, isCompleted = completed, updatedAt = 0L)

    private fun readerViewModel(route: ReaderRoute = ReaderRoute("moral-1")) = ReaderViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(
                "storyId" to route.storyId,
                "bookmarkFormat" to route.bookmarkFormat,
                "bookmarkLanguage" to route.bookmarkLanguage,
                "bookmarkPosition" to route.bookmarkPosition,
                "bookmarkOffset" to route.bookmarkOffset,
            ),
        ),
        getStoryDetail = GetStoryDetailUseCase(stories),
        getNextStory = GetNextStoryUseCase(stories),
        getReaderSettings = GetReaderSettingsUseCase(settings),
        updateReaderSettings = UpdateReaderSettingsUseCase(settings),
        saveReadingProgress = SaveReadingProgressUseCase(library),
        toggleFavorite = ToggleFavoriteUseCase(library),
        toggleDownload = ToggleDownloadUseCase(library),
        getStoryBookmarks = GetStoryBookmarksUseCase(library),
        addBookmark = AddBookmarkUseCase(library),
        removeBookmarks = RemoveBookmarksUseCase(library),
        ioDispatcher = mainDispatcher.dispatcher,
    )

    /** The state is worked out only while the screen watches it. */
    private fun TestScope.watch(viewModel: ReaderViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    @Test
    fun `opens where the reader left off`() = runTest {
        saved(story(progress = progress(page = 2)))
        val viewModel = readerViewModel()
        watch(viewModel)

        assertEquals(2, viewModel.uiState.value.initialPage)
        assertEquals(0.5f, viewModel.uiState.value.initialTextFraction)
    }

    @Test
    fun `a finished story opens at the start again`() = runTest {
        saved(story(progress = progress(page = 3, completed = true)))
        val viewModel = readerViewModel()
        watch(viewModel)

        assertEquals(0, viewModel.uiState.value.initialPage)
        assertEquals(0f, viewModel.uiState.value.initialTextFraction)
    }

    @Test
    fun `opened from a comic bookmark, opens on its page`() = runTest {
        saved(story(progress = progress(page = 1)))
        val viewModel = readerViewModel(Bookmark("moral-1", APP_LANGUAGE, StoryFormat.COMIC, position = 3).toReaderRoute())
        watch(viewModel)

        assertEquals(3, viewModel.uiState.value.initialPage)
        assertEquals(StoryFormat.COMIC, viewModel.uiState.value.selectedFormat)
        assertNull(viewModel.uiState.value.textBookmarkStart)
    }

    @Test
    fun `opened from a bookmark in the text, opens the Read tab there`() = runTest {
        saved(story(text = true))
        val bookmark = Bookmark("moral-1", APP_LANGUAGE, StoryFormat.TEXT, position = 1, offset = 5)
        val viewModel = readerViewModel(bookmark.toReaderRoute())
        watch(viewModel)

        assertEquals(StoryFormat.TEXT, viewModel.uiState.value.format)
        assertEquals(bookmark, viewModel.uiState.value.textBookmarkStart)
    }

    @Test
    fun `a story that isn't saved shows not found`() = runTest {
        val viewModel = readerViewModel(ReaderRoute("missing"))
        watch(viewModel)

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.detail)
    }

    @Test
    fun `turning pages saves progress without moving the open page`() = runTest {
        saved(story())
        val viewModel = readerViewModel()
        watch(viewModel)

        viewModel.onPageChanged(1)
        saved(story(progress = progress(page = 1))) // the database has the new progress
        viewModel.onPageChanged(4) // the like page

        assertEquals(listOf(Triple("moral-1", 1, 4), Triple("moral-1", 3, 4)), library.savedProgress)
        assertEquals(0, viewModel.uiState.value.initialPage)
    }

    @Test
    fun `reading the text saves progress too`() = runTest {
        saved(story(text = true))
        val viewModel = readerViewModel()
        watch(viewModel)

        viewModel.onTextProgress(paragraphIndex = 1, paragraphCount = 2)

        assertEquals(listOf(Triple("moral-1", 1, 2)), library.savedProgress)
    }

    @Test
    fun `offers the next episode, and nothing after the last`() = runTest {
        saved(story(), testStory("moral-2"))
        stories.nextStoryIds["moral-1"] = "moral-2"

        val first = readerViewModel()
        watch(first)
        val last = readerViewModel(ReaderRoute("moral-2"))
        watch(last)

        assertEquals("moral-2", first.uiState.value.nextStory?.id)
        assertNull(last.uiState.value.nextStory)
    }

    @Test
    fun `opens in the favourite tab when the story has it`() = runTest {
        settings.readerSettings.value = ReaderSettings(preferredFormat = StoryFormat.TEXT)
        saved(story(text = true), testStory("moral-2"))

        val withText = readerViewModel()
        watch(withText)
        val comicOnly = readerViewModel(ReaderRoute("moral-2"))
        watch(comicOnly)

        assertEquals(StoryFormat.TEXT, withText.uiState.value.format)
        assertEquals(StoryFormat.COMIC, comicOnly.uiState.value.format)
    }

    @Test
    fun `picking a tab shows it and remembers it for the next story`() = runTest {
        saved(story(text = true))
        val viewModel = readerViewModel()
        watch(viewModel)

        viewModel.onFormatSelected(StoryFormat.TEXT)

        assertEquals(StoryFormat.TEXT, viewModel.uiState.value.format)
        assertEquals(StoryFormat.TEXT, settings.readerSettings.value.preferredFormat)
    }

    @Test
    fun `another language shows that version of the story`() = runTest {
        saved(story(), story().copy(language = "en", title = "The crow"))
        val viewModel = readerViewModel()
        watch(viewModel)

        viewModel.onLanguageSelected("en")

        assertEquals("en", viewModel.uiState.value.language)
        assertEquals("The crow", viewModel.uiState.value.detail?.story?.title)
    }

    @Test
    fun `the heart and the download button say what they did`() = runTest {
        saved(story())
        val viewModel = readerViewModel()

        viewModel.onToggleFavorite()
        assertEquals(ReaderEvent.FavoriteChanged(isFavorite = true), viewModel.eventFlow.first())
        viewModel.onToggleFavorite()
        assertEquals(ReaderEvent.FavoriteChanged(isFavorite = false), viewModel.eventFlow.first())
        viewModel.onToggleDownload()
        assertEquals(ReaderEvent.DownloadChanged(isDownloaded = true), viewModel.eventFlow.first())
    }

    @Test
    fun `the ribbon marks a page in the language shown, and clears it`() = runTest {
        saved(story())
        val viewModel = readerViewModel()
        watch(viewModel)

        viewModel.onAddBookmark(StoryFormat.COMIC, position = 2, offset = 0)
        val marked = viewModel.uiState.value.bookmarks
        viewModel.onRemoveBookmarks(marked)

        assertEquals(listOf(Bookmark("moral-1", APP_LANGUAGE, StoryFormat.COMIC, position = 2)), marked)
        assertTrue(viewModel.uiState.value.bookmarks.isEmpty())
    }

    @Test
    fun `reading options are saved and shown`() = runTest {
        saved(story())
        val viewModel = readerViewModel()
        watch(viewModel)

        viewModel.onScrollModeChange(ReaderScrollMode.VERTICAL)
        viewModel.onKeepScreenOnChange(false)
        viewModel.onPageBackgroundChange(PageBackground.SEPIA)
        viewModel.onTextSizeChange(ReaderTextSize.LARGE)
        viewModel.onPageTurnHintShown()

        val expected = ReaderSettings(
            scrollMode = ReaderScrollMode.VERTICAL,
            keepScreenOn = false,
            pageBackground = PageBackground.SEPIA,
            textSize = ReaderTextSize.LARGE,
            pageTurnHintShown = true,
        )
        assertEquals(expected, settings.readerSettings.value)
        assertEquals(expected, viewModel.uiState.value.settings)
    }
}
