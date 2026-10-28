package com.vegamstories.app.presentation.onboarding

import com.vegamstories.app.core.logging.AppLogger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vegamstories.app.domain.model.Language
import com.vegamstories.app.domain.usecase.GetContentLanguagesUseCase
import com.vegamstories.app.domain.usecase.RefreshContentUseCase
import com.vegamstories.app.domain.usecase.SetContentLanguageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "StoryLanguageViewModel"

data class StoryLanguageUiState(
    val languages: List<Language> = emptyList(),
    /** Pre-selected: the phone's/app's language if stories exist in it. */
    val suggested: String? = null,
    /** The card tapped (else [suggested]); saved only when "Let's go" is pressed. */
    val selected: String? = null,
    val isLoading: Boolean = true,
    /** Languages couldn't be loaded (offline on first launch) and none are saved yet. */
    val loadFailed: Boolean = false,
)

/**
 * The first-launch "Choose your story language" screen. The languages come from the server,
 * so on a fresh install the stories are loaded first (Home then opens straight away).
 */
@HiltViewModel
class StoryLanguageViewModel @Inject constructor(
    getContentLanguages: GetContentLanguagesUseCase,
    private val refreshContent: RefreshContentUseCase,
    private val setContentLanguage: SetContentLanguageUseCase,
) : ViewModel() {

    private val loading = MutableStateFlow(true)
    private val failed = MutableStateFlow(false)
    private val tapped = MutableStateFlow<String?>(null)

    val uiState: StateFlow<StoryLanguageUiState> = combine(getContentLanguages(), loading, failed, tapped) { languages, isLoading, isFailed, picked ->
        val codes = languages.all.map { it.code }
        StoryLanguageUiState(
            languages = languages.all,
            suggested = languages.selected,
            selected = picked?.takeIf { it in codes } ?: languages.selected.takeIf { it in codes },
            // Saved languages (from an earlier refresh) can be shown while a new refresh runs.
            isLoading = isLoading && languages.all.isEmpty(),
            loadFailed = isFailed && languages.all.isEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoryLanguageUiState())

    private val log = AppLogger(TAG)

    init {
        log.i("$TAG: First launch: choose your story language")
        load()
    }

    fun load() {
        viewModelScope.launch {
            loading.value = true
            failed.value = false
            val result = refreshContent()
            failed.value = result.isFailure
            result.onFailure { log.w("$TAG: Languages couldn't be loaded (offline?)", it) }
            loading.value = false
        }
    }

    /** A card was tapped: only selects it. */
    fun onLanguageSelected(code: String) {
        log.i("$TAG: User: tapped $code")
        tapped.value = code
    }

    /** "Let's go" (or Continue when offline): saves [code]; MainActivity then shows Home in that language. */
    fun onLanguageChosen(code: String) {
        log.i("$TAG: User: Let's go with $code")
        viewModelScope.launch { setContentLanguage(code) }
    }
}
