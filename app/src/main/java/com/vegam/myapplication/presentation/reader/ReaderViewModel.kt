package com.vegam.myapplication.presentation.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vegam.myapplication.domain.model.PageBackground
import com.vegam.myapplication.domain.model.ReaderScrollMode
import com.vegam.myapplication.domain.model.ReaderSettings
import com.vegam.myapplication.domain.model.Story
import com.vegam.myapplication.domain.model.StoryDetail
import com.vegam.myapplication.domain.usecase.GetNextStoryUseCase
import com.vegam.myapplication.domain.usecase.GetReaderSettingsUseCase
import com.vegam.myapplication.domain.usecase.GetStoryDetailUseCase
import com.vegam.myapplication.domain.usecase.SaveReadingProgressUseCase
import com.vegam.myapplication.domain.usecase.ToggleDownloadUseCase
import com.vegam.myapplication.domain.usecase.ToggleFavoriteUseCase
import com.vegam.myapplication.domain.usecase.UpdateReaderSettingsUseCase
import com.vegam.myapplication.presentation.navigation.ReaderRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReaderUiState(
    val isLoading: Boolean = true,
    val detail: StoryDetail? = null,
    /** Next episode in the same category, offered at the end; null on the last episode. */
    val nextStory: Story? = null,
    /** Page to open on: where the reader left off, unless the story was finished. */
    val initialPage: Int = 0,
    /** Scroll mode, keep-screen-on and page background; loaded before the reader first shows. */
    val settings: ReaderSettings = ReaderSettings(),
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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getStoryDetail: GetStoryDetailUseCase,
    getNextStory: GetNextStoryUseCase,
    getReaderSettings: GetReaderSettingsUseCase,
    private val updateReaderSettings: UpdateReaderSettingsUseCase,
    private val saveReadingProgress: SaveReadingProgressUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val toggleDownload: ToggleDownloadUseCase,
) : ViewModel() {

    private val storyId: String = savedStateHandle.toRoute<ReaderRoute>().storyId

    /** Live, so its cover/title/like count stay current while the reader is open. */
    private val nextStory: Flow<Story?> = flow { emit(getNextStory(storyId)) }
        .flatMapLatest { id -> if (id == null) flowOf(null) else getStoryDetail(id).map { it?.story } }

    /** Captured from the first emission only, so saving progress doesn't move the pager. */
    private var initialPage: Int? = null

    private val events = Channel<ReaderEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    val uiState: StateFlow<ReaderUiState> = combine(
        getStoryDetail(storyId),
        nextStory,
        getReaderSettings(),
    ) { detail, next, settings ->
        if (detail != null && initialPage == null) {
            val progress = detail.story.progress
            initialPage = if (progress == null || progress.isCompleted) 0 else progress.lastPageIndex
        }
        ReaderUiState(
            isLoading = false,
            detail = detail,
            nextStory = next,
            initialPage = initialPage ?: 0,
            settings = settings,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderUiState())

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

    fun onScrollModeChange(mode: ReaderScrollMode) {
        viewModelScope.launch { updateReaderSettings { it.copy(scrollMode = mode) } }
    }

    fun onKeepScreenOnChange(keepScreenOn: Boolean) {
        viewModelScope.launch { updateReaderSettings { it.copy(keepScreenOn = keepScreenOn) } }
    }

    fun onPageBackgroundChange(background: PageBackground) {
        viewModelScope.launch { updateReaderSettings { it.copy(pageBackground = background) } }
    }
}
