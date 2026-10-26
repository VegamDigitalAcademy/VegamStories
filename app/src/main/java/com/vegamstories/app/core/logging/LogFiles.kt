package com.vegamstories.app.core.logging

import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The logs on the phone: a folder per day and a file per hour, in the phone's time,
 *
 *     logs/2026-10-26/09.log
 *     logs/2026-10-26/14.log
 *
 * kept for [keepDays] days and [maxBytes] at most, the oldest going first. One line per log,
 * its data and stack trace indented under it:
 *
 *     2026-10-26 14:03:22.517 D/StoryRepositoryImpl [main] StoryRepositoryImpl: Refresh requested
 *         {
 *           "force": true
 *         }
 *
 * Used only on AppLog's writer thread, except [list], which only reads the folder.
 */
internal class LogFiles(
    val dir: File,
    private val keepDays: Int = KEEP_DAYS,
    private val maxBytes: Long = MAX_TOTAL_BYTES,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val hourFormat = SimpleDateFormat("HH", Locale.US)
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private var current: File? = null
    private var out: Writer? = null

    /** Adds [entry], with its [data] already printed, to the file of its hour. */
    fun write(entry: LogEntry, data: String?) {
        val time = Date(entry.timeMillis)
        val file = File(File(dir, dayFormat.format(time)), hourFormat.format(time) + ".log")
        if (file != current) open(file)
        val writer = out ?: return
        // Flushed every time: a line is on disk even if the app is killed right after.
        writer.write(format(entry, time, data))
        writer.flush()
    }

    private fun format(entry: LogEntry, time: Date, data: String?): String = buildString {
        append(timeFormat.format(time)).append(' ')
        append(entry.level.letter).append('/').append(entry.tag)
        append(" [").append(entry.thread).append("] ")
        append(entry.message).append('\n')
        data?.let { appendIndented(it) }
        entry.error?.let { appendIndented(it.stackTraceToString().trimEnd()) }
    }

    private fun StringBuilder.appendIndented(text: String) {
        text.lineSequence().forEach { append("    ").append(it).append('\n') }
    }

    private fun open(file: File) {
        close()
        current = file
        out = try {
            file.parentFile?.mkdirs()
            BufferedWriter(OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8))
        } catch (e: IOException) {
            null // full or unreadable storage: logs are skipped until the next hour
        }
        prune()
    }

    fun flush() {
        runCatching { out?.flush() }
    }

    private fun close() {
        runCatching { out?.close() }
        out = null
    }

    /** Every log file, oldest first. */
    fun list(): List<File> =
        dir.listFiles { file -> file.isDirectory }.orEmpty().sortedBy { it.name }.flatMap { day ->
            day.listFiles { file -> file.isFile && file.name.endsWith(".log") }.orEmpty().sortedBy { it.name }
        }

    /** Deletes the days before the last [keepDays], then the oldest hours while they're over [maxBytes]. */
    fun prune() {
        val oldestKept = dayFormat.format(Date(now() - (keepDays - 1) * DAY_MS))
        dir.listFiles { file -> file.isDirectory && file.name < oldestKept }.orEmpty().forEach { it.deleteRecursively() }
        val files = list()
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= maxBytes) break
            if (file == current) continue
            total -= file.length()
            file.delete()
        }
        dir.listFiles { file -> file.isDirectory && file.list().isNullOrEmpty() && file != current?.parentFile }
            .orEmpty().forEach { it.delete() }
    }

    companion object {
        const val KEEP_DAYS = 7
        const val MAX_TOTAL_BYTES = 20L * 1024 * 1024
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
