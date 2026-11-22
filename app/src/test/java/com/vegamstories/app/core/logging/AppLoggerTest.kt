package com.vegamstories.app.core.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

/** AppLogger leaves the code it wraps unchanged, and costs nothing until AppLog is set up. */
class AppLoggerTest {

    private val log = AppLogger("Test")

    @Test
    fun `timed passes the result on`() {
        assertEquals(42, log.timed("Answer", summary = { "got $it" }) { 42 })
    }

    @Test
    fun `timed passes the error on`() {
        assertThrows(IllegalStateException::class.java) { log.timed<Unit>("Boom") { error("boom") } }
    }

    @Test
    fun `before setup nothing is written`() {
        log.d("Not written anywhere", data = mapOf("a" to 1))
        assertFalse(AppLog.isEnabled)
        assertEquals(emptyList<Any>(), AppLog.logFiles())
    }
}
