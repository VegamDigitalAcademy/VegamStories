package com.vegamstories.app.data.remote.datasource

/**
 * Sends this device's like/unlike to the server. Same two implementations as
 * [StoryRemoteDataSource]: [FakeLikeRemoteDataSource] and [NetworkLikeRemoteDataSource].
 */
interface LikeRemoteDataSource {
    /**
     * Idempotent: liking an already-liked story changes nothing, so a failed call can be retried.
     * @return the story's total like count after the change.
     */
    suspend fun setLiked(storyId: String, deviceId: String, liked: Boolean): Int
}
