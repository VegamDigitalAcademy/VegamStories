package com.vegamstories.app.domain.repository

import com.vegamstories.app.domain.model.AppLanguage

/** The app's own UI language (independent of the phone's language). */
interface LanguageRepository {
    fun getLanguage(): AppLanguage

    /** Applies immediately: open screens are redrawn in the new language. */
    fun setLanguage(language: AppLanguage)
}
