package com.vegamstories.app.data

import com.vegamstories.app.data.local.entity.PendingLikeEntity
import com.vegamstories.app.data.remote.datasource.LikeRemoteDataSource
import com.vegamstories.app.data.repository.LikeSyncer
import com.vegamstories.app.data.settings.DeviceIdProvider
import com.vegamstories.app.testutil.FakeLibraryDao
import com.vegamstories.app.testutil.InMemoryPreferencesDataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class LikeSyncerTest {

    private data class Call(val storyId: String, val deviceId: String, val liked: Boolean)

    /** Records calls; answers from [answers] per story (a count, or an exception to throw). */
    private class FakeLikeRemote(private val answers: Map<String, Any> = emptyMap()) : LikeRemoteDataSource {
        val calls = mutableListOf<Call>()
        var onCall: suspend (Call) -> Unit = {}

        override suspend fun setLiked(storyId: String, deviceId: String, liked: Boolean): Int {
            val call = Call(storyId, deviceId, liked)
            calls += call
            onCall(call)
            return when (val answer = answers[storyId] ?: 0) {
                is Int -> answer
                is Exception -> throw answer
                else -> error("bad answer $answer")
            }
        }
    }

    private val dataStore = InMemoryPreferencesDataStore()
    private val deviceIdProvider = DeviceIdProvider(dataStore)

    private fun httpError(code: Int) = HttpException(Response.error<Unit>(code, "".toResponseBody()))

    @Test
    fun `sends every queued like with this device's id and takes the server counts`() = runTest {
        val dao = FakeLibraryDao(likeCounts = mapOf("a" to 5, "b" to 3)).apply {
            upsertPendingLike(PendingLikeEntity("a", liked = true))
            upsertPendingLike(PendingLikeEntity("b", liked = false))
        }
        val remote = FakeLikeRemote(answers = mapOf("a" to 10, "b" to 2))

        LikeSyncer(dao, remote, deviceIdProvider).syncPending()

        val deviceId = deviceIdProvider.get()
        assertEquals(listOf(Call("a", deviceId, true), Call("b", deviceId, false)), remote.calls)
        assertEquals(mapOf("a" to 10, "b" to 2), dao.likeCounts)
        assertTrue(dao.getPendingLikes().isEmpty())
    }

    @Test
    fun `nothing queued means no network call and no device id is created`() = runTest {
        val remote = FakeLikeRemote()

        LikeSyncer(FakeLibraryDao(), remote, deviceIdProvider).syncPending()

        assertTrue(remote.calls.isEmpty())
        assertEquals(0, dataStore.writes)
    }

    @Test
    fun `a story deleted on the server (404) is dropped and the rest still sync`() = runTest {
        val dao = FakeLibraryDao(likeCounts = mapOf("gone" to 1, "b" to 3)).apply {
            upsertPendingLike(PendingLikeEntity("gone", liked = true))
            upsertPendingLike(PendingLikeEntity("b", liked = true))
        }
        val remote = FakeLikeRemote(answers = mapOf("gone" to httpError(404), "b" to 4))

        LikeSyncer(dao, remote, deviceIdProvider).syncPending()

        assertTrue(dao.getPendingLikes().isEmpty())
        assertEquals(1, dao.likeCounts["gone"]) // untouched
        assertEquals(4, dao.likeCounts["b"])
    }

    @Test
    fun `a server error keeps everything queued and stops`() = runTest {
        val dao = FakeLibraryDao().apply {
            upsertPendingLike(PendingLikeEntity("a", liked = true))
            upsertPendingLike(PendingLikeEntity("b", liked = true))
        }
        val remote = FakeLikeRemote(answers = mapOf("a" to httpError(500)))

        LikeSyncer(dao, remote, deviceIdProvider).syncPending()

        assertEquals(listOf("a"), remote.calls.map { it.storyId }) // stopped after the failure
        assertEquals(2, dao.getPendingLikes().size)
    }

    @Test
    fun `offline keeps everything queued for the next try`() = runTest {
        val dao = FakeLibraryDao().apply { upsertPendingLike(PendingLikeEntity("a", liked = true)) }
        val remote = FakeLikeRemote(answers = mapOf("a" to IOException("offline")))

        LikeSyncer(dao, remote, deviceIdProvider).syncPending()

        assertEquals(listOf(PendingLikeEntity("a", liked = true)), dao.getPendingLikes())
    }

    @Test
    fun `tapping again while a like is being sent keeps the newer tap queued`() = runTest {
        val dao = FakeLibraryDao(likeCounts = mapOf("a" to 41)).apply {
            upsertPendingLike(PendingLikeEntity("a", liked = true))
        }
        val remote = FakeLikeRemote(answers = mapOf("a" to 42)).apply {
            // The user unlikes while the like request is in flight.
            onCall = { dao.upsertPendingLike(PendingLikeEntity("a", liked = false)) }
        }

        LikeSyncer(dao, remote, deviceIdProvider).syncPending()

        assertEquals(41, dao.likeCounts["a"]) // the like's server count isn't applied
        assertEquals(listOf(PendingLikeEntity("a", liked = false)), dao.getPendingLikes())
    }

    @Test
    fun `two syncs at the same time send each like only once`() = runTest {
        val dao = FakeLibraryDao().apply { upsertPendingLike(PendingLikeEntity("a", liked = true)) }
        val remote = FakeLikeRemote(answers = mapOf("a" to 1)).apply { onCall = { delay(1_000) } }
        val syncer = LikeSyncer(dao, remote, deviceIdProvider)

        val first = launch { syncer.syncPending() }
        val second = launch { syncer.syncPending() }
        first.join()
        second.join()

        assertEquals(1, remote.calls.size)
    }

    @Test
    fun `the device id is made once and then reused`() = runTest {
        val first = deviceIdProvider.get()
        val second = DeviceIdProvider(dataStore).get()

        assertEquals(first, second)
        assertEquals(36, first.length) // a UUID
    }
}
