package com.vegamstories.app.presentation.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vegamstories.app.domain.model.PageBackground
import com.vegamstories.app.domain.model.ReaderScrollMode
import com.vegamstories.app.domain.model.ReaderSettings
import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.domain.model.StoryDetail
import com.vegamstories.app.domain.model.ReaderTextSize
import com.vegamstories.app.domain.model.StoryFormat
import com.vegamstories.app.domain.model.StoryText
import com.vegamstories.app.domain.model.Language
import com.vegamstories.app.domain.usecase.GetNextStoryUseCase
import com.vegamstories.app.domain.usecase.GetReaderSettingsUseCase
import com.vegamstories.app.domain.usecase.GetStoryDetailUseCase
import com.vegamstories.app.domain.usecase.SaveReadingProgressUseCase
import com.vegamstories.app.domain.usecase.ToggleDownloadUseCase
import com.vegamstories.app.domain.usecase.ToggleFavoriteUseCase
import com.vegamstories.app.domain.usecase.UpdateReaderSettingsUseCase
import com.vegamstories.app.presentation.navigation.ReaderRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    /** Comic page to open on: where the reader left off, unless the story was finished. */
    val initialPage: Int = 0,
    /** How far into the text to open, 0..1 (progress is shared by all formats). */
    val initialTextFraction: Float = 0f,
    /** Scroll mode, keep-screen-on, page background, format and text size; loaded before the reader first shows. */
    val settings: ReaderSettings = ReaderSettings(),
    /** The tab the user picked in this reader, if any. */
    val selectedFormat: StoryFormat? = null,
) {
    val hasVideo: Boolean get() = detail?.story?.video != null
    /** The "Did you like this story?" page, right after the last comic page. */
    val likePageIndex: Int get() = detail?.pages?.size ?: 0
    /** Comic pages + the like page. The video has its own Watch tab. */
    val totalPagerPages: Int get() = if (detail == null) 0 else likePageIndex + 1

    /** The formats this story comes in, in tab order. */
    val formats: Set<StoryFormat> get() = detail?.story?.formats.orEmpty()

    /** The tab to show: the one picked here, else the saved favourite, else the first the story has. */
    val format: StoryFormat
        get() = selectedFormat?.takeIf { it in formats }
            ?: settings.preferredFormat.takeIf { it in formats }
            ?: formats.firstOrNull()
            ?: StoryFormat.COMIC

    /** The text of the version shown, if it has one. */
    val text: StoryText? get() = detail?.text

    /** Languages the story is in (for the language chips), and the one shown. */
    val languages: List<Language> get() = detail?.languages.orEmpty()
    val language: String? get() = detail?.story?.language
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

    /** Captured from the first emission only, so saving progress doesn't move the pager or the text. */
    private var initialPage: Int? = null
    private var initialTextFraction = 0f

    private val selectedFormat = MutableStateFlow<StoryFormat?>(null)
    /** Language version picked with the chips in this reader; null = the app's story language. */
    private val selectedLanguage = MutableStateFlow<String?>(null)

    private val events = Channel<ReaderEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    val uiState: StateFlow<ReaderUiState> = combine(
        selectedLanguage.flatMapLatest { language -> getStoryDetail(storyId, language) },
        nextStory,
        getReaderSettings(),
        selectedFormat,
    ) { detail, next, settings, format ->
        if (detail != null && initialPage == null) {
            // Progress is per story, whichever format it was saved in: resume at the same fraction.
            val progress = detail.story.progress
            val fraction = if (progress == null || progress.isCompleted || progress.totalPages <= 0) {
                0f
            } else {
                progress.lastPageIndex.toFloat() / progress.totalPages
            }
            val comicPages = detail.pages.size
            initialPage = if (comicPages == 0) 0 else (fraction * comicPages).toInt().coerceIn(0, comicPages - 1)
            initialTextFraction = fraction
        }
        ReaderUiState(
            isLoading = false,
            detail = detail,
            nextStory = next,
            initialPage = initialPage ?: 0,
            initialTextFraction = initialTextFraction,
            settings = settings,
            selectedFormat = format,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderUiState())

    fun onPageChanged(pagerIndex: Int) {
        val pageCount = uiState.value.detail?.pages?.size ?: return
        viewModelScope.launch { saveReadingProgress(storyId, pagerIndex, pageCount) }
    }

    /** Read tab: [paragraphIndex] is the last paragraph on screen; the last one finishes the story. */
    fun onTextProgress(paragraphIndex: Int, paragraphCount: Int) {
        viewModelScope.launch { saveReadingProgress(storyId, paragraphIndex, paragraphCount) }
    }

    /** Switches tab and remembers it for the next story. */
    fun onFormatSelected(format: StoryFormat) {
        selectedFormat.value = format
        viewModelScope.launch { updateReaderSettings { it.copy(preferredFormat = format) } }
    }

    /** Shows another language version of this story (pages, text, video). Home's language stays as it is. */
    fun onLanguageSelected(languageCode: String) {
        selectedLanguage.value = languageCode
    }

    fun onTextSizeChange(size: ReaderTextSize) {
        viewModelScope.launch { updateReaderSettings { it.copy(textSize = size) } }
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
