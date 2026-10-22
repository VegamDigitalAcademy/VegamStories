package com.vegamstories.app.domain.model

/** The ways one story can be enjoyed. A story has one or more of them (see [Story.formats]). */
enum class StoryFormat { COMIC, TEXT, VIDEO }

/** The whole story as plain text, in the language version [languageCode] (e.g. "te", "or"). */
data class StoryText(
    val languageCode: String,
    val body: String,
) {
    /** Paragraphs, split on blank lines. */
    val paragraphs: List<String>
        get() = body.split(PARAGRAPH_BREAK).map { it.trim() }.filter { it.isNotEmpty() }

    private companion object {
        val PARAGRAPH_BREAK = Regex("""\n\s*\n""")
    }
}
