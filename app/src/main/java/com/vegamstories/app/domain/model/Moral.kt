package com.vegamstories.app.domain.model

/** How a story's text labels its moral, in the languages stories come in (compared ignoring case). */
private val MORAL_LABELS = setOf("moral of the story", "moral", "నీతి", "ନୀତି", "नीति", "सीख")

/** Characters that can end the label: "Moral: …", "నీతి - …". */
private val LABEL_ENDS = charArrayOf(':', '-', '–', '—', '：')

/**
 * The story's moral: the text after the label of a paragraph like "Moral: Together, any difficulty
 * becomes easy." or "నీతి: …", looked for near the end of the text, where stories put it. Null if
 * the text has none.
 */
fun findMoral(paragraphs: List<String>): String? =
    paragraphs.takeLast(3).asReversed().firstNotNullOfOrNull { paragraph ->
        val text = paragraph.trim()
        val labelEnd = text.indexOfAny(LABEL_ENDS)
        if (labelEnd <= 0) return@firstNotNullOfOrNull null
        val label = text.substring(0, labelEnd).trim().lowercase()
        if (label !in MORAL_LABELS) return@firstNotNullOfOrNull null
        text.substring(labelEnd + 1).trim().takeIf { it.isNotEmpty() }
    }
