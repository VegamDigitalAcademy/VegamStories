package com.vegam.myapplication.presentation.home

import com.vegam.myapplication.domain.model.Category
import com.vegam.myapplication.domain.model.Story

data class HomeUiState(
    val categories: List<Category> = emptyList(),
    /** null = "ALL" tab. */
    val selectedCategoryId: String? = null,
    val stories: List<Story> = emptyList(),
    val continueReading: Story? = null,
    val isRefreshing: Boolean = false,
    /** True until the database has emitted at least once. */
    val isLoading: Boolean = true,
) {
    val selectedCategory: Category? get() = categories.firstOrNull { it.id == selectedCategoryId }
}

/** One-off UI events (snackbars) that must not be replayed on rotation. */
sealed interface HomeEvent {
    data class FavoriteChanged(val isFavorite: Boolean) : HomeEvent
    data object RefreshFailed : HomeEvent
}
