package com.vegamstories.app.presentation.common

import com.vegamstories.app.domain.model.Story

/** Shared state for simple story lists (Favorites, Downloads, History, Search). */
data class StoryListUiState(
    val isLoading: Boolean = true,
    val stories: List<Story> = emptyList(),
)
