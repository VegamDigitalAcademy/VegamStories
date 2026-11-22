package com.vegamstories.app.domain.usecase

import com.vegamstories.app.domain.model.StoryDetail
import com.vegamstories.app.domain.repository.StoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetStoryDetailUseCase @Inject constructor(
    private val repository: StoryRepository,
) {
    /** [language] null = the app's story language. */
    operator fun invoke(storyId: String, language: String? = null): Flow<StoryDetail?> =
        repository.observeStoryDetail(storyId, language)
}
