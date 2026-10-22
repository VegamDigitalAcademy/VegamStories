package com.vegamstories.app.data.local.entity

import androidx.room.Embedded

/**
 * A story in one language version, joined with its category name and the user's state for it.
 * See STORY_WITH_STATE_SELECT for how the language is chosen.
 */
data class StoryWithStateRow(
    @Embedded val story: StoryEntity,
    /** The language of the version below. */
    val language: String,
    val title: String,
    val summary: String,
    val coverImageUrl: String,
    val pageCount: Int,
    /** This version has the story as text. */
    val hasText: Boolean,
    val videoTitle: String?,
    val videoDescription: String?,
    val videoThumbnailUrl: String?,
    val videoUrl: String?,
    val categoryName: String?,
    val isFavorite: Boolean,
    val isDownloaded: Boolean,
    val progressLastPageIndex: Int?,
    val progressTotalPages: Int?,
    val progressIsCompleted: Boolean?,
    val progressUpdatedAt: Long?,
)
