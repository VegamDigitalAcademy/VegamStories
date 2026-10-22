package com.vegamstories.app.domain.usecase

import com.vegamstories.app.domain.repository.StoryRepository
import javax.inject.Inject

class RefreshContentUseCase @Inject constructor(
    private val repository: StoryRepository,
) {
    suspend operator fun invoke(): Result<Unit> = repository.refresh()
}
