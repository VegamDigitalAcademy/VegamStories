package com.vegam.myapplication.presentation.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vegam.myapplication.domain.usecase.CheckForNewStoryUseCase
import com.vegam.myapplication.domain.usecase.GetCategoriesUseCase
import com.vegam.myapplication.domain.usecase.GetContinueReadingUseCase
import com.vegam.myapplication.domain.usecase.GetStoriesUseCase
import com.vegam.myapplication.domain.usecase.RefreshContentUseCase
import com.vegam.myapplication.presentation.navigation.HomeRoute
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
        isRefreshing,
    ) { categories, selectedId, stories, continueReading, refreshing ->
        HomeUiState(
            categories = categories,
            selectedCategoryId = selectedId,
            stories = stories,
            continueReading = continueReading,
            isRefreshing = refreshing,
            isLoading = false,
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
