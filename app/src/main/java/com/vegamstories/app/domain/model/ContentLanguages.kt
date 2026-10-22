package com.vegamstories.app.domain.model

/** The languages stories are available in (server order) and the one the app is showing. */
data class ContentLanguages(
    val all: List<Language>,
    val selected: String,
    /** The user has picked a story language at least once (first-launch screen or Home's button). */
    val isChosen: Boolean = false,
) {
    val selectedLanguage: Language? get() = all.firstOrNull { it.code == selected }
}

/**
 * Which language to show stories in: the user's pick ([saved]) while the server still has it,
 * else the app's/phone's language ([deviceCode]) if stories exist in it, else the server's first
 * language. Before the language list has loaded, the pick (or the device language) is used as is.
 */
fun resolveContentLanguage(saved: String?, deviceCode: String?, available: List<Language>): String {
    val codes = available.map { it.code }
    return when {
        saved != null && (codes.isEmpty() || saved in codes) -> saved
        deviceCode != null && deviceCode in codes -> deviceCode
        codes.isNotEmpty() -> codes.first()
        else -> deviceCode ?: DEFAULT_CONTENT_LANGUAGE
    }
}

/** Used only before the app has ever loaded the language list and the phone's language is unknown. */
const val DEFAULT_CONTENT_LANGUAGE = "te"
