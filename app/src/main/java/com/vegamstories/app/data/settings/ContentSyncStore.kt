package com.vegamstories.app.data.settings

import com.vegamstories.app.core.logging.logTime
import com.vegamstories.app.core.logging.AppLogger
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ContentSyncStore"

/**
 * When the story catalogue was last checked, so opening the app again within minutes doesn't
 * check again, and which version the saved stories came from, so a check downloads only a new one.
 */
@Singleton
class ContentSyncStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    /** When the last check succeeded (epoch millis), or null if none has. */
    suspend fun lastRefreshAt(): Long? = dataStore.data.first()[LAST_REFRESH_AT]

    suspend fun setLastRefreshAt(at: Long) {
        dataStore.edit { it[LAST_REFRESH_AT] = at }
        log.d("$TAG: Stories checked at ${logTime(at)}; next automatic check when the app opens again")
    }

    /** The catalogue version of the saved stories (see StoryRemoteDataSource), or null. */
    suspend fun catalogueVersion(): String? = dataStore.data.first()[CATALOGUE_VERSION]

    /** null: not known, so the next check downloads the catalogue again. */
    suspend fun setCatalogueVersion(version: String?) {
        dataStore.edit { if (version == null) it.remove(CATALOGUE_VERSION) else it[CATALOGUE_VERSION] = version }
    }

    private val log = AppLogger(TAG)

    private companion object {
        val LAST_REFRESH_AT = longPreferencesKey("content_last_refresh_at")
        val CATALOGUE_VERSION = stringPreferencesKey("content_catalogue_version")
    }
}
