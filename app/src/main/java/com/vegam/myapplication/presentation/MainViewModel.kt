package com.vegam.myapplication.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vegam.myapplication.domain.model.ThemeMode
import com.vegam.myapplication.domain.usecase.GetThemeModeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** App-level state for MainActivity: currently just the saved theme choice. */
@HiltViewModel
class MainViewModel @Inject constructor(
    getThemeMode: GetThemeModeUseCase,
) : ViewModel() {

    /** null while the saved setting is being read (a few milliseconds at launch). */
    val themeMode: StateFlow<ThemeMode?> = getThemeMode()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
