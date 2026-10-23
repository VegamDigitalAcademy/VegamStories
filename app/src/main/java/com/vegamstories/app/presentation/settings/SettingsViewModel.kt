package com.vegamstories.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vegamstories.app.domain.model.AppLanguage
import com.vegamstories.app.domain.model.ReaderSettings
import com.vegamstories.app.domain.model.ThemeMode
import com.vegamstories.app.domain.usecase.GetAppLanguageUseCase
import com.vegamstories.app.domain.usecase.GetNewStoryAlertsUseCase
import com.vegamstories.app.domain.usecase.GetReaderSettingsUseCase
import com.vegamstories.app.domain.usecase.SetNewStoryAlertsUseCase
import com.vegamstories.app.domain.usecase.SetAppLanguageUseCase
import com.vegamstories.app.domain.usecase.GetThemeModeUseCase
import com.vegamstories.app.domain.usecase.SetThemeModeUseCase
import com.vegamstories.app.domain.usecase.UpdateReaderSettingsUseCase
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
    getNewStoryAlerts: GetNewStoryAlertsUseCase,
    private val setNewStoryAlerts: SetNewStoryAlertsUseCase,
    getReaderSettings: GetReaderSettingsUseCase,
    private val updateReaderSettings: UpdateReaderSettingsUseCase,
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

    val newStoryAlerts: StateFlow<Boolean> = getNewStoryAlerts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The screen asks for the notification permission first, when needed. */
    fun onNewStoryAlertsChange(enabled: Boolean) {
        viewModelScope.launch { setNewStoryAlerts(enabled) }
    }

    /** Auto-turn of comic pages (also kept with the reader's other settings). */
    val readerSettings: StateFlow<ReaderSettings> = getReaderSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderSettings())

    fun onAutoTurnChange(enabled: Boolean) {
        viewModelScope.launch { updateReaderSettings { it.copy(autoTurnPages = enabled) } }
    }

    fun onAutoTurnSecondsChange(seconds: Int) {
        viewModelScope.launch { updateReaderSettings { it.copy(autoTurnSeconds = seconds) } }
    }
}
