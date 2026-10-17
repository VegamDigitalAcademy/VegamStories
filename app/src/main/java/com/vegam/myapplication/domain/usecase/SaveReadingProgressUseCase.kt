package com.vegam.myapplication.domain.usecase

import com.vegam.myapplication.domain.repository.LibraryRepository
import javax.inject.Inject

class SaveReadingProgressUseCase @Inject constructor(
    private val repository: LibraryRepository,
) {
    /**
     * [pageIndex] may point past the last comic page (e.g. the video page);
     * it is clamped so progress never exceeds the story length.
     */
    suspend operator fun invoke(storyId: String, pageIndex: Int, totalPages: Int) {
        if (totalPages <= 0) return
        repository.saveProgress(
            storyId = storyId,
            pageIndex = pageIndex.coerceIn(0, totalPages - 1),
            totalPages = totalPages,
        )
    }
}
