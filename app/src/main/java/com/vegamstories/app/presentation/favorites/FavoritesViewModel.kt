package com.vegamstories.app.presentation.favorites

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

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    getFavorites: GetFavoritesUseCase,
) : ViewModel() {

    val uiState: StateFlow<StoryListUiState> = getFavorites()
        .map { StoryListUiState(isLoading = false, stories = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoryListUiState())
}
