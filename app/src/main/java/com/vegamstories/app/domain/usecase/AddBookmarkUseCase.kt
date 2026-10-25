package com.vegamstories.app.domain.usecase

import com.vegamstories.app.domain.model.Bookmark
import com.vegamstories.app.domain.repository.LibraryRepository
import javax.inject.Inject

class AddBookmarkUseCase @Inject constructor(
    private val repository: LibraryRepository,
) {
    suspend operator fun invoke(bookmark: Bookmark) = repository.addBookmark(bookmark)
}
