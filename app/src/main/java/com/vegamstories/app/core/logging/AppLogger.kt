package com.vegamstories.app.core.logging

import kotlin.coroutines.cancellation.CancellationException

/**
 * How important a log line is. Debug builds print every level to Logcat; the log files on the
 * phone keep [DEBUG] and up. [priority] matches android.util.Log's levels.
 */
enum class LogLevel(val priority: Int, val letter: Char) {
    VERBOSE(2, 'V'),
    DEBUG(3, 'D'),
    INFO(4, 'I'),
    WARN(5, 'W'),
    ERROR(6, 'E'),
}

/**
 * The app's logger: one per class, under the class's name ([tag]). Every message starts with
 * that name too, written at the call through the file's TAG constant.
 *
 *     private const val TAG = "StoryRepositoryImpl"
 *     private val log = AppLogger(TAG)
 *
 *     log.d("$TAG: Refresh requested", data = mapOf("force" to force))    // a message, with data under it
 *     log.i("$TAG: Bookmark added", data = bookmark)                       // any object, as pretty JSON
 *     log.w("$TAG: Like not sent, will retry", e)                          // with the error and its stack trace
 *     val feed = log.timed("$TAG: Download stories") { remote.fetchFeed() } // and how long it took
 *
 * [data] is printed under the message as pretty JSON: server objects (@Serializable), the app's
 * own classes (models, database rows, screen states), lists, maps and JSON text. Long values
 * and long lists are cut short, so a log stays readable (see LogData).
 *
 * Where the lines go (see [AppLog]): Logcat in debug builds only; in every build, the log files
 * on the phone that the user can send from Settings → Support → Share logs. Until AppLog is set
 * up (as in unit tests) nothing is logged and nothing is printed.
 */
class AppLogger(val tag: String) {

    /** Step-by-step detail: Logcat only, never in the files. */
    fun v(message: String, data: Any? = null) = log(LogLevel.VERBOSE, message, data = data)

    /** What the app is doing: calls, requests and responses, results. */
    fun d(message: String, data: Any? = null) = log(LogLevel.DEBUG, message, data = data)

    /** Things worth seeing in a support log: the user's actions, downloads, saves. */
    fun i(message: String, data: Any? = null) = log(LogLevel.INFO, message, data = data)

    /** Something went wrong, but the app copes (offline, a retry, a fallback). */
    fun w(message: String, error: Throwable? = null, data: Any? = null) = log(LogLevel.WARN, message, error, data)

    /** Something went wrong and the user notices (a failed screen, a crash). */
    fun e(message: String, error: Throwable? = null, data: Any? = null) = log(LogLevel.ERROR, message, error, data)

    fun log(level: LogLevel, message: String, error: Throwable? = null, data: Any? = null) {
        AppLog.write(level, tag, message, error, data)
    }

    /**
     * Runs [block] and logs how long it took: "Download stories…" before it, then "Download
     * stories: done in 412 ms" with [summary] of the result under it (when given), or "failed
     * after 3021 ms" with the error. The result or error is passed on unchanged. Inline, so the
     * block can call suspend functions.
     */
    inline fun <T> timed(action: String, summary: (T) -> Any? = { null }, block: () -> T): T {
        d("$action…")
        val start = System.nanoTime()
        val result = try {
            block()
        } catch (e: Throwable) {
            if (e is CancellationException) {
                d("$action: stopped after ${elapsedMs(start)} ms")
            } else {
                w("$action: failed after ${elapsedMs(start)} ms", e)
            }
            throw e
        }
        d("$action: done in ${elapsedMs(start)} ms", summary(result))
        return result
    }

    @PublishedApi
    internal fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000
}
