package com.vegam.myapplication.data.remote.api

import com.vegam.myapplication.data.remote.dto.FeedResponseDto
import retrofit2.http.GET

/** Retrofit contract for the Vegam Stories backend. */
interface StoryApi {
    @GET("v1/feed")
    suspend fun getFeed(): FeedResponseDto
}
