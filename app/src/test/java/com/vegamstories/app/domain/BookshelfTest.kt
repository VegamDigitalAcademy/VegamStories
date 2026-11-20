package com.vegamstories.app.domain

import com.vegamstories.app.domain.model.Category
import com.vegamstories.app.domain.model.ReadingProgress
import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.domain.model.buildBookshelf
import com.vegamstories.app.testutil.testStory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bookshelf: finished stories grouped into a shelf per series, with a sticker for a full series. */
class BookshelfTest {

    private val categories = listOf(Category("moral", "Moral Stories", 0), Category("tenali", "Tenali Rama", 1))

    private fun story(category: String, episode: Int, finished: Boolean? = null) = testStory(
        id = "$category-$episode",
        publishedAt = episode.toLong(),
    ).copy(
        categoryId = category,
        episodeNumber = episode,
        progress = finished?.let { ReadingProgress("$category-$episode", lastPageIndex = 3, totalPages = 4, isCompleted = it, updatedAt = 0L) },
    )

    private val catalogue = listOf(story("moral", 1), story("moral", 2), story("moral", 3), story("tenali", 1), story("tenali", 2))

    @Test
    fun `only finished stories go on the shelves, a shelf per series in Home's order`() {
        val shelves = buildBookshelf(
            finished = listOf(story("tenali", 2, finished = true), story("moral", 3, finished = true), story("moral", 1, finished = false)),
            catalogue = catalogue,
            categories = categories,
        )
        assertEquals(listOf("moral", "tenali"), shelves.map { it.categoryId })
        assertEquals(listOf("moral-3"), shelves[0].finished.map { it.id }) // the half-read one isn't there
        assertEquals("Moral Stories", shelves[0].name)
        assertEquals(1, shelves[0].readCount)
        assertEquals(3, shelves[0].total)
    }

    @Test
    fun `finishing every episode of a series earns its sticker`() {
        val shelves = buildBookshelf(
            finished = listOf(story("tenali", 2, true), story("tenali", 1, true), story("moral", 1, true)),
            catalogue = catalogue,
            categories = categories,
        )
        val tenali = shelves.first { it.categoryId == "tenali" }
        assertTrue(tenali.isComplete)
        assertEquals(listOf(1, 2), tenali.finished.map { it.episodeNumber }) // in episode order
        assertFalse(shelves.first { it.categoryId == "moral" }.isComplete)
    }

    @Test
    fun `nothing finished, no shelves`() {
        assertTrue(buildBookshelf(listOf(story("moral", 1, false)), catalogue, categories).isEmpty())
    }
}
