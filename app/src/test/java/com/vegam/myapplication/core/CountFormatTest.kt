package com.vegam.myapplication.core

import com.vegam.myapplication.core.util.toCompactCount
import org.junit.Assert.assertEquals
import org.junit.Test

class CountFormatTest {

    @Test
    fun `small counts are shown as they are`() {
        assertEquals("0", 0.toCompactCount())
        assertEquals("999", 999.toCompactCount())
    }

    @Test
    fun `thousands get a K and one decimal below ten`() {
        assertEquals("1K", 1_000.toCompactCount())
        assertEquals("1.5K", 1_540.toCompactCount())
        assertEquals("9.9K", 9_999.toCompactCount())
        assertEquals("12K", 12_400.toCompactCount())
        assertEquals("999K", 999_999.toCompactCount())
    }

    @Test
    fun `millions get an M`() {
        assertEquals("1M", 1_000_000.toCompactCount())
        assertEquals("2.3M", 2_300_000.toCompactCount())
    }
}
