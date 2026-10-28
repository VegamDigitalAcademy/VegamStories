package com.vegamstories.app.presentation.settings

import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import com.vegamstories.app.core.logging.SharedLogs
import com.vegamstories.app.core.logging.LogExporter
import com.vegamstories.app.core.logging.AppLogger
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

private const val TAG = "SettingsViewModel"

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
    private val logExporter: LogExporter,
) : ViewModel() {

    private val log = AppLogger(TAG)

    private val _language = MutableStateFlow(getAppLanguage())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    /** The screen is recreated right after this, showing every text in the new language. */
    fun onLanguageSelected(language: AppLanguage) {
        if (language == _language.value) return
        log.i("$TAG: User: app language $language")
        _language.value = language
        setAppLanguage(language)
    }

    val themeMode: StateFlow<ThemeMode> = getThemeMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    fun onThemeModeSelected(mode: ThemeMode) {
        log.i("$TAG: User: theme $mode")
        viewModelScope.launch { setThemeMode(mode) }
    }

    val newStoryAlerts: StateFlow<Boolean> = getNewStoryAlerts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The screen asks for the notification permission first, when needed. */
    fun onNewStoryAlertsChange(enabled: Boolean) {
        log.i("$TAG: User: new story alerts ${if (enabled) "on" else "off"}")
        viewModelScope.launch { setNewStoryAlerts(enabled) }
    }

    /** Auto-turn of comic pages (also kept with the reader's other settings). */
    val readerSettings: StateFlow<ReaderSettings> = getReaderSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderSettings())

    fun onAutoTurnChange(enabled: Boolean) {
        log.i("$TAG: User: auto-turn ${if (enabled) "on" else "off"}")
        viewModelScope.launch { updateReaderSettings { it.copy(autoTurnPages = enabled) } }
    }

    fun onAutoTurnSecondsChange(seconds: Int) {
        log.i("$TAG: User: auto-turn every $seconds s")
        viewModelScope.launch { updateReaderSettings { it.copy(autoTurnSeconds = seconds) } }
    }

    private val _shareLogs = MutableStateFlow(ShareLogsState.Idle)

    /** Settings → Support → Share logs: packing them, or the last try failed. */
    val shareLogs: StateFlow<ShareLogsState> = _shareLogs.asStateFlow()

    private val readyLogs = Channel<SharedLogs>(Channel.BUFFERED)

    /** The logs packed into a zip, ready for the share sheet. */
    val logsToShare: Flow<SharedLogs> = readyLogs.receiveAsFlow()

    /** Packs the logs with the user's [note] ("stories are not coming"); the screen then shares them. */
    fun onShareLogs(note: String) {
        if (_shareLogs.value == ShareLogsState.Preparing) return
        log.i("$TAG: User: share logs", data = mapOf("note" to note.trim()))
        _shareLogs.value = ShareLogsState.Preparing
        viewModelScope.launch {
            try {
                readyLogs.send(logExporter.export(note))
                _shareLogs.value = ShareLogsState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e("$TAG: Logs couldn't be packed for sharing", e)
                _shareLogs.value = ShareLogsState.Failed
            }
        }
    }

    /** The dialog was closed: a failure message isn't shown again next time. */
    fun onShareLogsDismissed() {
        if (_shareLogs.value == ShareLogsState.Failed) _shareLogs.value = ShareLogsState.Idle
    }
}
