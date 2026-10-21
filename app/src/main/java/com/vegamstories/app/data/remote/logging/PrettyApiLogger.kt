package com.vegamstories.app.data.remote.logging

import com.vegamstories.app.core.util.toJson
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber

/**
 * Turns OkHttp's raw log lines into a readable block per call. In Logcat, filter by tag `VegamApi`:
 *
 *     ┌─▶ GET https://xxxx.supabase.co/rest/v1/stories?select=…
 *     │ apikey: ██
 *     └── end of request
 *     ┌─◀ 200 https://xxxx.supabase.co/rest/v1/stories?select=… (312ms)
 *     │ content-type: application/json; charset=utf-8
 *     [
 *         {
 *             "id": "moral-1",
 *             "title": "నిజాయితీ బహుమతి",
 *             …
 *     └── END HTTP (2041-byte body)
 */
class PrettyApiLogger : HttpLoggingInterceptor.Logger {

    override fun log(message: String) {
        val line = when {
            message.startsWith("--> END") -> "└── end of request"
            message.startsWith("-->") -> "┌─▶ " + message.removePrefix("-->").trim()
            message.startsWith("<-- END") -> "└── " + message.removePrefix("<--").trim()
            message.startsWith("<-- HTTP FAILED") -> "✖ " + message.removePrefix("<--").trim()
            message.startsWith("<--") -> "┌─◀ " + message.removePrefix("<--").trim()
            else -> {
                val pretty = message.toJson()
                if (pretty !== message) "\n" + pretty else "│ $message"
            }
        }
        Timber.tag(TAG).d(line)
    }

    companion object {
        const val TAG = "VegamApi"
    }
}
