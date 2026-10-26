package com.vegamstories.app.presentation.bookshelf

import kotlinx.coroutines.flow.onEach
import com.vegamstories.app.core.logging.AppLogger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vegamstories.app.domain.model.SeriesShelf
import com.vegamstories.app.domain.usecase.GetBookshelfUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private const val TAG = "BookshelfViewModel"

data class BookshelfUiState(
    val isLoading: Boolean = true,
    val shelves: List<SeriesShelf> = emptyList(),
) {
    val finishedCount: Int get() = shelves.sumOf { it.finished.size }
    val completeSeries: Int get() = shelves.count { it.isComplete }
}

@HiltViewModel
class BookshelfViewModel @Inject constructor(
    getBookshelf: GetBookshelfUseCase,
) : ViewModel() {
    private val log = AppLogger(TAG)

    val uiState: StateFlow<BookshelfUiState> = getBookshelf()
        .onEach { shelves ->
            log.d(
                "$TAG: Bookshelf shows ${shelves.sumOf { it.finished.size }} finished stories",
                data = shelves.associate { it.name to "${it.readCount} of ${it.total}" + if (it.isComplete) " (complete)" else "" },
            )
        }
        .map { BookshelfUiState(isLoading = false, shelves = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookshelfUiState())
}
