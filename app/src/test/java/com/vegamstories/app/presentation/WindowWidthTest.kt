package com.vegamstories.app.presentation

import androidx.compose.ui.unit.dp
import com.vegamstories.app.presentation.components.isWideLandscape
import com.vegamstories.app.presentation.components.storyGridColumns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bigger screens: more covers per row, and two comic pages side by side when there's room. */
class WindowWidthTest {

    @Test
    fun `phones keep two covers per row`() {
        assertEquals(2, storyGridColumns(320.dp))
        assertEquals(2, storyGridColumns(411.dp))
        assertEquals(2, storyGridColumns(599.dp))
    }

    @Test
    fun `small tablets and open foldables show three`() {
        assertEquals(3, storyGridColumns(600.dp))
        assertEquals(3, storyGridColumns(800.dp))
    }

    @Test
    fun `large tablets show four or more, each cover about phone size`() {
        assertEquals(4, storyGridColumns(840.dp))
        assertEquals(4, storyGridColumns(900.dp))
        assertEquals(6, storyGridColumns(1280.dp))
    }

    @Test
    fun `two pages side by side only on a wide screen lying on its side`() {
        assertTrue("tablet on its side", isWideLandscape(1280.dp, 800.dp))
        assertTrue("open foldable on its side", isWideLandscape(900.dp, 670.dp))
        assertFalse("tablet upright", isWideLandscape(800.dp, 1280.dp))
        assertFalse("open foldable upright", isWideLandscape(670.dp, 900.dp))
        assertFalse("phone", isWideLandscape(411.dp, 891.dp))
        assertFalse("small window on its side", isWideLandscape(560.dp, 400.dp))
    }
}
