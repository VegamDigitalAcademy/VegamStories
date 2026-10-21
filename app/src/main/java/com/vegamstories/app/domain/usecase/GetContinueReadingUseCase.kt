package com.vegamstories.app.domain.usecase

import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetContinueReadingUseCase @Inject constructor(
    private val repository: LibraryRepository,
) {
    operator fun invoke(categoryId: String?): Flow<Story?> = repository.observeContinueReading(categoryId)
}
