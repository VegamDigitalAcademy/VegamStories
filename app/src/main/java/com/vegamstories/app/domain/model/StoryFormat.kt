package com.vegamstories.app.domain.model

/** The ways one story can be enjoyed. A story has one or more of them (see [Story.formats]). */
enum class StoryFormat { COMIC, TEXT, VIDEO }

/** Languages a story's text can be in. [code] is what the server uses ("te", "en"). */
enum class TextLanguage(val code: String) {
    TELUGU("te"),
    ENGLISH("en"),
    ;

    companion object {
        fun fromCode(code: String): TextLanguage? = entries.firstOrNull { it.code == code }
    }
}

/** The whole story as plain text in one language. */
data class StoryText(
    val language: TextLanguage,
    val body: String,
) {
    /** Paragraphs, split on blank lines. */
    val paragraphs: List<String>
        get() = body.split(PARAGRAPH_BREAK).map { it.trim() }.filter { it.isNotEmpty() }

    private companion object {
        val PARAGRAPH_BREAK = Regex("""\n\s*\n""")
    }
}

/** The text to show: in [preferred] (e.g. the app language) if there is one, else Telugu, else any. */
fun List<StoryText>.pick(preferred: TextLanguage?): StoryText? =
    firstOrNull { it.language == preferred }
        ?: firstOrNull { it.language == TextLanguage.TELUGU }
        ?: firstOrNull()
