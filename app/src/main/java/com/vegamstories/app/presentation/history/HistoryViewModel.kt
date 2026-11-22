package com.vegamstories.app.presentation.history

import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.onEach
import com.vegamstories.app.core.logging.AppLogger
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

private const val TAG = "HistoryViewModel"

@HiltViewModel
class HistoryViewModel @Inject constructor(
    getReadingHistory: GetReadingHistoryUseCase,
    private val clearReadingHistory: ClearReadingHistoryUseCase,
) : ViewModel() {

    private val log = AppLogger(TAG)

    val uiState: StateFlow<StoryListUiState> = getReadingHistory()
        .distinctUntilChangedBy { stories -> stories.map { it.id } }
        .onEach { log.d("$TAG: Read stories shows ${it.size}", data = it.map { story -> story.id }) }
        .map { StoryListUiState(isLoading = false, stories = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoryListUiState())

    fun onClearHistory() {
        log.i("$TAG: User: clear reading history")
        viewModelScope.launch { clearReadingHistory() }
    }
}
