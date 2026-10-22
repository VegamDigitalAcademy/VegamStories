package com.vegamstories.app.domain.usecase

import com.vegamstories.app.domain.model.Category
import com.vegamstories.app.domain.repository.StoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCategoriesUseCase @Inject constructor(
    private val repository: StoryRepository,
) {
    operator fun invoke(): Flow<List<Category>> = repository.observeCategories()
}
