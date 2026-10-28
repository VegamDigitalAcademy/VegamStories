package com.vegamstories.app.domain.usecase

import com.vegamstories.app.domain.model.Bookmark
import com.vegamstories.app.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** The pages bookmarked in one story, for the ribbons in the reader. */
class GetStoryBookmarksUseCase @Inject constructor(
    private val repository: LibraryRepository,
) {
    operator fun invoke(storyId: String): Flow<List<Bookmark>> = repository.observeBookmarks(storyId)
}
