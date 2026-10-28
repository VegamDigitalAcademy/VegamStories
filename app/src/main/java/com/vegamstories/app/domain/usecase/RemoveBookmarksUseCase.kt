package com.vegamstories.app.domain.usecase

import com.vegamstories.app.domain.model.Bookmark
import com.vegamstories.app.domain.repository.LibraryRepository
import javax.inject.Inject

class RemoveBookmarksUseCase @Inject constructor(
    private val repository: LibraryRepository,
) {
    suspend operator fun invoke(bookmarks: List<Bookmark>) = repository.removeBookmarks(bookmarks)
}
