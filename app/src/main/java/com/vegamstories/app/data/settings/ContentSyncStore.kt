package com.vegamstories.app.data.settings

import com.vegamstories.app.core.logging.logTime
import com.vegamstories.app.core.logging.AppLogger
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ContentSyncStore"

/** When the story catalogue was last downloaded, so the app downloads it at most once a day. */
@Singleton
class ContentSyncStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    /** When the last download succeeded (epoch millis), or null if it never has. */
    suspend fun lastRefreshAt(): Long? = dataStore.data.first()[LAST_REFRESH_AT]

    suspend fun setLastRefreshAt(at: Long) {
        dataStore.edit { it[LAST_REFRESH_AT] = at }
        log.d("$TAG: Stories downloaded at ${logTime(at)}; next automatic download after a day")
    }

    private val log = AppLogger(TAG)

    private companion object {
        val LAST_REFRESH_AT = longPreferencesKey("content_last_refresh_at")
    }
}
