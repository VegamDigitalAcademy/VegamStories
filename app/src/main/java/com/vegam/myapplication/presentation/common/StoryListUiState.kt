package com.vegam.myapplication.presentation.common

import com.vegam.myapplication.domain.model.Story

/** Shared state for simple story lists (Favorites, Downloads, History, Search). */
data class StoryListUiState(
    val isLoading: Boolean = true,
    val stories: List<Story> = emptyList(),
)
