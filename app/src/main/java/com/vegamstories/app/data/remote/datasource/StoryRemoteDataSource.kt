package com.vegamstories.app.data.remote.datasource

import com.vegamstories.app.data.remote.dto.FeedResponseDto

/**
 * Where stories come from. Two implementations:
 *  - [FakeStoryRemoteDataSource]: dummy images + dummy text (used today)
 *  - [NetworkStoryRemoteDataSource]: the real server via Retrofit
 * Switch between them in [com.vegamstories.app.di.DataModule].
 */
interface StoryRemoteDataSource {
    suspend fun fetchFeed(): FeedResponseDto
}
