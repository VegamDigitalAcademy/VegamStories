package com.vegam.myapplication.domain.repository

import com.vegam.myapplication.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/** User preferences stored on the device. */
interface SettingsRepository {
    /** Emits [ThemeMode.SYSTEM] until the user picks something else. */
    val themeMode: Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)
}
