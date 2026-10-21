package com.vegamstories.app.presentation.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vegamstories.app.domain.usecase.GetDownloadsUseCase
import com.vegamstories.app.domain.usecase.ToggleDownloadUseCase
import com.vegamstories.app.presentation.common.StoryListUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    getDownloads: GetDownloadsUseCase,
    private val toggleDownload: ToggleDownloadUseCase,
) : ViewModel() {

    val uiState: StateFlow<StoryListUiState> = getDownloads()
        .map { StoryListUiState(isLoading = false, stories = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoryListUiState())

    fun onRemoveDownload(storyId: String) {
        viewModelScope.launch { toggleDownload(storyId) }
    }
}
