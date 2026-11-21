package com.vegamstories.app.domain.usecase

import com.vegamstories.app.domain.repository.LibraryRepository
import javax.inject.Inject

/** The heart: likes or unlikes a story (counted on the server). Separate from Favorites (the star). */
class ToggleLikeUseCase @Inject constructor(
    private val repository: LibraryRepository,
) {
    suspend operator fun invoke(storyId: String): Boolean = repository.toggleLike(storyId)
}
