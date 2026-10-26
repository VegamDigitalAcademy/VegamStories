package com.vegamstories.app.presentation

import kotlinx.coroutines.flow.onEach
import com.vegamstories.app.core.logging.AppLogger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vegamstories.app.domain.model.ThemeMode
import com.vegamstories.app.domain.usecase.GetContentLanguagesUseCase
import com.vegamstories.app.domain.usecase.GetThemeModeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private const val TAG = "MainViewModel"

/** App-level state for MainActivity: the saved theme, and whether a story language is chosen yet. */
@HiltViewModel
class MainViewModel @Inject constructor(
    getThemeMode: GetThemeModeUseCase,
    getContentLanguages: GetContentLanguagesUseCase,
) : ViewModel() {

    private val log = AppLogger(TAG)

    /** null while the saved setting is being read (a few milliseconds at launch). */
    val themeMode: StateFlow<ThemeMode?> = getThemeMode()
        .onEach { log.d("$TAG: Theme: $it") }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** true until the user picks a story language (first launch); null while being read. */
    val needsStoryLanguage: StateFlow<Boolean?> = getContentLanguages()
        .map { !it.isChosen }
        .onEach { needsChoice -> log.d(if (needsChoice) "$TAG: No story language chosen yet: showing the first-launch screen" else "$TAG: Story language already chosen") }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
