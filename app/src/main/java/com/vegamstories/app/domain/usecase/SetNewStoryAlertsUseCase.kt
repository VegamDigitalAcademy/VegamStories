package com.vegamstories.app.domain.usecase

import com.vegamstories.app.domain.repository.SettingsRepository
import javax.inject.Inject

class SetNewStoryAlertsUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(enabled: Boolean) = repository.setNewStoryAlertsEnabled(enabled)
}
