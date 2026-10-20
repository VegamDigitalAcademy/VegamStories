package com.vegam.myapplication.data

import com.vegam.myapplication.data.remote.datasource.parseSupabaseTimestamp
import org.junit.Assert.assertEquals
import org.junit.Test

class SupabaseTimestampTest {

    private val expected = 1_760_984_527_000L // 2025-10-20T18:22:07Z

    @Test
    fun `parses timestamptz with microseconds`() {
        assertEquals(expected, parseSupabaseTimestamp("2025-10-20T18:22:07.123456+00:00"))
    }

    @Test
    fun `parses Z suffix and other offsets`() {
        assertEquals(expected, parseSupabaseTimestamp("2025-10-20T18:22:07Z"))
        assertEquals(expected, parseSupabaseTimestamp("2025-10-20 23:52:07+05:30"))
    }

    @Test
    fun `garbage becomes zero instead of crashing`() {
        assertEquals(0L, parseSupabaseTimestamp("not a date"))
    }
}
