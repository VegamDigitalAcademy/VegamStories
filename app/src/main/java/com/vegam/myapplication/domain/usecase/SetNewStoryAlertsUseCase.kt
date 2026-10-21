package com.vegam.myapplication.domain.usecase

import com.vegam.myapplication.domain.repository.SettingsRepository
import javax.inject.Inject

class SetNewStoryAlertsUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(enabled: Boolean) = repository.setNewStoryAlertsEnabled(enabled)
}
