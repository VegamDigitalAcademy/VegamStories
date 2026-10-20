package com.vegam.myapplication.data.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.vegam.myapplication.domain.model.AppLanguage
import com.vegam.myapplication.domain.repository.LanguageRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-app language via AppCompat:
 *  - Android 13+: stored by the system (also editable in Settings → Apps → Vegam Stories → Language).
 *  - Android 7–12: stored by AppCompat itself (autoStoreLocales in the manifest).
 * Changing it recreates the screen so every text is reloaded in the new language.
 */
@Singleton
class AppCompatLanguageRepository @Inject constructor() : LanguageRepository {

    override fun getLanguage(): AppLanguage {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) AppLanguage.SYSTEM else AppLanguage.fromTag(locales[0]?.language)
    }

    override fun setLanguage(language: AppLanguage) {
        val locales = language.languageTag
            ?.let { LocaleListCompat.forLanguageTags(it) }
            ?: LocaleListCompat.getEmptyLocaleList() // empty = follow the phone
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
