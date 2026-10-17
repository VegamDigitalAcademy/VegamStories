package com.vegam.myapplication.domain.usecase

import com.vegam.myapplication.domain.model.Story
import com.vegam.myapplication.domain.repository.StoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetStoriesUseCase @Inject constructor(
    private val repository: StoryRepository,
) {
    operator fun invoke(categoryId: String?): Flow<List<Story>> = repository.observeStories(categoryId)
}
