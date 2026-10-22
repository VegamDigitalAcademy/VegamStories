package com.vegamstories.app.domain.usecase

import com.vegamstories.app.domain.model.ThemeMode
import com.vegamstories.app.domain.repository.SettingsRepository
import javax.inject.Inject

class SetThemeModeUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(mode: ThemeMode) = repository.setThemeMode(mode)
}
