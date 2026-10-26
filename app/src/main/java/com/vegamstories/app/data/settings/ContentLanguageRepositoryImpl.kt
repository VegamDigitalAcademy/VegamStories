package com.vegamstories.app.data.settings

import kotlinx.coroutines.flow.onEach
import com.vegamstories.app.core.logging.AppLogger
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vegamstories.app.data.local.dao.StoryDao
import com.vegamstories.app.data.mapper.toDomain
import com.vegamstories.app.domain.model.ContentLanguages
import com.vegamstories.app.domain.model.resolveContentLanguage
import com.vegamstories.app.domain.repository.ContentLanguageRepository
import com.vegamstories.app.domain.repository.LanguageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ContentLanguageRepositoryImpl"

@Singleton
class ContentLanguageRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val storyDao: StoryDao,
    private val appLanguage: LanguageRepository,
) : ContentLanguageRepository {

    private val log = AppLogger(TAG)
    @Volatile private var lastLogged: ContentLanguages? = null

    private val saved: Flow<String?> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[CONTENT_LANGUAGE] }
        .distinctUntilChanged()

    override val contentLanguages: Flow<ContentLanguages> =
        combine(storyDao.observeLanguages(), saved) { languages, picked ->
            val all = languages.map { it.toDomain() }
            ContentLanguages(
                all = all,
                selected = resolveContentLanguage(picked, deviceLanguage(), all),
                isChosen = picked != null,
            )
        }.distinctUntilChanged()
            .onEach { languages ->
                // Several screens watch the language at once: log a change once, not once per screen.
                if (languages != lastLogged) {
                    lastLogged = languages
                    log.d("$TAG: Story language: ${languages.selected}", data = mapOf("chosen" to picked(languages), "in the app" to languages.all.map { it.code }))
                }
            }

    override val contentLanguage: Flow<String> = contentLanguages.map { it.selected }.distinctUntilChanged()

    override suspend fun setContentLanguage(code: String) {
        dataStore.edit { it[CONTENT_LANGUAGE] = code }
        log.i("$TAG: Story language set: $code")
    }

    private fun picked(languages: ContentLanguages): String = if (languages.isChosen) "by the user" else "not yet (from the phone's language)"

    /** The app's UI language from Settings, or the phone's language (e.g. "or" on an Odia phone). */
    private fun deviceLanguage(): String = appLanguage.getLanguage().languageTag ?: Locale.getDefault().language

    private companion object {
        val CONTENT_LANGUAGE = stringPreferencesKey("content_language")
    }
}
