package com.vegam.myapplication.domain.usecase

import com.vegam.myapplication.domain.model.ThemeMode
import com.vegam.myapplication.domain.repository.SettingsRepository
import javax.inject.Inject

class SetThemeModeUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(mode: ThemeMode) = repository.setThemeMode(mode)
}
