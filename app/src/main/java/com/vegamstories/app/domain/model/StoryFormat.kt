package com.vegamstories.app.domain.model

/** The ways one story can be enjoyed. A story has one or more of them (see [Story.formats]). */
enum class StoryFormat { COMIC, TEXT, VIDEO }

/**
 * The whole story as plain text in one language. Languages are managed on the server (admin
 * site), so nothing here is fixed: [languageCode] is e.g. "te", "en" or "or", and
 * [languageName] its own-script name from the server ("తెలుగు", "English", "ଓଡ଼ିଆ").
 */
data class StoryText(
    val languageCode: String,
    val languageName: String,
    val body: String,
) {
    /** Paragraphs, split on blank lines. */
    val paragraphs: List<String>
        get() = body.split(PARAGRAPH_BREAK).map { it.trim() }.filter { it.isNotEmpty() }

    private companion object {
        val PARAGRAPH_BREAK = Regex("""\n\s*\n""")
    }
}

/**
 * The text to show: the first of [preferredCodes] the story has (null entries are skipped),
 * else the first text, which is in the server's language order.
 */
fun List<StoryText>.pick(vararg preferredCodes: String?): StoryText? =
    preferredCodes.firstNotNullOfOrNull { code -> code?.let { c -> firstOrNull { it.languageCode == c } } }
        ?: firstOrNull()
