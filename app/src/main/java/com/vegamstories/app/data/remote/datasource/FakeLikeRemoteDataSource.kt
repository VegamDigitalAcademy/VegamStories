package com.vegamstories.app.data.remote.datasource

import com.vegamstories.app.core.logging.AppLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FakeLikeRemoteDataSource"

/**
 * Pretends to be the server's likes: every story starts with a made-up count, and this
 * device's likes are kept in memory. [FakeStoryRemoteDataSource] reads [countFor] so the feed
 * and the like calls agree.
 */
@Singleton
class FakeLikeRemoteDataSource @Inject constructor() : LikeRemoteDataSource {

    private val mutex = Mutex()
    private val likedStoryIds = mutableSetOf<String>()

    private val log = AppLogger(TAG)

    override suspend fun setLiked(storyId: String, deviceId: String, liked: Boolean): Int {
        delay(NETWORK_DELAY_MS)
        return mutex.withLock {
            if (liked) likedStoryIds += storyId else likedStoryIds -= storyId
            countLocked(storyId)
        }.also { log.d("$TAG: Sample server: ${if (liked) "like" else "unlike"} $storyId → $it") }
    }

    suspend fun countFor(storyId: String): Int = mutex.withLock { countLocked(storyId) }

    private fun countLocked(storyId: String): Int =
        baseCount(storyId) + if (storyId in likedStoryIds) 1 else 0

    /** Stable per story, so counts don't jump around between refreshes. */
    private fun baseCount(storyId: String): Int = (storyId.hashCode() and Int.MAX_VALUE) % 2_000

    private companion object {
        const val NETWORK_DELAY_MS = 300L
    }
}
