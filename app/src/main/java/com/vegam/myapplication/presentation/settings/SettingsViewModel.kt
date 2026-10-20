package com.vegam.myapplication.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vegam.myapplication.domain.model.AppLanguage
import com.vegam.myapplication.domain.model.ThemeMode
import com.vegam.myapplication.domain.usecase.GetAppLanguageUseCase
import com.vegam.myapplication.domain.usecase.SetAppLanguageUseCase
import com.vegam.myapplication.domain.usecase.GetThemeModeUseCase
import com.vegam.myapplication.domain.usecase.SetThemeModeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    getThemeMode: GetThemeModeUseCase,
    private val setThemeMode: SetThemeModeUseCase,
    getAppLanguage: GetAppLanguageUseCase,
    private val setAppLanguage: SetAppLanguageUseCase,
) : ViewModel() {

    private val _language = MutableStateFlow(getAppLanguage())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    /** The screen is recreated right after this, showing every text in the new language. */
    fun onLanguageSelected(language: AppLanguage) {
        if (language == _language.value) return
        _language.value = language
        setAppLanguage(language)
    }

    val themeMode: StateFlow<ThemeMode> = getThemeMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    fun onThemeModeSelected(mode: ThemeMode) {
        viewModelScope.launch { setThemeMode(mode) }
    }
}
