package com.vegamstories.app.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vegamstories.app.domain.usecase.ClearReadingHistoryUseCase
import com.vegamstories.app.domain.usecase.GetReadingHistoryUseCase
import com.vegamstories.app.presentation.common.StoryListUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    getReadingHistory: GetReadingHistoryUseCase,
    private val clearReadingHistory: ClearReadingHistoryUseCase,
) : ViewModel() {

    val uiState: StateFlow<StoryListUiState> = getReadingHistory()
        .map { StoryListUiState(isLoading = false, stories = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoryListUiState())

    fun onClearHistory() {
        viewModelScope.launch { clearReadingHistory() }
    }
}
