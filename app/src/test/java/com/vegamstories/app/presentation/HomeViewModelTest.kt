package com.vegamstories.app.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vegamstories.app.domain.model.Category
import com.vegamstories.app.domain.usecase.CheckForNewStoryUseCase
import com.vegamstories.app.domain.usecase.GetCategoriesUseCase
import com.vegamstories.app.domain.usecase.GetContentLanguagesUseCase
import com.vegamstories.app.domain.usecase.GetContinueReadingUseCase
import com.vegamstories.app.domain.usecase.GetStoriesUseCase
import com.vegamstories.app.domain.usecase.RefreshContentUseCase
import com.vegamstories.app.domain.usecase.SetContentLanguageUseCase
import com.vegamstories.app.presentation.home.HomeEvent
import com.vegamstories.app.presentation.home.HomeViewModel
import com.vegamstories.app.testutil.FakeContentLanguageRepository
import com.vegamstories.app.testutil.FakeLibraryRepository
import com.vegamstories.app.testutil.FakeSettingsRepository
import com.vegamstories.app.testutil.FakeStoryRepository
import com.vegamstories.app.testutil.MainDispatcherRule
import com.vegamstories.app.testutil.testStory
import kotlinx.coroutines.CompletableDeferred
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
import java.io.IOException

/**
 * Home's view model with in-memory repositories: the tabs, Continue reading, the story language
 * and refreshing. Robolectric only because the route is read from Android's saved state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val stories = FakeStoryRepository().apply {
        categories.value = listOf(Category("moral", "Moral", 0), Category("tenali", "Tenali", 1))
        stories.value = listOf(
            testStory("moral-2", publishedAt = 2),
            testStory("tenali-1", publishedAt = 1).copy(categoryId = "tenali", categoryName = "Tenali"),
            testStory("moral-1"),
        )
    }
    private val library = FakeLibraryRepository()
    private val settings = FakeSettingsRepository()
    private val languages = FakeContentLanguageRepository()

    /** Home as opened from the bottom bar, or from an app-icon shortcut to [categoryId]'s tab. */
    private fun homeViewModel(categoryId: String? = null) = HomeViewModel(
        savedStateHandle = SavedStateHandle(listOfNotNull(categoryId?.let { "categoryId" to it }).toMap()),
        getCategories = GetCategoriesUseCase(stories),
        getStories = GetStoriesUseCase(stories),
        getContinueReading = GetContinueReadingUseCase(library),
        refreshContent = RefreshContentUseCase(stories),
        checkForNewStory = CheckForNewStoryUseCase(stories, settings),
        getContentLanguages = GetContentLanguagesUseCase(languages),
        setContentLanguage = SetContentLanguageUseCase(languages),
    )

    /** The state is worked out only while the screen watches it. */
    private fun TestScope.watch(viewModel: HomeViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    private val HomeViewModel.storyIds get() = uiState.value.stories.map { it.id }

    @Test
    fun `the ALL tab shows every story, a category's tab only its own`() = runTest {
        val viewModel = homeViewModel()
        watch(viewModel)

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.selectedCategoryId)
        assertEquals(listOf("moral-2", "tenali-1", "moral-1"), viewModel.storyIds)

        viewModel.onCategorySelected("tenali")

        assertEquals("Tenali", viewModel.uiState.value.selectedCategory?.name)
        assertEquals(listOf("tenali-1"), viewModel.storyIds)
    }

    @Test
    fun `opened from an app-icon shortcut, starts on that category's tab`() = runTest {
        val viewModel = homeViewModel(categoryId = "tenali")
        watch(viewModel)

        assertEquals("tenali", viewModel.uiState.value.selectedCategoryId)
        assertEquals(listOf("tenali-1"), viewModel.storyIds)
    }

    @Test
    fun `continue reading follows the tab`() = runTest {
        library.continueReading.value = testStory("moral-1", opened = true)
        val viewModel = homeViewModel()
        watch(viewModel)

        assertEquals("moral-1", viewModel.uiState.value.continueReading?.id)

        viewModel.onCategorySelected("tenali")

        assertNull(viewModel.uiState.value.continueReading)
    }

    @Test
    fun `each time Home comes into view it asks for a check, and pull to refresh forces one`() = runTest {
        val viewModel = homeViewModel()
        assertTrue(stories.refreshes.isEmpty())

        viewModel.onShown()
        viewModel.onShown()
        viewModel.refresh()

        assertEquals(listOf(false, false, true), stories.refreshes)
    }

    @Test
    fun `the spinner shows only while a pull to refresh runs, and a second pull waits for it`() = runTest {
        val viewModel = homeViewModel()
        watch(viewModel)
        val gate = CompletableDeferred<Unit>().also { stories.refreshGate = it }

        viewModel.refresh()
        assertTrue(viewModel.uiState.value.isRefreshing)
        viewModel.refresh()
        gate.complete(Unit)

        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals(listOf(true), stories.refreshes)
    }

    @Test
    fun `the check on opening runs without the spinner`() = runTest {
        val gate = CompletableDeferred<Unit>().also { stories.refreshGate = it }
        val viewModel = homeViewModel()
        watch(viewModel)

        viewModel.onShown()

        assertEquals(listOf(false), stories.refreshes)
        assertFalse(viewModel.uiState.value.isRefreshing)
        gate.complete(Unit)
    }

    @Test
    fun `on a first launch Home shows placeholders while the stories download, not No stories`() = runTest {
        stories.stories.value = emptyList()
        val gate = CompletableDeferred<Unit>().also { stories.refreshGate = it }
        val viewModel = homeViewModel()
        watch(viewModel)

        viewModel.onShown()
        assertTrue(viewModel.uiState.value.isLoading)
        assertFalse("the quiet check on opening still has no spinner", viewModel.uiState.value.isRefreshing)

        stories.stories.value = listOf(testStory("moral-1"))
        gate.complete(Unit)

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(listOf("moral-1"), viewModel.storyIds)
    }

    @Test
    fun `a first launch that couldn't download stops showing placeholders`() = runTest {
        stories.stories.value = emptyList()
        stories.refreshResult = Result.failure(IOException("offline"))
        val viewModel = homeViewModel()
        watch(viewModel)

        viewModel.onShown()

        assertFalse("Home shows No stories with Retry", viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.stories.isEmpty())
    }

    @Test
    fun `a failed pull to refresh says so, and the saved stories stay`() = runTest {
        stories.refreshResult = Result.failure(IOException("offline"))
        val viewModel = homeViewModel()
        watch(viewModel)

        viewModel.refresh()

        assertEquals(HomeEvent.RefreshFailed, viewModel.eventFlow.first())
        assertEquals(3, viewModel.uiState.value.stories.size)
    }

    @Test
    fun `a failed check on opening stays quiet`() = runTest {
        stories.refreshResult = Result.failure(IOException("offline"))
        val viewModel = homeViewModel()
        val events = mutableListOf<HomeEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.eventFlow.collect { events += it } }

        viewModel.onShown()

        assertEquals(listOf(false), stories.refreshes)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `new stories downloaded while the app is open are only marked seen`() = runTest {
        stories.refreshResult = Result.success(true)
        settings.newStoryAlertsEnabled.value = true

        homeViewModel().onShown()

        assertEquals("moral-2", settings.lastSeenStory?.id)
    }

    @Test
    fun `picking a story language goes back to the ALL tab`() = runTest {
        val viewModel = homeViewModel()
        watch(viewModel)
        viewModel.onCategorySelected("tenali")

        viewModel.onLanguageSelected("en")

        assertNull(viewModel.uiState.value.selectedCategoryId)
        assertEquals("en", viewModel.uiState.value.contentLanguages?.selected)
    }
}
