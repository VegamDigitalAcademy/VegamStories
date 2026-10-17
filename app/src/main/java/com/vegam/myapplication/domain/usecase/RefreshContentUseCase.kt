package com.vegam.myapplication.domain.usecase

import com.vegam.myapplication.domain.repository.StoryRepository
import javax.inject.Inject

class RefreshContentUseCase @Inject constructor(
    private val repository: StoryRepository,
) {
    suspend operator fun invoke(): Result<Unit> = repository.refresh()
}
