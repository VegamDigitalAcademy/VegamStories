package com.vegamstories.app.domain.usecase

import com.vegamstories.app.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ToggleDownloadUseCase @Inject constructor(
    private val repository: LibraryRepository,
) {
    suspend operator fun invoke(storyId: String): Boolean = repository.toggleDownload(storyId)

    /** How much of [storyId]'s download is saved, 0 … 1, while it's saving; else null. */
    fun progress(storyId: String): Flow<Float?> = repository.observeDownloadProgress(storyId)
}
