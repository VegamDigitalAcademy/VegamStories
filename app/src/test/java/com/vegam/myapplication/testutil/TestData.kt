package com.vegam.myapplication.testutil

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.vegam.myapplication.domain.model.ReadingProgress
import com.vegam.myapplication.domain.model.Story
import com.vegam.myapplication.domain.model.StoryVideo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A story with sensible defaults; override only what a test cares about. */
fun testStory(
    id: String = "moral-1",
    publishedAt: Long = 0L,
    opened: Boolean = false,
    withVideo: Boolean = false,
) = Story(
    id = id,
    categoryId = "moral",
    categoryName = "Moral",
    episodeNumber = 1,
    title = "Title $id",
    summary = "",
    coverImageUrl = "https://example.com/$id.webp",
    pageCount = 3,
    publishedAt = publishedAt,
    video = if (withVideo) {
        StoryVideo(title = "Video", description = "", thumbnailUrl = "https://example.com/v.webp", videoUrl = "https://youtu.be/x")
    } else {
        null
    },
    isFavorite = false,
    likeCount = 0,
    isDownloaded = false,
    progress = if (opened) {
        ReadingProgress(storyId = id, lastPageIndex = 0, totalPages = 3, isCompleted = false, updatedAt = 0L)
    } else {
        null
    },
)

/** DataStore kept in memory, so classes built on DataStore can be tested without files or Android. */
class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    var writes = 0
        private set

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        writes++
        return updated
    }
}
