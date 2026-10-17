package com.vegam.myapplication.data.remote.datasource

import com.vegam.myapplication.data.remote.api.StoryApi
import com.vegam.myapplication.data.remote.dto.FeedResponseDto
import javax.inject.Inject

class NetworkStoryRemoteDataSource @Inject constructor(
    private val api: StoryApi,
) : StoryRemoteDataSource {
    override suspend fun fetchFeed(): FeedResponseDto = api.getFeed()
}
