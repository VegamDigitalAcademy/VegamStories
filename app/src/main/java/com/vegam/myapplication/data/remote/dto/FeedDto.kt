package com.vegam.myapplication.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Shape of the JSON we expect from the server. When the real API is ready, adjust the
 * @SerialName values to match it — nothing outside the data layer has to change.
 */
@Serializable
data class FeedResponseDto(
    @SerialName("categories") val categories: List<CategoryDto>,
    @SerialName("stories") val stories: List<StoryDto>,
)

@Serializable
data class CategoryDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
data class StoryDto(
    @SerialName("id") val id: String,
    @SerialName("category_id") val categoryId: String,
    @SerialName("episode") val episode: Int,
    @SerialName("title") val title: String,
    @SerialName("summary") val summary: String = "",
    @SerialName("cover_url") val coverUrl: String,
    @SerialName("published_at") val publishedAt: Long,
    @SerialName("pages") val pages: List<PageDto> = emptyList(),
    @SerialName("video") val video: VideoDto? = null,
)

@Serializable
data class PageDto(
    @SerialName("panels") val panels: List<PanelDto>,
)

@Serializable
data class PanelDto(
    @SerialName("image_url") val imageUrl: String,
    @SerialName("caption") val caption: String? = null,
    @SerialName("dialogues") val dialogues: List<String> = emptyList(),
)

@Serializable
data class VideoDto(
    @SerialName("title") val title: String,
    @SerialName("description") val description: String = "",
    @SerialName("thumbnail_url") val thumbnailUrl: String,
    @SerialName("url") val url: String,
)
