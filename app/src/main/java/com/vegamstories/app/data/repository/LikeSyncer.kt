package com.vegamstories.app.data.repository

import com.vegamstories.app.core.logging.AppLogger
import com.vegamstories.app.data.local.dao.LibraryDao
import com.vegamstories.app.data.remote.datasource.LikeRemoteDataSource
import com.vegamstories.app.data.settings.DeviceIdProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LikeSyncer"

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
    private val log = AppLogger(TAG)

    // One sync at a time, so a quick like → unlike reaches the server in order.
    private val mutex = Mutex()

    suspend fun syncPending() = mutex.withLock {
        val pending = libraryDao.getPendingLikes()
        if (pending.isEmpty()) {
            log.v("$TAG: No likes waiting to be sent")
            return@withLock
        }
        log.d("$TAG: Sending ${pending.size} like(s) to the server", data = pending)
        val deviceId = deviceIdProvider.get()
        for (like in pending) {
            try {
                val serverCount = remote.setLiked(like.storyId, deviceId, like.liked)
                libraryDao.confirmLike(like.storyId, like.liked, serverCount)
                log.d("$TAG: ${if (like.liked) "Like" else "Unlike"} sent for ${like.storyId}: the server counts $serverCount")
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                if (e.code() == HTTP_NOT_FOUND) {
                    // Story deleted or unpublished on the server: nothing left to like.
                    libraryDao.deletePendingLike(like.storyId, like.liked)
                    log.w("$TAG: Like dropped: ${like.storyId} is no longer on the server")
                } else {
                    log.w("$TAG: Like for ${like.storyId} not sent (HTTP ${e.code()}); will retry", e)
                    return@withLock
                }
            } catch (e: Exception) {
                // Usually offline. Keep the rest queued for the next try.
                log.w("$TAG: Like for ${like.storyId} not sent (offline?); will retry", e)
                return@withLock
            }
        }
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}
