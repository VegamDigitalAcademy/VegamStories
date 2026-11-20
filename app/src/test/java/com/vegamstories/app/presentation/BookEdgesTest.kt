package com.vegamstories.app.presentation

import com.vegamstories.app.presentation.reader.BookEdges
import com.vegamstories.app.presentation.reader.bookEdges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The book's other pages at the open page's edges: still to come on the right, read on the left. */
class BookEdgesTest {

    @Test
    fun `the first page has every page still to come, the last has every page read`() {
        assertEquals(BookEdges(read = 0, toCome = 6), bookEdges(index = 0, pageCount = 10))
        assertEquals(BookEdges(read = 6, toCome = 0), bookEdges(index = 9, pageCount = 10))
    }

    @Test
    fun `reading moves the edges from the right to the left, the book staying as thick`() {
        val edges = (0 until 12).map { bookEdges(it, pageCount = 12) }
        edges.zipWithNext { a, b ->
            assertTrue(b.toCome <= a.toCome)
            assertTrue(b.read >= a.read)
        }
        assertTrue(edges.all { it.read + it.toCome == 6 })
        assertEquals(BookEdges(read = 3, toCome = 3), bookEdges(index = 5, pageCount = 11)) // the middle
    }

    @Test
    fun `a one-page story shows the pages under it`() {
        assertEquals(BookEdges(read = 0, toCome = 6), bookEdges(index = 0, pageCount = 1))
    }
}
