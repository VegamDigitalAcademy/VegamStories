package com.vegamstories.app.domain.model

/** App UI language. [SYSTEM] (the default) follows the phone's language. */
enum class AppLanguage(val languageTag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    TELUGU("te"),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.languageTag != null && tag?.startsWith(it.languageTag) == true } ?: SYSTEM
    }
}
