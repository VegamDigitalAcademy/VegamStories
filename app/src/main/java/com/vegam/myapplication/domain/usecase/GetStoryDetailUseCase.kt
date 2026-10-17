package com.vegam.myapplication.domain.usecase

import com.vegam.myapplication.domain.model.StoryDetail
import com.vegam.myapplication.domain.repository.StoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetStoryDetailUseCase @Inject constructor(
    private val repository: StoryRepository,
) {
    operator fun invoke(storyId: String): Flow<StoryDetail?> = repository.observeStoryDetail(storyId)
}
