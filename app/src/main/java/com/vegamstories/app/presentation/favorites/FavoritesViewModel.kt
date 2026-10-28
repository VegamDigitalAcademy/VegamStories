package com.vegamstories.app.presentation.favorites

import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.onEach
import com.vegamstories.app.core.logging.AppLogger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vegamstories.app.domain.usecase.GetFavoritesUseCase
import com.vegamstories.app.presentation.common.StoryListUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private const val TAG = "FavoritesViewModel"

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    getFavorites: GetFavoritesUseCase,
) : ViewModel() {

    private val log = AppLogger(TAG)

    val uiState: StateFlow<StoryListUiState> = getFavorites()
        .distinctUntilChangedBy { stories -> stories.map { it.id } }
        .onEach { log.d("$TAG: Favorites shows ${it.size} stories", data = it.map { story -> story.id }) }
        .map { StoryListUiState(isLoading = false, stories = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoryListUiState())
}
