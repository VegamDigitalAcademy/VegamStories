package com.vegamstories.app.data

import com.vegamstories.app.data.local.entity.FavoriteEntity
import com.vegamstories.app.data.local.entity.LikeEntity
import com.vegamstories.app.data.local.entity.PendingLikeEntity
import com.vegamstories.app.testutil.FakeLibraryDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LibraryDao.toggleLike / confirmLike: the heart's local logic, before anything reaches the server;
 * and toggleFavorite, the star, which stays on the phone.
 */
class LikeLogicTest {

    @Test
    fun `liking saves the like, adds one like and queues it, and doesn't touch Favorites`() = runTest {
        val dao = FakeLibraryDao(likeCounts = mapOf("moral-1" to 41))

        val liked = dao.toggleLike("moral-1", likedAt = 100)

        assertTrue(liked)
        assertEquals(LikeEntity("moral-1", 100), dao.likes["moral-1"])
        assertTrue(dao.favorites.isEmpty())
        assertEquals(42, dao.likeCounts["moral-1"])
        assertEquals(listOf(PendingLikeEntity("moral-1", liked = true)), dao.getPendingLikes())
    }

    @Test
    fun `unliking removes the like, takes one like off and replaces the queued like`() = runTest {
        val dao = FakeLibraryDao(likeCounts = mapOf("moral-1" to 41))
        dao.toggleLike("moral-1", likedAt = 100)

        val liked = dao.toggleLike("moral-1", likedAt = 200)

        assertFalse(liked)
        assertFalse("moral-1" in dao.likes)
        assertEquals(41, dao.likeCounts["moral-1"])
        // Only the latest decision is sent: one row per story.
        assertEquals(listOf(PendingLikeEntity("moral-1", liked = false)), dao.getPendingLikes())
    }

    @Test
    fun `the like count never goes below zero`() = runTest {
        val dao = FakeLibraryDao(likeCounts = mapOf("moral-1" to 0))
        dao.upsertLike(LikeEntity("moral-1", likedAt = 1)) // liked before the count caught up

        dao.toggleLike("moral-1", likedAt = 2)

        assertEquals(0, dao.likeCounts["moral-1"])
    }

    @Test
    fun `a favorite stays on the phone, with no like, no count change and nothing sent`() = runTest {
        val dao = FakeLibraryDao(likeCounts = mapOf("moral-1" to 41))

        assertTrue(dao.toggleFavorite("moral-1", addedAt = 100))

        assertEquals(FavoriteEntity("moral-1", 100), dao.favorites["moral-1"])
        assertTrue(dao.likes.isEmpty())
        assertEquals(41, dao.likeCounts["moral-1"])
        assertTrue(dao.getPendingLikes().isEmpty())

        assertFalse(dao.toggleFavorite("moral-1", addedAt = 200))
        assertTrue(dao.favorites.isEmpty())
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
