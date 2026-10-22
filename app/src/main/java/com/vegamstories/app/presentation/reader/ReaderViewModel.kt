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
import com.vegamstories.app.domain.model.pick
import com.vegamstories.app.domain.usecase.GetAppLanguageUseCase
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
import java.util.Locale
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
    /** Language code of the text picked in this reader, if any. */
    val selectedTextLanguage: String? = null,
    /** Language code of the app (Settings) or the phone, e.g. "te"; used until the user picks a text language. */
    val appTextLanguage: String? = null,
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

    /** The story's texts, one per language, in the server's language order. */
    val texts: List<StoryText> get() = detail?.texts.orEmpty()

    /**
     * The text to read: the language picked here, else the one picked last time (any story),
     * else the app's language, else the first language in the server's order.
     */
    val text: StoryText? get() = texts.pick(selectedTextLanguage, settings.textLanguage, appTextLanguage)
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
    getAppLanguage: GetAppLanguageUseCase,
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
    private val selectedTextLanguage = MutableStateFlow<String?>(null)

    /** The app's language from Settings, or the phone's (e.g. "or" on an Odia phone). */
    private val appTextLanguage: String = getAppLanguage().languageTag ?: Locale.getDefault().language

    private val events = Channel<ReaderEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    val uiState: StateFlow<ReaderUiState> = combine(
        getStoryDetail(storyId),
        nextStory,
        getReaderSettings(),
        selectedFormat,
        selectedTextLanguage,
    ) { detail, next, settings, format, textLanguage ->
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
            selectedTextLanguage = textLanguage,
            appTextLanguage = appTextLanguage,
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

    /** Switches the text language and remembers it for the next story. */
    fun onTextLanguageSelected(languageCode: String) {
        selectedTextLanguage.value = languageCode
        viewModelScope.launch { updateReaderSettings { it.copy(textLanguage = languageCode) } }
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
