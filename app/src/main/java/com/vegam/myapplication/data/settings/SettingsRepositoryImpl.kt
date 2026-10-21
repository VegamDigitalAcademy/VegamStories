package com.vegam.myapplication.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vegam.myapplication.domain.model.PageBackground
import com.vegam.myapplication.domain.model.ReaderScrollMode
import com.vegam.myapplication.domain.model.ReaderSettings
import com.vegam.myapplication.domain.model.ThemeMode
import com.vegam.myapplication.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private val prefs: Flow<Preferences> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    override val themeMode: Flow<ThemeMode> = prefs
        // Nothing saved yet (first launch) → follow the phone.
        .map { it[THEME_MODE].toEnumOr(ThemeMode.SYSTEM) }
        .distinctUntilChanged()

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    override val readerSettings: Flow<ReaderSettings> = prefs
        .map { it.toReaderSettings() }
        .distinctUntilChanged()

    override suspend fun updateReaderSettings(transform: (ReaderSettings) -> ReaderSettings) {
        dataStore.edit { prefs ->
            val updated = transform(prefs.toReaderSettings())
            prefs.write(updated)
        }
    }

    private fun Preferences.toReaderSettings(): ReaderSettings {
        val defaults = ReaderSettings()
        return ReaderSettings(
            scrollMode = this[READER_SCROLL_MODE].toEnumOr(defaults.scrollMode),
            keepScreenOn = this[READER_KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
            pageBackground = this[READER_PAGE_BACKGROUND].toEnumOr(defaults.pageBackground),
        )
    }

    private fun MutablePreferences.write(settings: ReaderSettings) {
        this[READER_SCROLL_MODE] = settings.scrollMode.name
        this[READER_KEEP_SCREEN_ON] = settings.keepScreenOn
        this[READER_PAGE_BACKGROUND] = settings.pageBackground.name
    }

    /** Unknown or missing names (e.g. from an older app version) fall back to [default]. */
    private inline fun <reified E : Enum<E>> String?.toEnumOr(default: E): E =
        this?.let { saved -> enumValues<E>().firstOrNull { it.name == saved } } ?: default

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val READER_SCROLL_MODE = stringPreferencesKey("reader_scroll_mode")
        val READER_KEEP_SCREEN_ON = booleanPreferencesKey("reader_keep_screen_on")
        val READER_PAGE_BACKGROUND = stringPreferencesKey("reader_page_background")
    }
}
