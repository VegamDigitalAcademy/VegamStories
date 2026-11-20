package com.vegamstories.app.presentation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.vegamstories.app.presentation.reader.BookColors
import com.vegamstories.app.presentation.reader.BookLine
import com.vegamstories.app.presentation.reader.TextBook
import com.vegamstories.app.presentation.reader.TextSlice
import com.vegamstories.app.presentation.reader.paginate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Read tab's pages: text split to fit the screen, a paragraph running on where it must. */
class TextBookTest {

    /** A paragraph of [count] lines, 10 px each, 5 characters per line. */
    private fun paragraph(count: Int) = List(count) { i -> BookLine(start = i * 5, end = i * 5 + 5, top = i * 10f, bottom = i * 10f + 10f) }

    @Test
    fun `short text fits on one page`() {
        val pages = paginate(listOf(paragraph(2), paragraph(3)), pageHeight = 100f, firstPageTop = 0f, gap = 5f)
        assertEquals(listOf(listOf(TextSlice(0, 0, 10), TextSlice(1, 0, 15))), pages)
    }

    @Test
    fun `a paragraph that doesn't fit runs on to the next page from the line where it broke`() {
        // 4 lines fit under the 55 px title; the other 3 go over.
        val pages = paginate(listOf(paragraph(7)), pageHeight = 100f, firstPageTop = 55f, gap = 5f)
        assertEquals(listOf(listOf(TextSlice(0, 0, 20)), listOf(TextSlice(0, 20, 35))), pages)
    }

    @Test
    fun `the gap between paragraphs counts, and a page never holds more than fits`() {
        val lines = listOf(paragraph(5), paragraph(5), paragraph(5))
        val pages = paginate(lines, pageHeight = 100f, firstPageTop = 0f, gap = 5f)
        // 50 + 5 + 45: the second paragraph's last line doesn't fit.
        assertEquals(listOf(TextSlice(0, 0, 25), TextSlice(1, 0, 20)), pages[0])
        assertEquals(TextSlice(1, 20, 25), pages[1].first())
        // Every character is on exactly one page, in order.
        val all = pages.flatten()
        lines.indices.forEach { p ->
            val parts = all.filter { it.paragraph == p }
            assertEquals(0, parts.first().start)
            assertEquals(25, parts.last().end)
            parts.zipWithNext { a, b -> assertEquals(a.end, b.start) }
        }
    }

    @Test
    fun `a page always takes at least one line, so huge text still ends`() {
        val pages = paginate(listOf(paragraph(3)), pageHeight = 5f, firstPageTop = 0f, gap = 0f)
        assertEquals(3, pages.size)
        assertTrue(pages.all { it.size == 1 })
    }

    @Test
    fun `a spot in the text finds its page, even mid-paragraph`() {
        val pages = paginate(listOf(paragraph(5), paragraph(5), paragraph(5)), pageHeight = 100f, firstPageTop = 0f, gap = 5f)
        val book = TextBook(
            title = "",
            paragraphs = List(3) { "x".repeat(25) },
            pages = pages,
            pageHeight = 100.dp,
            body = TextStyle(),
            titleStyle = TextStyle(),
            gap = 1.dp,
            colors = BookColors(Color.White, Color.Black, Color.Gray, Color.Gray),
        )
        assertEquals(0, book.pageAt(paragraph = 0))
        assertEquals(0, book.pageAt(paragraph = 1)) // starts on page 1…
        assertEquals(1, book.pageAt(paragraph = 1, offset = 20)) // …its last line is on page 2
        assertEquals(1, book.pageAt(paragraph = 2))
        // Progress counts the last paragraph on the page.
        assertEquals(1, book.lastParagraphOn(0))
        assertEquals(2, book.lastParagraphOn(1))
    }

    @Test
    fun `no text is still one page, for the title`() {
        assertEquals(listOf(emptyList<TextSlice>()), paginate(emptyList(), pageHeight = 100f, firstPageTop = 40f, gap = 5f))
    }
}
