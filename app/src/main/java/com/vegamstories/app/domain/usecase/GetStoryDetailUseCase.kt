package com.vegamstories.app.domain.usecase

import com.vegamstories.app.domain.model.StoryDetail
import com.vegamstories.app.domain.repository.StoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetStoryDetailUseCase @Inject constructor(
    private val repository: StoryRepository,
) {
    operator fun invoke(storyId: String): Flow<StoryDetail?> = repository.observeStoryDetail(storyId)
}
