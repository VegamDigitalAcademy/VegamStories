package com.vegamstories.app.core.util

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** Shared pretty printer: 4-space indent, Telugu and other non-English text kept readable. */
@PublishedApi
internal val PrettyJson = Json {
    prettyPrint = true
    encodeDefaults = true
}

/**
 * Pretty-prints JSON text for logs:
 *
 *     """{"id":"moral-1","pages":[1,2]}""".toJson()
 *     // {
 *     //     "id": "moral-1",
 *     //     "pages": [
 *     //         1,
 *     //         2
 *     //     ]
 *     // }
 *
 * Anything that isn't valid JSON (plain text, HTML, a half-received body) comes back unchanged,
 * so it's always safe to call.
 */
fun String.toJson(): String {
    val text = trim()
    if (text.isEmpty() || (text.first() != '{' && text.first() != '[')) return this
    return runCatching {
        PrettyJson.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(text))
    }.getOrDefault(this)
}

/**
 * Pretty JSON for any @Serializable object (DTOs, entities…), e.g. `Timber.d(story.toPrettyJson())`.
 * Falls back to toString() for classes that aren't @Serializable.
 */
inline fun <reified T> T.toPrettyJson(): String =
    runCatching { PrettyJson.encodeToString(this) }.getOrElse { toString() }
