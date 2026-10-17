package com.vegam.myapplication.data.remote.datasource

import com.vegam.myapplication.data.remote.dto.FeedResponseDto

/**
 * Where stories come from. Two implementations:
 *  - [FakeStoryRemoteDataSource]: dummy images + dummy text (used today)
 *  - [NetworkStoryRemoteDataSource]: the real server via Retrofit
 * Switch between them in [com.vegam.myapplication.di.DataModule].
 */
interface StoryRemoteDataSource {
    suspend fun fetchFeed(): FeedResponseDto
}
