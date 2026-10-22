package com.vegamstories.app.domain.usecase

import com.vegamstories.app.domain.repository.LibraryRepository
import javax.inject.Inject

class ClearReadingHistoryUseCase @Inject constructor(
    private val repository: LibraryRepository,
) {
    suspend operator fun invoke() = repository.clearReadingHistory()
}
