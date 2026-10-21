package com.vegam.myapplication.domain.usecase

import com.vegam.myapplication.domain.model.ReaderSettings
import com.vegam.myapplication.domain.repository.SettingsRepository
import javax.inject.Inject

class UpdateReaderSettingsUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(transform: (ReaderSettings) -> ReaderSettings) =
        repository.updateReaderSettings(transform)
}
