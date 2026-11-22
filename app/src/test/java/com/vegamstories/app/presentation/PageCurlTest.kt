package com.vegamstories.app.presentation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import com.vegamstories.app.presentation.reader.AUTO_TURN_LEAN
import com.vegamstories.app.presentation.reader.PageCurl
import com.vegamstories.app.presentation.reader.curlBands
import com.vegamstories.app.presentation.reader.fingerLean
import com.vegamstories.app.presentation.reader.rollShade
import com.vegamstories.app.presentation.reader.setAcross
import com.vegamstories.app.presentation.reader.turnLayer
import com.vegamstories.app.presentation.reader.turnPosition
import com.vegamstories.app.presentation.reader.turnVisible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/** The book page curl in the sideways reader: which page turns, where it folds, and how it is drawn. */
class PageCurlTest {

    private val width = 1080f
    private val height = 1920f
    private val radius = 75f
    private val corners = listOf(Offset(0f, 0f), Offset(width, 0f), Offset(0f, height), Offset(width, height))

    private fun curl(progress: Float, lean: Float = 0f) = PageCurl(width, height, progress, lean, radius)

    /** How far along the fold [p] is. */
    private fun PageCurl.along(p: Offset): Float = (p.x - fx) * -ny + (p.y - fy) * nx

    @Test
    fun `at rest only the open page shows, on top`() {
        val open = turnPosition(page = 3, currentPage = 3, offsetFraction = 0f)
        assertEquals(0f, open)
        assertTrue(turnVisible(open))
        assertEquals(1f, turnLayer(open))
        // The pages either side are hidden, under everything.
        for (neighbour in listOf(2, 4, 5)) {
            val position = turnPosition(neighbour, currentPage = 3, offsetFraction = 0f)
            assertFalse(turnVisible(position))
            assertEquals(-1f, turnLayer(position))
        }
    }

    @Test
    fun `turning forward curls the open page away over the next one, then it is gone`() {
        // A quarter of the way to page 4.
        val turning = turnPosition(page = 3, currentPage = 3, offsetFraction = 0.25f)
        val next = turnPosition(page = 4, currentPage = 3, offsetFraction = 0.25f)
        assertEquals(-0.25f, turning) // the curl's progress is 0.25
        assertTrue(turnVisible(next))
        assertTrue(turnLayer(turning) > turnLayer(next))
        // Past halfway the pager counts page 4 as current; the position runs on without a jump.
        val later = turnPosition(page = 3, currentPage = 4, offsetFraction = -0.4f)
        assertEquals(turnPosition(3, currentPage = 3, offsetFraction = 0.6f), later, 0.0001f)
        assertTrue(turnLayer(later) > turnLayer(turnPosition(page = 4, currentPage = 4, offsetFraction = -0.4f)))
        // Fully turned: gone.
        assertFalse(turnVisible(turnPosition(page = 3, currentPage = 4, offsetFraction = 0f)))
    }

    @Test
    fun `turning back uncurls the previous page over the open one from the first move`() {
        // Just started dragging back from page 4: the previous page shows, still nearly turned away.
        val justStarted = turnPosition(page = 3, currentPage = 4, offsetFraction = -0.1f)
        assertTrue(turnVisible(justStarted))
        assertEquals(-0.9f, justStarted, 0.0001f)
        // Past halfway, page 3 counts as current and its offset runs from +0.5 down to 0 as it settles.
        val previous = turnPosition(page = 3, currentPage = 3, offsetFraction = 0.3f)
        val open = turnPosition(page = 4, currentPage = 3, offsetFraction = 0.3f)
        assertTrue(turnLayer(previous) > turnLayer(open))
    }

    @Test
    fun `the fold starts just past the right edge and ends with the roll past the left edge`() {
        for (lean in listOf(-1f, -0.3f, 0f, AUTO_TURN_LEAN, 1f)) {
            // Nothing lifted, but only just: the first move lifts a corner.
            val flat = curl(progress = 0f, lean = lean)
            assertEquals(0f, corners.maxOf { flat.distance(it.x, it.y) }, 0.5f)
            // Turned away: even the roll (up to one radius past the fold) has left the page.
            val gone = curl(progress = 1f, lean = lean)
            assertEquals(radius, corners.minOf { gone.distance(it.x, it.y) }, 0.5f)
        }
        // In between, the fold sweeps steadily right to left.
        val folds = listOf(0.2f, 0.5f, 0.8f).map { curl(it).fx }
        assertTrue(folds[0] > folds[1] && folds[1] > folds[2])
    }

