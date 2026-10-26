package com.vegamstories.app.data.settings

import com.vegamstories.app.core.logging.logTime
import com.vegamstories.app.core.logging.AppLogger
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vegamstories.app.domain.model.AUTO_TURN_SECONDS_OPTIONS
import com.vegamstories.app.domain.model.PageBackground
import com.vegamstories.app.domain.model.ReaderScrollMode
import com.vegamstories.app.domain.model.ReaderSettings
import com.vegamstories.app.domain.model.SeenStory
import com.vegamstories.app.domain.model.ThemeMode
import com.vegamstories.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SettingsRepositoryImpl"

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private val log = AppLogger(TAG)

    private val prefs: Flow<Preferences> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                log.w("$TAG: Settings file couldn't be read; using the defaults", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }

    override val themeMode: Flow<ThemeMode> = prefs
        // Nothing saved yet (first launch) → follow the phone.
        .map { it[THEME_MODE].toEnumOr(ThemeMode.SYSTEM) }
        .distinctUntilChanged()

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
        log.i("$TAG: Theme saved: $mode")
    }

    override val readerSettings: Flow<ReaderSettings> = prefs
        .map { it.toReaderSettings() }
        .distinctUntilChanged()

    override suspend fun updateReaderSettings(transform: (ReaderSettings) -> ReaderSettings) {
        dataStore.edit { prefs ->
            val before = prefs.toReaderSettings()
            val updated = transform(before)
            prefs.write(updated)
            if (updated != before) log.i("$TAG: Reader settings saved", data = updated)
        }
    }

    override val newStoryAlertsEnabled: Flow<Boolean> = prefs
        .map { it[NEW_STORY_ALERTS] ?: false }
        .distinctUntilChanged()

    override suspend fun setNewStoryAlertsEnabled(enabled: Boolean) {
        dataStore.edit { it[NEW_STORY_ALERTS] = enabled }
        log.i("$TAG: New story alerts ${if (enabled) "on" else "off"}")
    }

    override suspend fun getLastSeenStory(): SeenStory? {
        val p = prefs.first()
        val id = p[LAST_SEEN_STORY_ID] ?: return null
        return SeenStory(id = id, publishedAt = p[LAST_SEEN_STORY_PUBLISHED_AT] ?: 0L)
    }

    override suspend fun setLastSeenStory(story: SeenStory) {
        dataStore.edit {
            it[LAST_SEEN_STORY_ID] = story.id
            it[LAST_SEEN_STORY_PUBLISHED_AT] = story.publishedAt
        }
        log.d("$TAG: Newest story seen: ${story.id} (${logTime(story.publishedAt)})")
    }

    private fun Preferences.toReaderSettings(): ReaderSettings {
        val defaults = ReaderSettings()
        return ReaderSettings(
            scrollMode = this[READER_SCROLL_MODE].toEnumOr(defaults.scrollMode),
            keepScreenOn = this[READER_KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
            pageBackground = this[READER_PAGE_BACKGROUND].toEnumOr(defaults.pageBackground),
            preferredFormat = this[READER_PREFERRED_FORMAT].toEnumOr(defaults.preferredFormat),
            textSize = this[READER_TEXT_SIZE].toEnumOr(defaults.textSize),
            autoTurnPages = this[READER_AUTO_TURN] ?: defaults.autoTurnPages,
            pageTurnHintShown = this[READER_PAGE_TURN_HINT_SHOWN] ?: defaults.pageTurnHintShown,
            autoTurnSeconds = this[READER_AUTO_TURN_SECONDS]?.takeIf { it in AUTO_TURN_SECONDS_OPTIONS }
                ?: defaults.autoTurnSeconds,
        )
    }

    private fun MutablePreferences.write(settings: ReaderSettings) {
        this[READER_SCROLL_MODE] = settings.scrollMode.name
        this[READER_KEEP_SCREEN_ON] = settings.keepScreenOn
        this[READER_PAGE_BACKGROUND] = settings.pageBackground.name
        this[READER_PREFERRED_FORMAT] = settings.preferredFormat.name
        this[READER_TEXT_SIZE] = settings.textSize.name
        this[READER_AUTO_TURN] = settings.autoTurnPages
        this[READER_AUTO_TURN_SECONDS] = settings.autoTurnSeconds
        this[READER_PAGE_TURN_HINT_SHOWN] = settings.pageTurnHintShown
    }

    /** Unknown or missing names (e.g. from an older app version) fall back to [default]. */
    private inline fun <reified E : Enum<E>> String?.toEnumOr(default: E): E =
        this?.let { saved -> enumValues<E>().firstOrNull { it.name == saved } } ?: default

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val READER_SCROLL_MODE = stringPreferencesKey("reader_scroll_mode")
        val READER_KEEP_SCREEN_ON = booleanPreferencesKey("reader_keep_screen_on")
        val READER_PAGE_BACKGROUND = stringPreferencesKey("reader_page_background")
        val READER_PREFERRED_FORMAT = stringPreferencesKey("reader_preferred_format")
        val READER_TEXT_SIZE = stringPreferencesKey("reader_text_size")
        val READER_AUTO_TURN = booleanPreferencesKey("reader_auto_turn")
        val READER_AUTO_TURN_SECONDS = intPreferencesKey("reader_auto_turn_seconds")
        val READER_PAGE_TURN_HINT_SHOWN = booleanPreferencesKey("reader_page_turn_hint_shown")
        val NEW_STORY_ALERTS = booleanPreferencesKey("new_story_alerts")
        val LAST_SEEN_STORY_ID = stringPreferencesKey("last_seen_story_id")
        val LAST_SEEN_STORY_PUBLISHED_AT = longPreferencesKey("last_seen_story_published_at")
    }
}
