package com.vegamstories.app.domain.usecase

import com.vegamstories.app.domain.repository.StoryRepository
import javax.inject.Inject

class GetNextStoryUseCase @Inject constructor(
    private val repository: StoryRepository,
) {
    suspend operator fun invoke(currentStoryId: String): String? = repository.getNextStoryId(currentStoryId)
}
