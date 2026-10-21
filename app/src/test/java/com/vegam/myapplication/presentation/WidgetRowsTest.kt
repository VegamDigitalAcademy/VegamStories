package com.vegam.myapplication.presentation

import com.vegam.myapplication.presentation.widget.WidgetEntry.Kind
import com.vegam.myapplication.presentation.widget.widgetRows
import com.vegam.myapplication.testutil.testStory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which rows the home screen widget shows, and how they're labelled. */
class WidgetRowsTest {

    private val reading = testStory(id = "moral-2", publishedAt = 100, opened = true)
    private val newest = testStory(id = "moral-3", publishedAt = 200)
    private val always = { _: Long -> true }
    private val never = { _: Long -> false }

    @Test
    fun `continue reading first, then today's new story`() {
        val rows = widgetRows(continueReading = reading, newest = newest, isToday = always)
        assertEquals(listOf(Kind.CONTINUE_READING to reading, Kind.NEW_TODAY to newest), rows)
    }

    @Test
    fun `an older newest story is labelled latest`() {
        val rows = widgetRows(continueReading = reading, newest = newest, isToday = never)
        assertEquals(listOf(Kind.CONTINUE_READING to reading, Kind.LATEST to newest), rows)
    }

    @Test
    fun `the newest story isn't repeated when it's the one being read`() {
        val rows = widgetRows(continueReading = reading, newest = reading, isToday = always)
        assertEquals(listOf(Kind.CONTINUE_READING to reading), rows)
    }

    @Test
    fun `with nothing in progress only the newest story shows`() {
        val rows = widgetRows(continueReading = null, newest = newest, isToday = always)
        assertEquals(listOf(Kind.NEW_TODAY to newest), rows)
    }

    @Test
    fun `no stories means an empty widget`() {
        assertTrue(widgetRows(continueReading = null, newest = null, isToday = always).isEmpty())
    }

    @Test
    fun `the date check is made with the newest story's publish time`() {
        var checked: Long? = null
        widgetRows(continueReading = null, newest = newest, isToday = { checked = it; true })
        assertEquals(200L, checked)
    }
}
