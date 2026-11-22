package com.vegamstories.app.domain.usecase

import com.vegamstories.app.domain.model.BookmarkItem
import com.vegamstories.app.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Every bookmarked page, newest first, for the Bookmarks screen. */
class GetBookmarksUseCase @Inject constructor(
    private val repository: LibraryRepository,
) {
    operator fun invoke(): Flow<List<BookmarkItem>> = repository.observeBookmarks()
}
