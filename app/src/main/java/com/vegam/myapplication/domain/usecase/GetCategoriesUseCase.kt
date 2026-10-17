package com.vegam.myapplication.domain.usecase

import com.vegam.myapplication.domain.model.Category
import com.vegam.myapplication.domain.repository.StoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCategoriesUseCase @Inject constructor(
    private val repository: StoryRepository,
) {
    operator fun invoke(): Flow<List<Category>> = repository.observeCategories()
}
