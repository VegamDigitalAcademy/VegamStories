package com.vegamstories.app.domain.repository

import com.vegamstories.app.domain.model.ContentLanguages
import kotlinx.coroutines.flow.Flow

/**
 * The language stories are shown in (Home, Search, the reader), chosen with the language button
 * on Home. Separate from the app's own UI language ([LanguageRepository]).
 */
interface ContentLanguageRepository {
    /** The server's languages and the one in use; changes when either changes. */
    val contentLanguages: Flow<ContentLanguages>

    /** The code in use, e.g. "te". */
    val contentLanguage: Flow<String>

    suspend fun setContentLanguage(code: String)
}
