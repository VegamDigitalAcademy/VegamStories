package com.vegamstories.app.data.repository

import com.vegamstories.app.data.local.dao.LibraryDao
import com.vegamstories.app.data.remote.datasource.LikeRemoteDataSource
import com.vegamstories.app.data.settings.DeviceIdProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends queued likes/unlikes (the pending_likes table) to the server. The heart changes
 * locally right away; this runs afterwards, and again on every refresh until it succeeds.
 */
@Singleton
class LikeSyncer @Inject constructor(
    private val libraryDao: LibraryDao,
    private val remote: LikeRemoteDataSource,
    private val deviceIdProvider: DeviceIdProvider,
) {
    // One sync at a time, so a quick like → unlike reaches the server in order.
    private val mutex = Mutex()

    suspend fun syncPending() = mutex.withLock {
        val pending = libraryDao.getPendingLikes()
        if (pending.isEmpty()) return@withLock
        val deviceId = deviceIdProvider.get()
        for (like in pending) {
            try {
                val serverCount = remote.setLiked(like.storyId, deviceId, like.liked)
                libraryDao.confirmLike(like.storyId, like.liked, serverCount)
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                if (e.code() == HTTP_NOT_FOUND) {
                    // Story deleted or unpublished on the server: nothing left to like.
                    libraryDao.deletePendingLike(like.storyId, like.liked)
                } else {
                    Timber.w(e, "Like sync failed for %s, will retry", like.storyId)
                    return@withLock
                }
            } catch (e: Exception) {
                // Usually offline. Keep the rest queued for the next try.
                Timber.w(e, "Like sync failed for %s, will retry", like.storyId)
                return@withLock
            }
        }
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}
