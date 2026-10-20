package com.vegam.myapplication.domain.usecase

import com.vegam.myapplication.domain.model.ThemeMode
import com.vegam.myapplication.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetThemeModeUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    operator fun invoke(): Flow<ThemeMode> = repository.themeMode
}
