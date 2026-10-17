package com.vegam.myapplication.domain.usecase

import com.vegam.myapplication.domain.model.Story
import com.vegam.myapplication.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetContinueReadingUseCase @Inject constructor(
    private val repository: LibraryRepository,
) {
    operator fun invoke(categoryId: String?): Flow<Story?> = repository.observeContinueReading(categoryId)
}
