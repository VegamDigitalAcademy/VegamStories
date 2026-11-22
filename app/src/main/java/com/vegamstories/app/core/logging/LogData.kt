package com.vegamstories.app.core.logging

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializerOrNull
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.text.SimpleDateFormat
import java.util.Date
import java.util.IdentityHashMap
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Prints the data given to [AppLogger] as pretty JSON:
 *  - JSON text (a server response) and JsonElements: re-indented;
 *  - @Serializable objects (the server's DTOs): as they go over the wire, by their serializer;
 *  - the app's own classes (models, database rows, screen states): field by field;
 *  - lists, sets, arrays and maps: item by item;
 *  - plain text as it is; anything else by its toString().
 * Long text values, long lists and deep nesting are cut short, and the whole block is capped,
 * so one big response can't flood the log.
 */
internal object LogData {

    /** Characters of one text value (a story's text, a summary). */
    const val MAX_TEXT = 500

    /** Items of one list (stories in a response, pages of a story). */
    const val MAX_ITEMS = 10

    /** How deep objects inside objects are followed. */
    const val MAX_DEPTH = 8

    /** Characters of the whole block. */
    const val MAX_CHARS = 16_000

    /** Only the app's own classes are read field by field (their field names are kept in release builds: keepRules). */
    private const val APP_PACKAGE = "com.vegamstories.app."

    @OptIn(ExperimentalSerializationApi::class)
    private val pretty = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    /** For @Serializable objects: every property, also those left at their defaults. */
    private val wire = Json { encodeDefaults = true }

    private val fieldsByClass = ConcurrentHashMap<Class<*>, List<Field>>()

    fun render(data: Any?): String {
        if (data is CharSequence && !looksLikeJson(data)) return capped(data.toString())
        val element = toElement(data, depth = 0, path = IdentityHashMap())
        return capped(pretty.encodeToString(JsonElement.serializer(), element))
    }

    private fun toElement(value: Any?, depth: Int, path: IdentityHashMap<Any, Unit>): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> shorten(value, depth)
        is CharSequence -> {
            val text = value.toString()
            val json = if (looksLikeJson(text)) runCatching { Json.parseToJsonElement(text) }.getOrNull() else null
            json?.let { shorten(it, depth) } ?: JsonPrimitive(short(text))
        }
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Char -> JsonPrimitive(value.toString())
        is Enum<*> -> JsonPrimitive(value.name)
        else -> when {
            depth >= MAX_DEPTH -> JsonPrimitive("…")
            // An object inside itself (a loop): said once, not followed again.
            path.containsKey(value) -> JsonPrimitive("(the same object as above)")
            else -> {
                path[value] = Unit
                try {
                    structured(value, depth, path)
                } finally {
                    path.remove(value)
                }
            }
        }
    }

    private fun structured(value: Any, depth: Int, path: IdentityHashMap<Any, Unit>): JsonElement = when (value) {
        is Map<*, *> -> {
            val shown = value.entries.take(MAX_ITEMS * 3).associate { (key, item) -> key.toString() to toElement(item, depth + 1, path) }
            val hidden = value.size - shown.size
            JsonObject(if (hidden > 0) shown + ("…" to JsonPrimitive("$hidden more")) else shown)
        }
        is Collection<*> -> list(value, value.size, depth, path)
        is Iterable<*> -> list(value, value.count(), depth, path)
        is Array<*> -> list(value.asList(), value.size, depth, path)
        is IntArray -> list(value.asList(), value.size, depth, path)
        is LongArray -> list(value.asList(), value.size, depth, path)
        is FloatArray -> list(value.asList(), value.size, depth, path)
        is DoubleArray -> list(value.asList(), value.size, depth, path)
        is BooleanArray -> list(value.asList(), value.size, depth, path)
        is ByteArray -> JsonPrimitive("(${value.size} bytes)")
        is Pair<*, *> -> JsonObject(mapOf("first" to toElement(value.first, depth + 1, path), "second" to toElement(value.second, depth + 1, path)))
        is Throwable -> JsonPrimitive(short(value.toString()))
        else -> serialized(value, depth) ?: fields(value, depth, path) ?: JsonPrimitive(short(value.toString()))
    }

    private fun list(items: Iterable<*>, size: Int, depth: Int, path: IdentityHashMap<Any, Unit>): JsonArray {
        val shown = items.take(MAX_ITEMS).map { toElement(it, depth + 1, path) }
        return JsonArray(if (size > MAX_ITEMS) shown + JsonPrimitive("… ${size - MAX_ITEMS} more") else shown)
    }

    /** A @Serializable object, by its own serializer: its JSON names, as the server sees it. */
    private fun serialized(value: Any, depth: Int): JsonElement? {
        val serializer = runCatching { serializerOrNull(value.javaClass) }.getOrNull() ?: return null
        return runCatching { shorten(wire.encodeToJsonElement(serializer, value), depth) }.getOrNull()
    }

    /** One of the app's own classes, field by field. Objects without fields (data objects) by their name. */
    private fun fields(value: Any, depth: Int, path: IdentityHashMap<Any, Unit>): JsonElement? {
        val type = value.javaClass
        if (!type.name.startsWith(APP_PACKAGE)) return null
        val fields = fieldsByClass.getOrPut(type) { fieldsOf(type) }
        if (fields.isEmpty()) return JsonPrimitive(value.toString())
        return JsonObject(
            fields.associate { field ->
                field.name to toElement(runCatching { field.get(value) }.getOrNull(), depth + 1, path)
            },
        )
    }

    private fun fieldsOf(type: Class<*>): List<Field> =
        generateSequence(type) { it.superclass }
            .takeWhile { it.name.startsWith(APP_PACKAGE) }
            .flatMap { it.declaredFields.asSequence() }
            // Not companion constants, not compiler-made fields (lazy delegates, $stable…).
            .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic && '$' !in it.name }
            .onEach { it.isAccessible = true }
            .toList()

    private fun shorten(element: JsonElement, depth: Int): JsonElement = when (element) {
        is JsonObject -> if (depth >= MAX_DEPTH) JsonPrimitive("{…}") else JsonObject(element.mapValues { shorten(it.value, depth + 1) })
        is JsonArray -> when {
            depth >= MAX_DEPTH -> JsonPrimitive("[…]")
            element.size > MAX_ITEMS -> JsonArray(element.take(MAX_ITEMS).map { shorten(it, depth + 1) } + JsonPrimitive("… ${element.size - MAX_ITEMS} more"))
            else -> JsonArray(element.map { shorten(it, depth + 1) })
        }
        is JsonPrimitive -> if (element.isString && element.content.length > MAX_TEXT) JsonPrimitive(short(element.content)) else element
    }

    private fun short(text: String): String =
        if (text.length <= MAX_TEXT) text else text.take(MAX_TEXT) + "… (+${text.length - MAX_TEXT} characters)"

    private fun capped(text: String): String =
        if (text.length <= MAX_CHARS) text else text.take(MAX_CHARS) + "\n… (${text.length - MAX_CHARS} more characters not shown)"

    private fun looksLikeJson(text: CharSequence): Boolean {
        val start = text.indexOfFirst { !it.isWhitespace() }
        return start >= 0 && (text[start] == '{' || text[start] == '[')
    }
}

/** A time for a log line, e.g. "2026-10-26 09:12:44", or "never" for null. */
fun logTime(millis: Long?): String =
    if (millis == null) "never" else SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(millis))

/** A duration for a log line: "850 ms", "42 s", "3 min 12 s", "1 h 05 min". */
fun logDuration(millis: Long): String = when {
    millis < 1_000 -> "$millis ms"
    millis < 60_000 -> "${millis / 1_000} s"
    millis < 3_600_000 -> "${millis / 60_000} min ${millis / 1_000 % 60} s"
    else -> "${millis / 3_600_000} h %02d min".format(millis / 60_000 % 60)
}
