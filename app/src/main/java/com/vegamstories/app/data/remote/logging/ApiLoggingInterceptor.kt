package com.vegamstories.app.data.remote.logging

import com.vegamstories.app.core.logging.AppLog
import com.vegamstories.app.core.logging.AppLogger
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "ApiLoggingInterceptor"

/**
 * Logs every call to the server (tag "Api"), in every build, numbered so each response can be
 * matched with its request:
 *
 *     → #3 GET stories
 *         { "url": "https://xxxx.supabase.co/rest/v1/stories?select=id,category_id,…" }
 *     ← #3 GET stories: 200 in 412 ms (84 KB)
 *         [ { "id": "moral-1", "category_id": "moral", … }, "… 27 more" ]
 *
 * Bodies print as pretty JSON, shortened (see AppLogger). The project key (apikey,
 * Authorization) is never logged: no header is. A call that gets no response logs the error and
 * how long it waited.
 */
class ApiLoggingInterceptor : Interceptor {

    private val log = AppLogger(TAG)
    private val calls = AtomicInteger()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!AppLog.isEnabled) return chain.proceed(request)

        val call = "#${calls.incrementAndGet()} ${request.method} ${request.url.encodedPath.substringAfterLast('/')}"
        log.d("$TAG: → $call", data = requestData(request))
        val start = System.nanoTime()
        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            log.w("$TAG: ✕ $call: no response after ${elapsedMs(start)} ms", e)
            throw e
        }
        val took = elapsedMs(start)

        // Peeked, so Retrofit still reads the whole body afterwards.
        val body = response.peekBody(MAX_BODY_BYTES)
        val bytes = body.contentLength()
        val type = body.contentType()
        val size = if (bytes >= MAX_BODY_BYTES) "first ${bytes / 1024} KB shown" else sizeText(bytes)
        val summary = "← $call: ${response.code} in $took ms ($size)"
        val data = if (type.isText()) body.string() else "(${type ?: "no content type"})"
        if (response.isSuccessful) log.d("$TAG: $summary", data) else log.w("$TAG: $summary", data = data)
        return response
    }

    /** The full address (readable, not %-encoded), and the body of a POST. */
    private fun requestData(request: Request): Map<String, String> = buildMap {
        val url = request.url.toString()
        put("url", runCatching { URLDecoder.decode(url, "UTF-8") }.getOrDefault(url))
        val body = request.body
        if (body != null && !body.isOneShot()) {
            put(
                "body",
                if (body.contentType().isText()) {
                    Buffer().also { body.writeTo(it) }.readUtf8()
                } else {
                    "(${sizeText(body.contentLength())} of ${body.contentType()})"
                },
            )
        }
    }

    private fun MediaType?.isText(): Boolean =
        this != null && (type == "text" || subtype.contains("json") || subtype.contains("xml"))

    private fun sizeText(bytes: Long): String = when {
        bytes < 0 -> "size unknown"
        bytes < 1024 -> "$bytes bytes"
        else -> "${bytes / 1024} KB"
    }

    private fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000

    private companion object {
        /** Bodies up to this size are logged (shortened); bigger ones only their start. */
        const val MAX_BODY_BYTES = 1024L * 1024
    }
}
