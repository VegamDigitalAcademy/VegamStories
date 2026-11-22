package com.vegamstories.app.presentation

import com.vegamstories.app.presentation.reader.ComicSpreads
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Two comic pages side by side: which pages each spread holds, and where reading carries on. */
class ComicSpreadsTest {

    @Test
    fun `pages pair up, and an odd last page is on its own`() {
        val spreads = ComicSpreads(comicPageCount = 5)
        assertEquals(3, spreads.spreadCount)
        assertEquals(0..1, spreads.pagesOn(0))
        assertEquals(2..3, spreads.pagesOn(1))
        assertEquals(4..4, spreads.pagesOn(2))
        assertTrue("the like page holds no comic page", spreads.pagesOn(spreads.likeSpread).isEmpty())
        assertEquals("spreads + the like page", 4, spreads.pagerPageCount)
    }

    @Test
    fun `an even number of pages fills every spread`() {
        val spreads = ComicSpreads(comicPageCount = 4)
        assertEquals(2, spreads.spreadCount)
        assertEquals(2..3, spreads.pagesOn(1))
        assertEquals(3, spreads.pagerPageCount)
    }

    @Test
    fun `a page opens on its spread`() {
        val spreads = ComicSpreads(comicPageCount = 5)
        assertEquals(0, spreads.spreadOf(0))
        assertEquals(0, spreads.spreadOf(1))
        assertEquals(1, spreads.spreadOf(2))
        assertEquals(2, spreads.spreadOf(4))
        assertEquals("the like page", spreads.likeSpread, spreads.spreadOf(5))
    }

    @Test
    fun `reading a spread reaches its last page, and the like page finishes the story`() {
        val spreads = ComicSpreads(comicPageCount = 5)
        assertEquals(1, spreads.readTo(0))
        assertEquals(3, spreads.readTo(1))
        assertEquals(4, spreads.readTo(2))
        assertEquals(5, spreads.readTo(spreads.likeSpread))
    }

    @Test
    fun `turning the phone back keeps the place`() {
        val spreads = ComicSpreads(comicPageCount = 8)
        for (page in 0..8) {
            assertEquals("page $page", spreads.spreadOf(page), spreads.spreadOf(spreads.readTo(spreads.spreadOf(page))))
        }
    }
}
