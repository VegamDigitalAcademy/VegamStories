package com.vegamstories.app.presentation

import com.vegamstories.app.presentation.reader.pageBarFill
import org.junit.Assert.assertEquals
import org.junit.Test

/** The page bars under the comic: which are full, empty, or filling with the auto-turn timer. */
class PageBarFillTest {

    // 4 comic pages, then the like page (index 4).
    private val likePage = 4

    private fun fill(index: Int, current: Int, timerPage: Int = current, progress: Float = 0.4f, autoTurn: Boolean = true) =
        pageBarFill(index, current, timerPage, progress, autoTurn, likePage)

    @Test
    fun `pages before the current one are full and pages after it empty`() {
        assertEquals(1f, fill(index = 0, current = 2))
        assertEquals(1f, fill(index = 1, current = 2))
        assertEquals(0f, fill(index = 3, current = 2))
        assertEquals(0f, fill(index = 4, current = 2))
    }

    @Test
    fun `the current page's bar follows its timer`() {
        assertEquals(0.4f, fill(index = 2, current = 2, progress = 0.4f))
        assertEquals(1f, fill(index = 2, current = 2, progress = 1f))
    }

    @Test
    fun `mid-turn the new page's bar starts empty, not full from the page being left`() {
        // The timer still belongs to page 1 (full) while the pager has passed halfway to page 2.
        assertEquals(0f, fill(index = 2, current = 2, timerPage = 1, progress = 1f))
        assertEquals(1f, fill(index = 1, current = 2, timerPage = 1, progress = 1f))
    }

    @Test
    fun `with auto-turn off, or on the like page, the current bar is simply full`() {
        assertEquals(1f, fill(index = 2, current = 2, progress = 0f, autoTurn = false))
        assertEquals(1f, fill(index = 4, current = 4, progress = 0f))
    }
}
