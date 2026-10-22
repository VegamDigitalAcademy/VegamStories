package com.vegamstories.app.data.remote.datasource

import com.vegamstories.app.data.remote.api.StoryApi
import com.vegamstories.app.data.remote.dto.SetStoryLikeRequest
import javax.inject.Inject

/** Calls the set_story_like() Postgres function (supabase/migrations/003_story_likes.sql). */
class NetworkLikeRemoteDataSource @Inject constructor(
    private val api: StoryApi,
) : LikeRemoteDataSource {

    override suspend fun setLiked(storyId: String, deviceId: String, liked: Boolean): Int =
        api.setStoryLike(SetStoryLikeRequest(storyId = storyId, deviceId = deviceId, liked = liked))
}
