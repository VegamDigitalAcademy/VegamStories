package com.vegamstories.app.data.remote.datasource

import com.vegamstories.app.core.logging.AppLogger
import com.vegamstories.app.data.remote.api.StoryApi
import com.vegamstories.app.data.remote.dto.SetStoryLikeRequest
import javax.inject.Inject

private const val TAG = "NetworkLikeRemoteDataSource"

/** Calls the set_story_like() Postgres function (supabase/migrations/003_story_likes.sql). */
class NetworkLikeRemoteDataSource @Inject constructor(
    private val api: StoryApi,
) : LikeRemoteDataSource {

    private val log = AppLogger(TAG)

    override suspend fun setLiked(storyId: String, deviceId: String, liked: Boolean): Int {
        val request = SetStoryLikeRequest(storyId = storyId, deviceId = deviceId, liked = liked)
        log.d("$TAG: set_story_like", data = request)
        return api.setStoryLike(request).also { log.d("$TAG: set_story_like → like count $it") }
    }
}
