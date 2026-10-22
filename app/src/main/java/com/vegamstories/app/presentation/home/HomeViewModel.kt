package com.vegamstories.app.presentation.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vegamstories.app.domain.usecase.CheckForNewStoryUseCase
import com.vegamstories.app.domain.usecase.GetCategoriesUseCase
import com.vegamstories.app.domain.usecase.GetContentLanguagesUseCase
import com.vegamstories.app.domain.usecase.GetContinueReadingUseCase
import com.vegamstories.app.domain.usecase.GetStoriesUseCase
import com.vegamstories.app.domain.usecase.RefreshContentUseCase
import com.vegamstories.app.domain.usecase.SetContentLanguageUseCase
import com.vegamstories.app.presentation.navigation.HomeRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getCategories: GetCategoriesUseCase,
    private val getStories: GetStoriesUseCase,
    private val getContinueReading: GetContinueReadingUseCase,
    private val refreshContent: RefreshContentUseCase,
    private val checkForNewStory: CheckForNewStoryUseCase,
    getContentLanguages: GetContentLanguagesUseCase,
    private val setContentLanguage: SetContentLanguageUseCase,
) : ViewModel() {

    // Opened from an app-icon shortcut → start on that category's tab.
    private val selectedCategoryId = MutableStateFlow(savedStateHandle.toRoute<HomeRoute>().categoryId)
    private val isRefreshing = MutableStateFlow(false)

    private val events = Channel<HomeEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    private val storiesFlow = selectedCategoryId.flatMapLatest { getStories(it) }
    private val continueReadingFlow = selectedCategoryId.flatMapLatest { getContinueReading(it) }

    val uiState: StateFlow<HomeUiState> = combine(
        getCategories(),
        selectedCategoryId,
        storiesFlow,
        continueReadingFlow,
        combine(isRefreshing, getContentLanguages(), ::Pair),
    ) { categories, selectedId, stories, continueReading, (refreshing, languages) ->
        HomeUiState(
            categories = categories,
            selectedCategoryId = selectedId,
            stories = stories,
            continueReading = continueReading,
            isRefreshing = refreshing,
            isLoading = false,
            contentLanguages = languages,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    init {
        refresh()
    }

    fun onCategorySelected(categoryId: String?) {
        selectedCategoryId.value = categoryId
    }

    /** Shows Home (and Search, the reader, the widget…) in another language. Categories differ, so back to ALL. */
    fun onLanguageSelected(code: String) {
        selectedCategoryId.value = null
        viewModelScope.launch { setContentLanguage(code) }
    }

    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch {
            isRefreshing.value = true
            refreshContent()
                // New stories are on screen now, so the background check won't announce them later.
                .onSuccess { checkForNewStory(userIsInApp = true) }
                .onFailure { events.send(HomeEvent.RefreshFailed) }
            isRefreshing.value = false
        }
    }
}
