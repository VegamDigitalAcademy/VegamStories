package com.vegam.myapplication.data.remote.dto

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
    /** Embedded from the story_pages table via select=*,story_pages(...). */
    @SerialName("story_pages") val pages: List<SupabasePageRow> = emptyList(),
)

@Serializable
data class SupabasePageRow(
    @SerialName("page_index") val pageIndex: Int,
    /** The jsonb column: [{"image_url": "...", "caption": "...", "dialogues": ["..."]}] */
    @SerialName("panels") val panels: List<PanelDto> = emptyList(),
)
