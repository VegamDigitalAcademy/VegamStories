package com.vegam.myapplication.domain.usecase

import com.vegam.myapplication.domain.repository.LibraryRepository
import javax.inject.Inject

class ClearReadingHistoryUseCase @Inject constructor(
    private val repository: LibraryRepository,
) {
    suspend operator fun invoke() = repository.clearReadingHistory()
}
