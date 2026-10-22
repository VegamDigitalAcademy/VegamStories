package com.vegamstories.app.data.remote.dto

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
    /** Languages story texts can be in. Empty if the server couldn't send them (texts still show). */
    @SerialName("languages") val languages: List<LanguageDto> = emptyList(),
)

/** A story text language, managed in the admin site: e.g. code "or", name "Odia", native name "ଓଡ଼ିଆ". */
@Serializable
data class LanguageDto(
    @SerialName("code") val code: String,
    @SerialName("name") val name: String,
    @SerialName("native_name") val nativeName: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
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
    @SerialName("like_count") val likeCount: Int = 0,
    /** The story as text, one entry per language. Empty if it has no text version. */
    @SerialName("texts") val texts: List<TextDto> = emptyList(),
)

/** The whole story as plain text in one language ([language] is a code from the languages list). */
@Serializable
data class TextDto(
    @SerialName("language") val language: String,
    @SerialName("body") val body: String,
)

/** One finished comic page image. */
@Serializable
data class PageDto(
    @SerialName("image_url") val imageUrl: String,
)

@Serializable
data class VideoDto(
    @SerialName("title") val title: String,
    @SerialName("description") val description: String = "",
    @SerialName("thumbnail_url") val thumbnailUrl: String,
    @SerialName("url") val url: String,
)
