package com.vegam.myapplication.domain.repository

import com.vegam.myapplication.domain.model.ReaderSettings
import com.vegam.myapplication.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/** User preferences stored on the device. */
interface SettingsRepository {
    /** Emits [ThemeMode.SYSTEM] until the user picks something else. */
    val themeMode: Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)

    /** Emits the defaults of [ReaderSettings] for anything the user hasn't changed. */
    val readerSettings: Flow<ReaderSettings>

    /** Applies [transform] to the saved settings in one atomic edit. */
    suspend fun updateReaderSettings(transform: (ReaderSettings) -> ReaderSettings)
}
