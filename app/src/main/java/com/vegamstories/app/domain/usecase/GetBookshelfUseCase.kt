package com.vegamstories.app.domain.usecase

import com.vegamstories.app.domain.model.SeriesShelf
import com.vegamstories.app.domain.model.buildBookshelf
import com.vegamstories.app.domain.repository.LibraryRepository
import com.vegamstories.app.domain.repository.StoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/** The reader's bookshelf: finished stories, a shelf per series (see [buildBookshelf]). */
class GetBookshelfUseCase @Inject constructor(
    private val library: LibraryRepository,
    private val stories: StoryRepository,
) {
    operator fun invoke(): Flow<List<SeriesShelf>> = combine(
        library.observeReadingHistory(),
        stories.observeStories(categoryId = null),
        stories.observeCategories(),
        ::buildBookshelf,
    )
}
