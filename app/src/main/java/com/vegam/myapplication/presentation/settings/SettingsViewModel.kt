package com.vegam.myapplication.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vegam.myapplication.domain.model.ThemeMode
import com.vegam.myapplication.domain.usecase.GetThemeModeUseCase
import com.vegam.myapplication.domain.usecase.SetThemeModeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    getThemeMode: GetThemeModeUseCase,
    private val setThemeMode: SetThemeModeUseCase,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = getThemeMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    fun onThemeModeSelected(mode: ThemeMode) {
        viewModelScope.launch { setThemeMode(mode) }
    }
}
