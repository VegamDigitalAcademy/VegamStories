package com.vegamstories.app.presentation

import com.vegamstories.app.presentation.reader.PAGE_BAR_FOCUS_EXTRA
import com.vegamstories.app.presentation.reader.pageBarFill
import com.vegamstories.app.presentation.reader.pageBarLayout
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The page bars under the comic: which are full, empty, or filling with the auto-turn timer; and
 * the current page's bar, wider than the rest.
 */
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

    @Test
    fun `the current page's bar is wider than the rest, and the bars fill the width`() {
        val bars = pageBarLayout(pageCount = 5, focus = 2f, totalWidth = 500f, gap = 4f)

        val others = bars.filterIndexed { i, _ -> i != 2 }.map { it.width }.distinct()
        assertEquals(1, others.size)
        assertEquals(1f + PAGE_BAR_FOCUS_EXTRA, bars[2].width / others.single(), 0.001f)
        assertEquals(1f, bars[2].near)
        assertEquals(500f, bars.last().left + bars.last().width, 0.01f)
        // Side by side, in order, the gap apart.
        bars.zipWithNext().forEach { (a, b) -> assertEquals(a.left + a.width + 4f, b.left, 0.01f) }
    }

    @Test
    fun `sliding to the next page, the two bars share the extra width half way`() {
        val bars = pageBarLayout(pageCount = 5, focus = 2.5f, totalWidth = 500f, gap = 4f)

        assertEquals(bars[2].width, bars[3].width, 0.01f)
        assertEquals(0.5f, bars[2].near, 0.001f)
        assertEquals(500f, bars.last().left + bars.last().width, 0.01f)
    }
}
