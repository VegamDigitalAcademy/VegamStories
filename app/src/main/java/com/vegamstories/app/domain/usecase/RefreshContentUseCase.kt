package com.vegamstories.app.domain.usecase

import com.vegamstories.app.domain.repository.StoryRepository
import javax.inject.Inject

class RefreshContentUseCase @Inject constructor(
    private val repository: StoryRepository,
) {
    /** See [StoryRepository.refresh]: on its own the app checks at most every few minutes, and downloads only what changed. */
    suspend operator fun invoke(force: Boolean = false): Result<Boolean> = repository.refresh(force)
}
