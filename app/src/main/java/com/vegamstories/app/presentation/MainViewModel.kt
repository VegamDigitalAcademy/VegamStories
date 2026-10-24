package com.vegamstories.app.presentation

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

/** App-level state for MainActivity: the saved theme, and whether a story language is chosen yet. */
@HiltViewModel
class MainViewModel @Inject constructor(
    getThemeMode: GetThemeModeUseCase,
    getContentLanguages: GetContentLanguagesUseCase,
) : ViewModel() {

    /** null while the saved setting is being read (a few milliseconds at launch). */
    val themeMode: StateFlow<ThemeMode?> = getThemeMode()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** true until the user picks a story language (first launch); null while being read. */
    val needsStoryLanguage: StateFlow<Boolean?> = getContentLanguages()
        .map { !it.isChosen }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
