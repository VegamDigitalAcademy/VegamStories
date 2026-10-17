package com.vegam.myapplication.domain.usecase

import com.vegam.myapplication.domain.model.Story
import com.vegam.myapplication.domain.repository.StoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class SearchStoriesUseCase @Inject constructor(
    private val repository: StoryRepository,
) {
    operator fun invoke(query: String): Flow<List<Story>> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) flowOf(emptyList()) else repository.searchStories(trimmed)
    }
}
