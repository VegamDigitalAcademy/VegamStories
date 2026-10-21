package com.vegamstories.app.domain.usecase

import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.domain.repository.StoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetStoriesUseCase @Inject constructor(
    private val repository: StoryRepository,
) {
    operator fun invoke(categoryId: String?): Flow<List<Story>> = repository.observeStories(categoryId)
}