    @Test
    fun `a forward lean lifts the bottom corner first, a backward one the top`() {
        val bottomFirst = curl(progress = 0.05f, lean = 0.5f)
        assertTrue(bottomFirst.distance(width, height) > 0f)
        assertTrue(bottomFirst.distance(width, 0f) < 0f)
        val topFirst = curl(progress = 0.05f, lean = -0.5f)
        assertTrue(topFirst.distance(width, 0f) > 0f)
        assertTrue(topFirst.distance(width, height) < 0f)
        // No lean: the fold runs straight down the page.
        val straight = curl(progress = 0.5f)
        assertEquals(straight.distance(width / 2, 0f), straight.distance(width / 2, height), 0.001f)
    }

    @Test
    fun `the finger leans the fold toward its corner, leading the way it moves`() {
        assertEquals(1f, fingerLean(y = height, height = height, forward = true))
        assertEquals(-1f, fingerLean(y = 0f, height = height, forward = true))
        assertEquals(0f, fingerLean(y = height / 2, height = height, forward = true))
        // Turning back the held corner leads the other way.
        assertEquals(-0.5f, fingerLean(y = height * 0.75f, height = height, forward = false))
        // Off the page (dragged past its edge) it leans no further.
        assertEquals(1f, fingerLean(y = height * 2, height = height, forward = true))
        // Turned by the app: the bottom corner leads.
        assertTrue(AUTO_TURN_LEAN > 0f)
    }

    @Test
    fun `the roll goes once around half the cylinder, front up its side then back over the top`() {
        val bands = curlBands(radius)
        assertEquals(0f, bands.first().from)
        assertEquals(PI.toFloat() * radius, bands.last().to, 0.01f)
        bands.zipWithNext { a, b ->
            assertEquals(a.to, b.from, 0.001f)
            assertEquals(a.drawnTo, b.drawnFrom, 0.001f)
        }
        val front = bands.filterNot { it.isBack }
        val back = bands.filter { it.isBack }
        assertEquals(bands.size / 2, front.size)
        assertTrue(bands.indexOf(front.last()) < bands.indexOf(back.first())) // the back is drawn over the front
        // Up the side, seen from above: further and further out, to the roll's outer edge.
        assertTrue(front.all { it.drawnTo > it.drawnFrom })
        assertEquals(radius, front.last().drawnTo, 0.01f)
        // Over the top: back toward the fold, where the rest of the page lies flat.
        assertTrue(back.all { it.drawnTo < it.drawnFrom })
        assertEquals(0f, back.last().drawnTo, 0.01f)
    }

    @Test
    fun `the roll darkens toward its outer edge, unshaded where it meets the flat page`() {
        assertEquals(0f, rollShade(across = 0f, radius = radius), 0.0001f)
        val shades = (0..10).map { rollShade(across = radius * it / 10, radius = radius) }
        shades.zipWithNext { a, b -> assertTrue(b > a) }
        // Mostly in the last stretch, where the page turns side-on.
        assertTrue(shades[5] < shades[10] / 4)
        // Beyond the roll it darkens no further.
        assertEquals(shades[10], rollShade(across = radius * 2, radius = radius), 0.0001f)
    }

    @Test
    fun `each part of the page is drawn where it lands across the fold, staying put along it`() {
        val c = curl(progress = 0.4f, lean = 0.5f)
        val matrix = Matrix()
        val point = Offset(700f, 1500f)
        val d = c.distance(point.x, point.y)

        // Before the fold: unmoved.
        matrix.setAcross(c, scale = 1f, shift = 0f)
        assertEquals(point, matrix.map(point))

        // Past the roll: folded back over the page, mirrored across the fold.
        matrix.setAcross(c, scale = -1f, shift = PI.toFloat() * radius)
        val folded = matrix.map(point)
        assertEquals(PI.toFloat() * radius - d, c.distance(folded.x, folded.y), 0.01f)
        assertEquals(c.along(point), c.along(folded), 0.01f)

        // A band of the roll: its ends land where the cylinder puts them.
        for (band in curlBands(radius)) {
            matrix.setAcross(c, band.scale, band.shift)
            val start = matrix.map(c.at(band.from))
            val end = matrix.map(c.at(band.to))
            assertEquals(band.drawnFrom, c.distance(start.x, start.y), 0.01f)
            assertEquals(band.drawnTo, c.distance(end.x, end.y), 0.01f)
        }
    }
}
