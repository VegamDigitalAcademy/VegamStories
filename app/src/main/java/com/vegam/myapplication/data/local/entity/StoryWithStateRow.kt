package com.vegam.myapplication.data.local.entity

import androidx.room.Embedded

/** Result of joining a story with its category and the user's state for it. */
data class StoryWithStateRow(
    @Embedded val story: StoryEntity,
    val categoryName: String?,
    val isFavorite: Boolean,
    val isDownloaded: Boolean,
    val progressLastPageIndex: Int?,
    val progressTotalPages: Int?,
    val progressIsCompleted: Boolean?,
    val progressUpdatedAt: Long?,
)
