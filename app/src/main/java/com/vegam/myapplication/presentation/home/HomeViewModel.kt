package com.vegam.myapplication.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vegam.myapplication.domain.usecase.GetCategoriesUseCase
import com.vegam.myapplication.domain.usecase.GetContinueReadingUseCase
import com.vegam.myapplication.domain.usecase.GetStoriesUseCase
import com.vegam.myapplication.domain.usecase.RefreshContentUseCase
import com.vegam.myapplication.domain.usecase.ToggleFavoriteUseCase
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
    getCategories: GetCategoriesUseCase,
    private val getStories: GetStoriesUseCase,
    private val getContinueReading: GetContinueReadingUseCase,
    private val refreshContent: RefreshContentUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
) : ViewModel() {

    private val selectedCategoryId = MutableStateFlow<String?>(null)
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
            refreshContent().onFailure { events.send(HomeEvent.RefreshFailed) }
            isRefreshing.value = false
        }
    }

    fun onToggleFavorite(storyId: String) {
        viewModelScope.launch {
            val nowFavorite = toggleFavorite(storyId)
            events.send(HomeEvent.FavoriteChanged(nowFavorite))
        }
    }
}
