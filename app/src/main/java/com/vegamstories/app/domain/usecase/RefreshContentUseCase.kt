package com.vegamstories.app.domain.usecase

import com.vegamstories.app.domain.repository.StoryRepository
import javax.inject.Inject

class RefreshContentUseCase @Inject constructor(
    private val repository: StoryRepository,
) {
    /** See [StoryRepository.refresh]: on its own the app downloads at most once a day. */
    suspend operator fun invoke(force: Boolean = false): Result<Boolean> = repository.refresh(force)
}
