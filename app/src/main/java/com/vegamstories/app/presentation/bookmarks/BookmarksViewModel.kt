package com.vegamstories.app.presentation.bookmarks

import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.onEach
import com.vegamstories.app.core.logging.AppLogger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vegamstories.app.domain.model.Bookmark
import com.vegamstories.app.domain.model.BookmarkItem
import com.vegamstories.app.domain.usecase.GetBookmarksUseCase
import com.vegamstories.app.domain.usecase.RemoveBookmarksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "BookmarksViewModel"

data class BookmarksUiState(
    val isLoading: Boolean = true,
    val items: List<BookmarkItem> = emptyList(),
)

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    getBookmarks: GetBookmarksUseCase,
    private val removeBookmarks: RemoveBookmarksUseCase,
) : ViewModel() {

    private val log = AppLogger(TAG)

    val uiState: StateFlow<BookmarksUiState> = getBookmarks()
        .distinctUntilChangedBy { items -> items.map { it.bookmark } }
        .onEach { log.d("$TAG: Bookmarks shows ${it.size}", data = it.map { item -> item.bookmark }) }
        .map { BookmarksUiState(isLoading = false, items = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookmarksUiState())

    fun onRemove(bookmark: Bookmark) {
        log.i("$TAG: User: remove bookmark", data = bookmark)
        viewModelScope.launch { removeBookmarks(listOf(bookmark)) }
    }
}
