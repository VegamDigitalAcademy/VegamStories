package com.vegamstories.app.presentation.downloads

import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.onEach
import com.vegamstories.app.core.logging.AppLogger
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

private const val TAG = "DownloadsViewModel"

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    getDownloads: GetDownloadsUseCase,
    private val toggleDownload: ToggleDownloadUseCase,
) : ViewModel() {

    private val log = AppLogger(TAG)

    val uiState: StateFlow<StoryListUiState> = getDownloads()
        .distinctUntilChangedBy { stories -> stories.map { it.id } }
        .onEach { log.d("$TAG: Downloads shows ${it.size} stories", data = it.map { story -> story.id }) }
        .map { StoryListUiState(isLoading = false, stories = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoryListUiState())

    fun onRemoveDownload(storyId: String) {
        log.i("$TAG: User: remove download $storyId")
        viewModelScope.launch { toggleDownload(storyId) }
    }
}
