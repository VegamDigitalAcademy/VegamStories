package com.vegam.myapplication.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vegam.myapplication.domain.usecase.SearchStoriesUseCase
import com.vegam.myapplication.domain.usecase.ToggleFavoriteUseCase
import com.vegam.myapplication.presentation.common.StoryListUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchStories: SearchStoriesUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val results: StateFlow<StoryListUiState> = _query
        .debounce(250)
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest { q -> searchStories(q).map { StoryListUiState(isLoading = false, stories = it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoryListUiState(isLoading = false))

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onToggleFavorite(storyId: String) {
        viewModelScope.launch { toggleFavorite(storyId) }
    }
}
