package com.vegamstories.app.data.settings

import com.vegamstories.app.core.logging.AppLogger
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DeviceIdProvider"

/**
 * A random id for this install, made on first use. The app has no login, so the server uses it
 * to count one like per device. It is not tied to the phone or the person.
 */
@Singleton
class DeviceIdProvider @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun get(): String {
        dataStore.data.first()[DEVICE_ID]?.let { return it }
        // edit() is atomic, so two first calls at the same time still agree on one id.
        val prefs = dataStore.edit {
            if (it[DEVICE_ID] == null) {
                it[DEVICE_ID] = UUID.randomUUID().toString()
                log.i("$TAG: Random app ID made for likes (first like on this install)")
            }
        }
        return checkNotNull(prefs[DEVICE_ID])
    }

    private val log = AppLogger(TAG)

    private companion object {
        val DEVICE_ID = stringPreferencesKey("device_id")
    }
}
