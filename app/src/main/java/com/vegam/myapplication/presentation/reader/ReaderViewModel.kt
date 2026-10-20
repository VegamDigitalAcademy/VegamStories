package com.vegam.myapplication.presentation.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vegam.myapplication.domain.model.NarrationError
import com.vegam.myapplication.domain.model.NarrationState
import com.vegam.myapplication.domain.model.StoryDetail
import com.vegam.myapplication.domain.model.StoryPage
import com.vegam.myapplication.domain.service.StoryNarrator
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

/** Read-aloud state as the reader UI needs it. */
data class NarrationUiState(
    val isActive: Boolean = false,
    /** Comic page currently being read (the pager follows it). */
    val page: Int? = null,
    /** Panel on [page] being read right now, for the highlight. */
    val panelIndex: Int? = null,
)

data class ReaderUiState(
    val isLoading: Boolean = true,
    val detail: StoryDetail? = null,
    val nextStoryId: String? = null,
    /** Page to open on: where the reader left off, unless the story was finished. */
    val initialPage: Int = 0,
    val narration: NarrationUiState = NarrationUiState(),
) {
    val hasVideo: Boolean get() = detail?.story?.video != null
    /** Comic pages + optional video page at the end. */
    val totalPagerPages: Int get() = (detail?.pages?.size ?: 0) + if (hasVideo) 1 else 0
}

sealed interface ReaderEvent {
    data class FavoriteChanged(val isFavorite: Boolean) : ReaderEvent
    data class DownloadChanged(val isDownloaded: Boolean) : ReaderEvent
    data class NarrationFailed(val reason: NarrationError) : ReaderEvent
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getStoryDetail: GetStoryDetailUseCase,
    private val getNextStory: GetNextStoryUseCase,
    private val saveReadingProgress: SaveReadingProgressUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val toggleDownload: ToggleDownloadUseCase,
    private val narrator: StoryNarrator,
) : ViewModel() {

    private val storyId: String = savedStateHandle.toRoute<ReaderRoute>().storyId

    private val nextStoryId = MutableStateFlow<String?>(null)

    /** Captured from the first emission only, so saving progress doesn't move the pager. */
    private var initialPage: Int? = null

    private val events = Channel<ReaderEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    // ---- Read aloud ----
    /** Page being narrated; null when read-aloud is off. */
    private val narratedPage = MutableStateFlow<Int?>(null)

    /** For the current page: which panel each spoken line belongs to. */
    private var linePanels: List<Int> = emptyList()

    private val narrationUi = combine(narratedPage, narrator.state) { page, state ->
        if (page == null) {
            NarrationUiState()
        } else {
            NarrationUiState(
                isActive = true,
                page = page,
                panelIndex = (state as? NarrationState.Speaking)?.let { linePanels.getOrNull(it.segmentIndex) },
            )
        }
    }

    val uiState: StateFlow<ReaderUiState> = combine(
        getStoryDetail(storyId),
        nextStoryId,
        narrationUi,
    ) { detail, next, narration ->
        if (detail != null && initialPage == null) {
            val progress = detail.story.progress
            initialPage = if (progress == null || progress.isCompleted) 0 else progress.lastPageIndex
        }
        ReaderUiState(
            isLoading = false,
            detail = detail,
            nextStoryId = next,
            initialPage = initialPage ?: 0,
            narration = narration,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderUiState())

    init {
        viewModelScope.launch { nextStoryId.value = getNextStory(storyId) }
        viewModelScope.launch { narrator.state.collect { onNarrationState(it) } }
    }

    fun onPageChanged(pagerIndex: Int) {
        val pages = uiState.value.detail?.pages ?: return
        viewModelScope.launch { saveReadingProgress(storyId, pagerIndex, pages.size) }

        // User swiped while reading aloud → read the page they moved to (or stop on the video page).
        val reading = narratedPage.value
        if (reading != null && reading != pagerIndex) {
            if (pagerIndex in pages.indices) narratePage(pagerIndex) else stopNarration()
        }
    }

    /** Speaker button. [currentPage] is the page on screen. */
    fun onToggleNarration(currentPage: Int) {
        if (narratedPage.value != null) {
            stopNarration()
        } else {
            val pages = uiState.value.detail?.pages ?: return
            // On the video page there's nothing to read, so start from the first page.
            narratePage(if (currentPage in pages.indices) currentPage else 0)
        }
    }

    fun stopNarration() {
        if (narratedPage.value == null) return
        narratedPage.value = null
        linePanels = emptyList()
        narrator.stop()
    }

    /** Reads [page], skipping forward past pages that have no text. Stops after the last page. */
    private fun narratePage(page: Int) {
        val pages = uiState.value.detail?.pages ?: return
        val target = (page until pages.size).firstOrNull { linesFor(pages[it]).isNotEmpty() }
        if (target == null) {
            stopNarration()
            return
        }
        val lines = linesFor(pages[target])
        linePanels = lines.map { it.first }
        narratedPage.value = target
        narrator.speak(lines.map { it.second })
    }

    private suspend fun onNarrationState(state: NarrationState) {
        val page = narratedPage.value ?: return // not ours (e.g. left over from another screen)
        when (state) {
            NarrationState.Finished -> {
                val lastPage = (uiState.value.detail?.pages?.size ?: 0) - 1
                // Page done → turn the page and keep reading, like an audiobook.
                if (page < lastPage) narratePage(page + 1) else stopNarration()
            }
            is NarrationState.Error -> {
                narratedPage.value = null
                events.send(ReaderEvent.NarrationFailed(state.reason))
            }
            else -> Unit
        }
    }

    fun onToggleFavorite() {
        viewModelScope.launch { events.send(ReaderEvent.FavoriteChanged(toggleFavorite(storyId))) }
    }

    fun onToggleDownload() {
        viewModelScope.launch { events.send(ReaderEvent.DownloadChanged(toggleDownload(storyId))) }
    }

    override fun onCleared() {
        narrator.stop()
    }

    private companion object {
        /** Spoken lines for a page in reading order: each panel's caption, then its speech bubbles. */
        fun linesFor(page: StoryPage): List<Pair<Int, String>> =
            page.panels.flatMapIndexed { panelIndex, panel ->
                (listOfNotNull(panel.caption) + panel.dialogues)
                    .filter { it.isNotBlank() }
                    .map { panelIndex to it }
            }
    }
}
