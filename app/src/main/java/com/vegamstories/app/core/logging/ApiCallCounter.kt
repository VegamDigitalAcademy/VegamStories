package com.vegamstories.app.core.logging

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** One API in Settings → API calls: how many times it was called, and when last (epoch millis). */
@Serializable
data class ApiCallCount(val name: String, val count: Int, val lastCalledAt: Long)

/**
 * Debug builds only (Settings → API calls): how many times each API has been called, counted by
 * ApiCallCountInterceptor. Kept on the phone, so the counts go on across app restarts until Reset.
 * A plain SharedPreferences file, not the app's DataStore: calls are counted on OkHttp's threads,
 * as they happen.
 */
@Singleton
class ApiCallCounter @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _counts = MutableStateFlow(load())

    /** Every API called so far, the latest call first. */
    val counts: StateFlow<List<ApiCallCount>> = _counts.asStateFlow()

    @Synchronized
    fun record(apiName: String, at: Long = System.currentTimeMillis()) {
        val count = (_counts.value.firstOrNull { it.name == apiName }?.count ?: 0) + 1
        _counts.value = listOf(ApiCallCount(apiName, count, at)) + _counts.value.filter { it.name != apiName }
        save()
    }

    @Synchronized
    fun reset() {
        _counts.value = emptyList()
        save()
    }

    private fun load(): List<ApiCallCount> =
        prefs.getString(KEY_COUNTS, null)
            ?.let { runCatching { Json.decodeFromString(serializer, it) }.getOrNull() }
            .orEmpty()

    private fun save() {
        prefs.edit().putString(KEY_COUNTS, Json.encodeToString(serializer, _counts.value)).apply()
    }

    private companion object {
        const val PREFS_NAME = "api_calls"
        const val KEY_COUNTS = "counts"
        val serializer = ListSerializer(ApiCallCount.serializer())
    }
}
