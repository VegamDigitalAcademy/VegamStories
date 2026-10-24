package com.vegamstories.app.presentation.home

import com.vegamstories.app.domain.model.Category
import com.vegamstories.app.domain.model.ContentLanguages
import com.vegamstories.app.domain.model.Story

data class HomeUiState(
    val categories: List<Category> = emptyList(),
    /** null = "ALL" tab. */
    val selectedCategoryId: String? = null,
    val stories: List<Story> = emptyList(),
    val continueReading: Story? = null,
    val isRefreshing: Boolean = false,
    /** True until the database has emitted at least once. */
    val isLoading: Boolean = true,
    /** Languages stories are available in, and the one Home shows (the language button). */
    val contentLanguages: ContentLanguages? = null,
) {
    val selectedCategory: Category? get() = categories.firstOrNull { it.id == selectedCategoryId }
}

/** One-off UI events (snackbars) that must not be replayed on rotation. */
sealed interface HomeEvent {
    data object RefreshFailed : HomeEvent
}
