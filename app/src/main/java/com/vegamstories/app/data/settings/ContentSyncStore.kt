package com.vegamstories.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** When the story catalogue was last downloaded, so the app downloads it at most once a day. */
@Singleton
class ContentSyncStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    /** When the last download succeeded (epoch millis), or null if it never has. */
    suspend fun lastRefreshAt(): Long? = dataStore.data.first()[LAST_REFRESH_AT]

    suspend fun setLastRefreshAt(at: Long) {
        dataStore.edit { it[LAST_REFRESH_AT] = at }
    }

    private companion object {
        val LAST_REFRESH_AT = longPreferencesKey("content_last_refresh_at")
    }
}
