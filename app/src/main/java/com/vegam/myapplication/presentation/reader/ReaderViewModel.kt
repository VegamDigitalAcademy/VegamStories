package com.vegam.myapplication.presentation.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vegam.myapplication.domain.model.StoryDetail
import com.vegam.myapplication.domain.usecase.GetNextStoryUseCase
import com.vegam.myapplication.domain.usecase.GetStoryDetailUseCase
import com.vegam.myapplication.domain.usecase.SaveReadingProgressUseCase
import com.vegam.myapplication.domain.usecase.ToggleDownloadUseCase
import com.vegam.myapplication.domain.usecase.ToggleFavoriteUseCase
import com.vegam.myapplication.presentation.navigation.ReaderRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReaderUiState(
    val isLoading: Boolean = true,
    val detail: StoryDetail? = null,
    val nextStoryId: String? = null,
    /** Page to open on: where the reader left off, unless the story was finished. */
    val initialPage: Int = 0,
) {
    val hasVideo: Boolean get() = detail?.story?.video != null
    /** The "Did you like this story?" page, right after the last comic page. */
    val likePageIndex: Int get() = detail?.pages?.size ?: 0
    /** Comic pages + the like page + optional video page at the end. */
    val totalPagerPages: Int get() = if (detail == null) 0 else likePageIndex + 1 + if (hasVideo) 1 else 0
}

sealed interface ReaderEvent {
    data class FavoriteChanged(val isFavorite: Boolean) : ReaderEvent
    data class DownloadChanged(val isDownloaded: Boolean) : ReaderEvent
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getStoryDetail: GetStoryDetailUseCase,
    private val getNextStory: GetNextStoryUseCase,
    private val saveReadingProgress: SaveReadingProgressUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val toggleDownload: ToggleDownloadUseCase,
) : ViewModel() {

    private val storyId: String = savedStateHandle.toRoute<ReaderRoute>().storyId

    private val nextStoryId = MutableStateFlow<String?>(null)

    /** Captured from the first emission only, so saving progress doesn't move the pager. */
    private var initialPage: Int? = null

    private val events = Channel<ReaderEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    val uiState: StateFlow<ReaderUiState> = combine(
        getStoryDetail(storyId),
        nextStoryId,
    ) { detail, next ->
        if (detail != null && initialPage == null) {
            val progress = detail.story.progress
            initialPage = if (progress == null || progress.isCompleted) 0 else progress.lastPageIndex
        }
        ReaderUiState(
            isLoading = false,
            detail = detail,
            nextStoryId = next,
            initialPage = initialPage ?: 0,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderUiState())

    init {
        viewModelScope.launch { nextStoryId.value = getNextStory(storyId) }
    }

    fun onPageChanged(pagerIndex: Int) {
        val pageCount = uiState.value.detail?.pages?.size ?: return
        viewModelScope.launch { saveReadingProgress(storyId, pagerIndex, pageCount) }
    }

    fun onToggleFavorite() {
        viewModelScope.launch { events.send(ReaderEvent.FavoriteChanged(toggleFavorite(storyId))) }
    }

    fun onToggleDownload() {
        viewModelScope.launch { events.send(ReaderEvent.DownloadChanged(toggleDownload(storyId))) }
    }
}
