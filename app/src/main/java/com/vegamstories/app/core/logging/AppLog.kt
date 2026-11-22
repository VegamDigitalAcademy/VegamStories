package com.vegamstories.app.core.logging

import android.content.Context
import timber.log.Timber
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Where [AppLogger]'s lines go, set up once in VegamStoriesApp:
 *  - Logcat, in debug builds only (through Timber, which splits long lines). Each line ends with
 *    the file and line it came from, e.g. "(StoryRepositoryImpl.kt:112)", a link in Logcat.
 *  - The log files on the phone, in every build (see [LogFiles]), for Settings → Support →
 *    Share logs (see [LogExporter]).
 * Lines are put together and written on one background thread, in order, so logging never
 * slows the app down. A crash is written down before the app closes.
 */
object AppLog {

    /** The logs folder inside the app's private files (not backed up: see res/xml/backup_rules.xml). */
    private const val LOG_DIR = "logs"
    private const val WRITER_THREAD = "AppLog-writer"

    /** Lines waiting to be written at most; beyond it the oldest are dropped, never the app's memory. */
    private const val MAX_WAITING = 5_000

    @Volatile private var files: LogFiles? = null
    @Volatile private var toLogcat = false
    @Volatile private var catchingCrashes = false

    /** Whether anything is written at all: false until [init], so logging in unit tests costs nothing. */
    val isEnabled: Boolean get() = toLogcat || files != null

    private val writer = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(MAX_WAITING),
        { task -> Thread(task, WRITER_THREAD).apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardOldestPolicy(),
    )

    /** Call once, first thing when the app starts. [logcat]: also print to Logcat (debug builds). */
    @Synchronized
    fun init(context: Context, logcat: Boolean) {
        if (logcat && !toLogcat) {
            Timber.plant(Timber.DebugTree())
            toLogcat = true
        }
        if (files == null) files = LogFiles(File(context.filesDir, LOG_DIR))
        writeCrashes()
    }

    internal fun write(level: LogLevel, tag: String, message: String, error: Throwable?, data: Any?) {
        val logcat = toLogcat
        if (!logcat && files == null) return
        if (!logcat && level < LogLevel.DEBUG) return // the files keep DEBUG and up
        val entry = LogEntry(
            timeMillis = System.currentTimeMillis(),
            level = level,
            tag = tag,
            thread = Thread.currentThread().name,
            message = message,
            error = error,
            data = data,
            source = if (logcat) callSite() else null,
        )
        writer.execute { output(entry) }
    }

    /** On the writer thread: prints the data, then writes the line to Logcat and to the file. */
    private fun output(entry: LogEntry) {
        try {
            val data = entry.data?.let { value ->
                runCatching { LogData.render(value) }.getOrElse { "(couldn't print the data: $it)" }
            }
            if (toLogcat) {
                val text = buildString {
                    append(entry.message)
                    entry.source?.let { append(' ').append(it) }
                    data?.let { append('\n').append(it) }
                }
                Timber.tag(entry.tag).log(entry.level.priority, entry.error, text)
            }
            if (entry.level >= LogLevel.DEBUG) files?.write(entry, data)
        } catch (_: Throwable) {
            // Logging must never take the app down.
        }
    }

    /** Waits (up to [timeoutMs]) until everything logged so far is in the files. */
    fun flush(timeoutMs: Long = 3_000) {
        val store = files ?: return
        if (Thread.currentThread().name == WRITER_THREAD) {
            store.flush()
            return
        }
        runCatching { writer.submit { store.flush() }.get(timeoutMs, TimeUnit.MILLISECONDS) }
    }

    /** The logs folder, or null before [init]. */
    val logDir: File? get() = files?.dir

    /** Every log file, oldest first. */
    fun logFiles(): List<File> = files?.list().orEmpty()

    /** A crash is logged with its stack trace and written out before the app closes as usual. */
    private fun writeCrashes() {
        if (catchingCrashes) return
        catchingCrashes = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            write(LogLevel.ERROR, "Crash", "The app crashed (thread ${thread.name})", error, null)
            flush(timeoutMs = 2_000)
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * Where the log call is in the code, as Logcat links it: "(StoryRepositoryImpl.kt:112)". A call
     * inside an inlined lambda (a Flow's map { }) reports the library's file and line, so it is
     * given as its own class's file instead, without the line.
     */
    private fun callSite(): String? {
        val frame = Throwable().stackTrace.firstOrNull { frame -> LOGGER_CLASSES.none { frame.className.startsWith(it) } } ?: return null
        val file = frame.fileName ?: return null
        val owner = frame.className.substringAfterLast('.').substringBefore('$')
        val inlined = frame.className.contains("\$\$inlined") && file.removeSuffix(".kt") != owner
        return if (inlined) "($owner.kt)" else "($file:${frame.lineNumber})"
    }

    /** The logger's own classes (and their lambdas), skipped to find who logged. */
    private val LOGGER_CLASSES = listOf(AppLog::class.java.name, AppLogger::class.java.name)
}

/** One logged line, as handed to the writer thread. */
internal class LogEntry(
    val timeMillis: Long,
    val level: LogLevel,
    val tag: String,
    val thread: String,
    val message: String,
    val error: Throwable?,
    val data: Any?,
    /** "(File.kt:12)" in debug builds, for Logcat. */
    val source: String?,
)
