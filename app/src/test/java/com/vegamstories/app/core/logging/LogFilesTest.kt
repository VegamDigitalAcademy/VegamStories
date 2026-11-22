package com.vegamstories.app.core.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/** The log files on the phone: a folder per day, a file per hour, the oldest deleted first. */
class LogFilesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private fun at(time: String): Long = format.parse(time)!!.time

    private fun entry(time: String, message: String = "hello", error: Throwable? = null) =
        LogEntry(at(time), LogLevel.INFO, "Test", "main", message, error, data = null, source = null)

    @Test
    fun `each hour gets its own file in its day's folder`() {
        val files = LogFiles(folder.root, now = { at("2026-10-26 15:00:00") })
        files.write(entry("2026-10-26 09:15:00", "first"), null)
        files.write(entry("2026-10-26 14:01:02", "second"), "{\n  \"a\": 1\n}")
        files.write(entry("2026-10-26 14:59:59", "third"), null)

        assertEquals(listOf("2026-10-26/09.log", "2026-10-26/14.log"), files.list().map { it.relativeTo(folder.root).path })
        assertEquals(
            "2026-10-26 14:01:02.000 I/Test [main] second\n" +
                "    {\n" +
                "      \"a\": 1\n" +
                "    }\n" +
                "2026-10-26 14:59:59.000 I/Test [main] third\n",
            File(folder.root, "2026-10-26/14.log").readText(),
        )
    }

    @Test
    fun `an error's stack trace is written under its line`() {
        val files = LogFiles(folder.root, now = { at("2026-10-26 15:00:00") })
        files.write(entry("2026-10-26 15:00:00", "failed", IllegalStateException("boom")), null)
        val lines = File(folder.root, "2026-10-26/15.log").readLines()
        assertEquals("2026-10-26 15:00:00.000 I/Test [main] failed", lines[0])
        assertEquals("    java.lang.IllegalStateException: boom", lines[1])
        assertTrue(lines[2].startsWith("    \tat "))
    }

    @Test
    fun `days before the last week are deleted`() {
        listOf("2026-10-18", "2026-10-19", "2026-10-20").forEach { day ->
            File(folder.root, "$day/10.log").apply { parentFile!!.mkdirs(); writeText("old\n") }
        }
        val files = LogFiles(folder.root, keepDays = 7, now = { at("2026-10-26 12:00:00") })
        files.write(entry("2026-10-26 12:00:00"), null)

        assertFalse(File(folder.root, "2026-10-18").exists())
        assertFalse(File(folder.root, "2026-10-19").exists())
        assertTrue(File(folder.root, "2026-10-20/10.log").exists()) // the 7th day back is kept
    }

    @Test
    fun `over the size limit the oldest hours go first, never the one being written`() {
        listOf("2026-10-25/08.log", "2026-10-25/09.log", "2026-10-26/07.log").forEach { name ->
            File(folder.root, name).apply { parentFile!!.mkdirs(); writeText("x".repeat(100)) }
        }
        val files = LogFiles(folder.root, maxBytes = 250, now = { at("2026-10-26 12:00:00") })
        files.write(entry("2026-10-26 12:00:00"), null)

        assertEquals(
            listOf("2026-10-25/09.log", "2026-10-26/07.log", "2026-10-26/12.log"),
            files.list().map { it.relativeTo(folder.root).path },
        )
    }
}
