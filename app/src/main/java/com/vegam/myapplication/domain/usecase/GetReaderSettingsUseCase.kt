package com.vegam.myapplication.domain.usecase

import com.vegam.myapplication.domain.model.ReaderSettings
import com.vegam.myapplication.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetReaderSettingsUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    operator fun invoke(): Flow<ReaderSettings> = repository.readerSettings
}
