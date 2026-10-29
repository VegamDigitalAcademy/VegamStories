package com.vegamstories.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Rows exactly as Supabase returns them (one class per table, column names as-is), also in the
 * catalogue in R2 ([SupabaseCatalogueDto]). NetworkStoryRemoteDataSource converts these into the
 * app's [FeedResponseDto].
 * See supabase/migrations/006_story_translations.sql for the per-language tables.
 */
@Serializable
data class SupabaseStoryRow(
    @SerialName("id") val id: String,
    @SerialName("category_id") val categoryId: String,
    @SerialName("episode") val episode: Int,
    /** ISO-8601 timestamp, e.g. "2026-10-20T18:22:07.123456+00:00". */
    @SerialName("published_at") val publishedAt: String,
    /**
     * Kept up to date on the server by set_story_like() (supabase/migrations/003_story_likes.sql).
     * Not in the catalogue in R2: the app reads the counts on their own.
     */
    @SerialName("like_count") val likeCount: Int? = null,
    /** Embedded: one row per language version. */
    @SerialName("story_translations") val translations: List<SupabaseTranslationRow> = emptyList(),
    /** Embedded: comic pages of every language version. */
    @SerialName("story_pages") val pages: List<SupabasePageRow> = emptyList(),
)

/** One language version of a story: title, cover, text and video in that language. */
@Serializable
data class SupabaseTranslationRow(
    /** A code from the languages table, e.g. "te", "en", "or". */
    @SerialName("language") val language: String,
    @SerialName("title") val title: String,
    @SerialName("summary") val summary: String = "",
    /** Optional (supabase/migrations/007). */
    @SerialName("cover_url") val coverUrl: String? = null,
    /** The story as plain text (paragraphs separated by a blank line); null = no text version. */
    @SerialName("body") val body: String? = null,
    @SerialName("video_title") val videoTitle: String? = null,
    @SerialName("video_description") val videoDescription: String? = null,
    @SerialName("video_thumbnail_url") val videoThumbnailUrl: String? = null,
    @SerialName("video_url") val videoUrl: String? = null,
)

@Serializable
data class SupabasePageRow(
    @SerialName("language") val language: String,
    @SerialName("page_index") val pageIndex: Int,
    /** The whole page as one image, e.g. https://media.vegamstories.com/moral-1/te/page-0-ab12cd.webp */
    @SerialName("image_url") val imageUrl: String,
)

/** A category: the id and order are shared, the name is per language. */
@Serializable
data class SupabaseCategoryRow(
    @SerialName("id") val id: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("category_translations") val names: List<SupabaseCategoryName> = emptyList(),
)

@Serializable
data class SupabaseCategoryName(
    @SerialName("language") val language: String,
    @SerialName("name") val name: String,
)

/** Arguments of the set_story_like() function, called as POST /rpc/set_story_like. */
@Serializable
data class SetStoryLikeRequest(
    @SerialName("p_story_id") val storyId: String,
    /** Random UUID made once per install (see DeviceIdProvider). */
    @SerialName("p_device_id") val deviceId: String,
    @SerialName("p_liked") val liked: Boolean,
)

/** A story's like count, read on its own with every refresh (the catalogue in R2 has none). */
@Serializable
data class StoryLikeCountRow(
    @SerialName("id") val id: String,
    @SerialName("like_count") val likeCount: Int,
)

/**
 * The catalogue in R2: the published stories, the categories and the languages, as Supabase's
 * own rows (the admin site saves Supabase's responses as they are; see vegam-admin publishCatalogue).
 */
@Serializable
data class SupabaseCatalogueDto(
    @SerialName("categories") val categories: List<SupabaseCategoryRow>,
    @SerialName("stories") val stories: List<SupabaseStoryRow>,
    @SerialName("languages") val languages: List<LanguageDto> = emptyList(),
)
