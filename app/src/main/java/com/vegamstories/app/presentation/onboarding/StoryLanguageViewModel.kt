package com.vegamstories.app.presentation.onboarding

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

data class StoryLanguageUiState(
    val languages: List<Language> = emptyList(),
    /** Pre-highlighted: the phone's/app's language if stories exist in it. */
    val suggested: String? = null,
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

    val uiState: StateFlow<StoryLanguageUiState> = combine(getContentLanguages(), loading, failed) { languages, isLoading, isFailed ->
        StoryLanguageUiState(
            languages = languages.all,
            suggested = languages.selected,
            // Saved languages (from an earlier refresh) can be shown while a new refresh runs.
            isLoading = isLoading && languages.all.isEmpty(),
            loadFailed = isFailed && languages.all.isEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoryLanguageUiState())

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            loading.value = true
            failed.value = false
            failed.value = refreshContent().isFailure
            loading.value = false
        }
    }

    /** Saves the choice; MainActivity then shows Home in that language. */
    fun onLanguageChosen(code: String) {
        viewModelScope.launch { setContentLanguage(code) }
    }
}
