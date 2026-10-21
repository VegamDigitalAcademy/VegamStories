package com.vegam.myapplication.data

import com.vegam.myapplication.data.local.entity.FavoriteEntity
import com.vegam.myapplication.data.local.entity.PendingLikeEntity
import com.vegam.myapplication.testutil.FakeLibraryDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** LibraryDao.toggleFavorite / confirmLike: the heart's local logic, before anything reaches the server. */
class LikeLogicTest {

    @Test
    fun `liking saves the favorite, adds one like and queues it`() = runTest {
        val dao = FakeLibraryDao(likeCounts = mapOf("moral-1" to 41))

        val liked = dao.toggleFavorite("moral-1", addedAt = 100)

        assertTrue(liked)
        assertEquals(FavoriteEntity("moral-1", 100), dao.favorites["moral-1"])
        assertEquals(42, dao.likeCounts["moral-1"])
        assertEquals(listOf(PendingLikeEntity("moral-1", liked = true)), dao.getPendingLikes())
    }

    @Test
    fun `unliking removes the favorite, takes one like off and replaces the queued like`() = runTest {
        val dao = FakeLibraryDao(likeCounts = mapOf("moral-1" to 41))
        dao.toggleFavorite("moral-1", addedAt = 100)

        val liked = dao.toggleFavorite("moral-1", addedAt = 200)

        assertFalse(liked)
        assertFalse("moral-1" in dao.favorites)
        assertEquals(41, dao.likeCounts["moral-1"])
        // Only the latest decision is sent: one row per story.
        assertEquals(listOf(PendingLikeEntity("moral-1", liked = false)), dao.getPendingLikes())
    }

    @Test
    fun `the like count never goes below zero`() = runTest {
        val dao = FakeLibraryDao(likeCounts = mapOf("moral-1" to 0))
        dao.upsertFavorite(FavoriteEntity("moral-1", addedAt = 1)) // liked before the count caught up

        dao.toggleFavorite("moral-1", addedAt = 2)

        assertEquals(0, dao.likeCounts["moral-1"])
    }

    @Test
    fun `a confirmed like takes the server's count and leaves the queue`() = runTest {
        val dao = FakeLibraryDao(likeCounts = mapOf("moral-1" to 42))
        dao.upsertPendingLike(PendingLikeEntity("moral-1", liked = true))

        dao.confirmLike("moral-1", liked = true, serverCount = 57)

        assertEquals(57, dao.likeCounts["moral-1"])
        assertTrue(dao.getPendingLikes().isEmpty())
    }

    @Test
    fun `a confirmation for an outdated tap changes nothing`() = runTest {
        val dao = FakeLibraryDao(likeCounts = mapOf("moral-1" to 41))
        // The user liked, then unliked while the like was on its way to the server.
        dao.upsertPendingLike(PendingLikeEntity("moral-1", liked = false))

        dao.confirmLike("moral-1", liked = true, serverCount = 57)

        assertEquals(41, dao.likeCounts["moral-1"])
        assertEquals(listOf(PendingLikeEntity("moral-1", liked = false)), dao.getPendingLikes())
    }
}
