package com.vegamstories.app.data.remote.datasource

import com.vegamstories.app.data.remote.dto.FeedResponseDto

/**
 * Where stories come from. Two implementations:
 *  - [FakeStoryRemoteDataSource]: dummy images + dummy text (no server set up)
 *  - [NetworkStoryRemoteDataSource]: the catalogue in R2, like counts from Supabase
 * Switch between them in [com.vegamstories.app.di.DataModule].
 */
interface StoryRemoteDataSource {
    /**
     * The story catalogue, unless the phone has its current version already. [savedVersion] is the
     * version the stories on the phone came from; null when none are saved.
     */
    suspend fun fetchCatalogue(savedVersion: String?): CatalogueUpdate

    /** Every published story's like count, by story id: small, as likes change all the time and stories rarely. */
    suspend fun fetchLikeCounts(): Map<String, Int>
}

/** What a catalogue check found. */
sealed interface CatalogueUpdate {
    /** The phone has the current catalogue: nothing was downloaded. */
    data object Unchanged : CatalogueUpdate

    /** A catalogue the phone doesn't have, and its [version]: null when not known, so the next check downloads again. */
    data class Changed(val feed: FeedResponseDto, val version: String?) : CatalogueUpdate
}
