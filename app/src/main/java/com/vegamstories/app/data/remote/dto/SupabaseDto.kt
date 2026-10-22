package com.vegamstories.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Rows exactly as Supabase returns them (one class per table, column names as-is).
 * NetworkStoryRemoteDataSource converts these into the app's [FeedResponseDto].
 */
@Serializable
data class SupabaseStoryRow(
    @SerialName("id") val id: String,
    @SerialName("category_id") val categoryId: String,
    @SerialName("episode") val episode: Int,
    @SerialName("title") val title: String,
    @SerialName("summary") val summary: String = "",
    @SerialName("cover_url") val coverUrl: String,
    /** ISO-8601 timestamp, e.g. "2026-10-20T18:22:07.123456+00:00". */
    @SerialName("published_at") val publishedAt: String,
    @SerialName("video_title") val videoTitle: String? = null,
    @SerialName("video_description") val videoDescription: String? = null,
    @SerialName("video_thumbnail_url") val videoThumbnailUrl: String? = null,
    @SerialName("video_url") val videoUrl: String? = null,
    /** Kept up to date on the server by set_story_like() (supabase/migrations/003_story_likes.sql). */
    @SerialName("like_count") val likeCount: Int = 0,
    /** Embedded from the story_pages table via select=*,story_pages(...). */
    @SerialName("story_pages") val pages: List<SupabasePageRow> = emptyList(),
    /** Embedded from the story_texts table: the story as text, per language (supabase/migrations/004). */
    @SerialName("story_texts") val texts: List<SupabaseTextRow> = emptyList(),
)

@Serializable
data class SupabasePageRow(
    @SerialName("page_index") val pageIndex: Int,
    /** The whole page as one image, e.g. https://media.vegamstories.com/moral-1/page-0-ab12cd.webp */
    @SerialName("image_url") val imageUrl: String,
)

/** The whole story as plain text in one language. Paragraphs are separated by a blank line. */
@Serializable
data class SupabaseTextRow(
    /** A code from the languages table, e.g. "te", "en", "or". */
    @SerialName("language") val language: String,
    @SerialName("body") val body: String,
)

/** Arguments of the set_story_like() function, called as POST /rpc/set_story_like. */
@Serializable
data class SetStoryLikeRequest(
    @SerialName("p_story_id") val storyId: String,
    /** Random UUID made once per install (see DeviceIdProvider). */
    @SerialName("p_device_id") val deviceId: String,
    @SerialName("p_liked") val liked: Boolean,
)
