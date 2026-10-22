package com.vegamstories.app.data.remote.api

import com.vegamstories.app.data.remote.dto.CategoryDto
import com.vegamstories.app.data.remote.dto.LanguageDto
import com.vegamstories.app.data.remote.dto.SetStoryLikeRequest
import com.vegamstories.app.data.remote.dto.SupabaseStoryRow
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Supabase REST API (PostgREST), base URL = https://<project>.supabase.co/rest/v1/
 * Auth headers are added by the OkHttp interceptor in NetworkModule.
 * Row Level Security on the server decides what is visible (published stories only).
 */
interface StoryApi {

    /** GET /categories?select=id,name,sort_order&order=sort_order.asc */
    @GET("categories")
    suspend fun getCategories(
        @Query("select") select: String = "id,name,sort_order",
        @Query("order") order: String = "sort_order.asc",
    ): List<CategoryDto>

    /** GET /languages: the languages story texts can be in, in display order (added in the admin site). */
    @GET("languages")
    suspend fun getLanguages(
        @Query("select") select: String = "code,name,native_name,sort_order",
        @Query("order") order: String = "sort_order.asc",
    ): List<LanguageDto>

    /** Published stories with their pages and texts embedded, newest first. */
    @GET("stories")
    suspend fun getStories(
        @Query("select") select: String = "*,story_pages(page_index,image_url),story_texts(language,body)",
        @Query("is_published") isPublished: String = "eq.true",
        @Query("order") order: String = "published_at.desc",
        @Query("story_pages.order") pagesOrder: String = "page_index.asc",
    ): List<SupabaseStoryRow>

    /** Likes or unlikes a story for this device. Returns the story's new like count, e.g. `42`. */
    @POST("rpc/set_story_like")
    suspend fun setStoryLike(@Body body: SetStoryLikeRequest): Int
}
