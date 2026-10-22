package com.vegamstories.app.domain.usecase

import com.vegamstories.app.domain.repository.LibraryRepository
import javax.inject.Inject

class ToggleFavoriteUseCase @Inject constructor(
    private val repository: LibraryRepository,
) {
    suspend operator fun invoke(storyId: String): Boolean = repository.toggleFavorite(storyId)
}
