package com.vegamstories.app.domain.usecase

import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetDownloadsUseCase @Inject constructor(
    private val repository: LibraryRepository,
) {
    operator fun invoke(): Flow<List<Story>> = repository.observeDownloads()
}
