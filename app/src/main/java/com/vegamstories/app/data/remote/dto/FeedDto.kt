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
    /** Languages stories can be in. Empty if the server couldn't send them (the saved list is kept). */
    @SerialName("languages") val languages: List<LanguageDto> = emptyList(),
)

/** A story language, managed in the admin site: e.g. code "or", name "Odia", native name "ଓଡ଼ିଆ". */
@Serializable
data class LanguageDto(
    @SerialName("code") val code: String,
    @SerialName("name") val name: String,
    @SerialName("native_name") val nativeName: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

/** A category: the id and order are shared; [names] maps language code → name ("te" → "నీతి కథలు"). */
@Serializable
data class CategoryDto(
    @SerialName("id") val id: String,
    @SerialName("names") val names: Map<String, String>,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

/** A story: what all languages share, and one [StoryTranslationDto] per language it's in. */
@Serializable
data class StoryDto(
    @SerialName("id") val id: String,
    @SerialName("category_id") val categoryId: String,
    @SerialName("episode") val episode: Int,
    @SerialName("published_at") val publishedAt: Long,
    @SerialName("like_count") val likeCount: Int = 0,
    @SerialName("translations") val translations: List<StoryTranslationDto> = emptyList(),
)

/** One language version of a story. It needs at least one of [pages], [text] or [video]. */
@Serializable
data class StoryTranslationDto(
    /** A code from the languages list, e.g. "te", "en", "or". */
    @SerialName("language") val language: String,
    @SerialName("title") val title: String,
    @SerialName("summary") val summary: String = "",
    /** Optional: without it the app shows the first comic page, or a title card. */
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("pages") val pages: List<PageDto> = emptyList(),
    /** The story as plain text (paragraphs separated by a blank line); null = no text version. */
    @SerialName("text") val text: String? = null,
    @SerialName("video") val video: VideoDto? = null,
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
