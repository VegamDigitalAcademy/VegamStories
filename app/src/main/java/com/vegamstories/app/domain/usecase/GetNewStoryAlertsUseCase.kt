package com.vegamstories.app.domain.usecase

import com.vegamstories.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetNewStoryAlertsUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.newStoryAlertsEnabled
}
