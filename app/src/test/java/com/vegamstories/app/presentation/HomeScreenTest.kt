package com.vegamstories.app.presentation

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.vegamstories.app.domain.model.ReadingProgress
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vegamstories.app.R
import com.vegamstories.app.domain.model.Category
import com.vegamstories.app.domain.model.ContentLanguages
import com.vegamstories.app.domain.model.Language
import com.vegamstories.app.domain.usecase.CheckForNewStoryUseCase
import com.vegamstories.app.domain.usecase.GetCategoriesUseCase
import com.vegamstories.app.domain.usecase.GetContentLanguagesUseCase
import com.vegamstories.app.domain.usecase.GetContinueReadingUseCase
import com.vegamstories.app.domain.usecase.GetStoriesUseCase
import com.vegamstories.app.domain.usecase.RefreshContentUseCase
import com.vegamstories.app.domain.usecase.SetContentLanguageUseCase
import com.vegamstories.app.presentation.home.HomeContent
import com.vegamstories.app.presentation.home.HomeScreen
import com.vegamstories.app.presentation.home.HomeUiState
import com.vegamstories.app.presentation.home.HomeViewModel
import com.vegamstories.app.presentation.preview.PreviewTheme
import com.vegamstories.app.testutil.FakeContentLanguageRepository
import com.vegamstories.app.testutil.FakeLibraryRepository
import com.vegamstories.app.testutil.FakeSettingsRepository
import com.vegamstories.app.testutil.FakeStoryRepository
import com.vegamstories.app.testutil.testStory
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Home on a phone-sized screen: what it shows in each state, and what each tap asks for. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun text(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)

    private val categories = listOf(Category("moral", "Moral", 0), Category("tenali", "Tenali", 1))
    private val stories = listOf(
        testStory("moral-2"),
        testStory("tenali-1").copy(categoryId = "tenali", categoryName = "Tenali"),
        testStory("moral-1"),
    )
    private val loaded = HomeUiState(categories = categories, stories = stories, isLoading = false)

    // What the screen asked for.
    private val opened = mutableListOf<String>()
    private val tabsPicked = mutableListOf<String?>()
    private val languagesPicked = mutableListOf<String>()
    private var refreshes = 0
    private var searches = 0

    private fun show(state: HomeUiState) = compose.setContent {
        PreviewTheme {
            HomeContent(
                state = state,
                onCategorySelected = { tabsPicked += it },
                onRefresh = { refreshes++ },
                onStoryClick = { opened += it },
                onSearchClick = { searches++ },
                onLanguageSelected = { languagesPicked += it },
            )
        }
    }

    /** A category tab (the story cards show category names too). */
    private fun tab(name: String) =
        compose.onNode(hasText(name) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))

    @Test
    fun `shows the latest stories with their count, and a tap opens one`() {
        show(loaded)

        compose.onNodeWithText(text(R.string.latest_stories)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.stories_count, 3)).assertIsDisplayed()
        // The title's on the cover (read out from it), not written under it.
        compose.onNodeWithContentDescription("Title tenali-1").performClick()

        assertEquals(listOf("tenali-1"), opened)
    }

    @Test
    fun `tabs pick a category, or ALL`() {
        show(loaded)

        tab(text(R.string.tab_all)).assertIsSelected()
        tab("Tenali").performClick()
        tab(text(R.string.tab_all)).performClick()

        assertEquals(listOf("tenali", null), tabsPicked)
    }

    @Test
    fun `a chosen category's tab is selected`() {
        show(loaded.copy(selectedCategoryId = "tenali", stories = stories.filter { it.categoryId == "tenali" }))

        tab("Tenali").assertIsSelected()
        compose.onNodeWithText(text(R.string.stories_count, 1)).assertIsDisplayed()
    }

    @Test
    fun `on ALL each card names its category, but not its title (that's on the cover)`() {
        show(loaded)

        // The tab's own name aside, "Tenali" shows under its card.
        assertEquals(2, compose.onAllNodesWithText("Tenali").fetchSemanticsNodes().size)
        compose.onNodeWithText("Title tenali-1").assertDoesNotExist()
    }

    @Test
    fun `on a category's own tab the cards don't repeat its name`() {
        show(loaded.copy(selectedCategoryId = "tenali", stories = stories.filter { it.categoryId == "tenali" }))

        // Only the tab and the section's heading above the cards; none under the card.
        assertEquals(2, compose.onAllNodesWithText("Tenali").fetchSemanticsNodes().size)
    }

    @Test
    fun `a story already read shows the green tick, one not read doesn't`() {
        val read = testStory("moral-1").copy(progress = ReadingProgress("moral-1", lastPageIndex = 2, totalPages = 3, isCompleted = true, updatedAt = 0L))
        show(loaded.copy(stories = listOf(read, testStory("moral-2"))))

        assertEquals(1, compose.onAllNodesWithContentDescription(text(R.string.read_badge)).fetchSemanticsNodes().size)
    }

    @Test
    fun `continue reading opens the story left unfinished`() {
        show(loaded.copy(continueReading = testStory("moral-1", opened = true)))

        compose.onNodeWithText(text(R.string.continue_reading)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.continue_action)).performClick()

        assertEquals(listOf("moral-1"), opened)
    }

    @Test
    fun `with no stories, says so and offers Retry`() {
        show(HomeUiState(categories = categories, isLoading = false))

        compose.onNodeWithText(text(R.string.no_stories_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.retry)).performClick()

        assertEquals(1, refreshes)
    }

    @Test
    fun `while loading, shows neither stories nor the empty message`() {
        show(HomeUiState(isLoading = true))

        compose.onNodeWithText(text(R.string.no_stories_title)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.latest_stories)).assertDoesNotExist()
    }

    @Test
    fun `the language button lists the story languages and picks one`() {
        val languages = ContentLanguages(
            all = listOf(Language("te", "Telugu", "తెలుగు"), Language("en", "English", "English")),
            selected = "te",
        )
        show(loaded.copy(contentLanguages = languages))

        compose.onNodeWithContentDescription(text(R.string.cd_story_language), substring = true).performClick()
        compose.onNodeWithText("English").performClick()

        assertEquals(listOf("en"), languagesPicked)
    }

    @Test
    fun `with stories in one language only, there is no language button`() {
        show(loaded.copy(contentLanguages = ContentLanguages(all = listOf(Language("te", "Telugu", "తెలుగు")), selected = "te")))

        compose.onNodeWithContentDescription(text(R.string.cd_story_language), substring = true).assertDoesNotExist()
    }

    @Test
    fun `the search button opens search`() {
        show(loaded)

        compose.onNodeWithContentDescription(text(R.string.cd_search)).performClick()

        assertEquals(1, searches)
    }

    @Test
    fun `opening Home checks for new stories`() {
        val repository = FakeStoryRepository()
        val languages = FakeContentLanguageRepository()
        val viewModel = HomeViewModel(
            savedStateHandle = SavedStateHandle(),
            getCategories = GetCategoriesUseCase(repository),
            getStories = GetStoriesUseCase(repository),
            getContinueReading = GetContinueReadingUseCase(FakeLibraryRepository()),
            refreshContent = RefreshContentUseCase(repository),
            checkForNewStory = CheckForNewStoryUseCase(repository, FakeSettingsRepository()),
            getContentLanguages = GetContentLanguagesUseCase(languages),
            setContentLanguage = SetContentLanguageUseCase(languages),
        )

        compose.setContent {
            PreviewTheme { HomeScreen(onStoryClick = {}, onSearchClick = {}, onShowMessage = {}, viewModel = viewModel) }
        }
        compose.waitForIdle()

        assertEquals(listOf(false), repository.refreshes) // not forced: the 5-minute rule applies
    }
}
