package com.vegam.myapplication.domain.usecase

import com.vegam.myapplication.domain.repository.StoryRepository
import javax.inject.Inject

class GetNextStoryUseCase @Inject constructor(
    private val repository: StoryRepository,
) {
    suspend operator fun invoke(currentStoryId: String): String? = repository.getNextStoryId(currentStoryId)
}
