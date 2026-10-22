package com.vegamstories.app.domain

import com.vegamstories.app.data.mapper.toTextEntities
import com.vegamstories.app.data.remote.dto.StoryDto
import com.vegamstories.app.data.remote.dto.TextDto
import com.vegamstories.app.domain.model.StoryFormat
import com.vegamstories.app.domain.model.StoryText
import com.vegamstories.app.domain.model.TextLanguage
import com.vegamstories.app.domain.model.pick
import com.vegamstories.app.testutil.testStory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoryFormatsTest {

    @Test
    fun `a story has the formats it has content for, in the order comic, text, video`() {
        assertEquals(
            listOf(StoryFormat.COMIC, StoryFormat.TEXT, StoryFormat.VIDEO),
            testStory(hasText = true, withVideo = true).formats.toList(),
        )
        assertEquals(setOf(StoryFormat.COMIC), testStory().formats)
        assertEquals(setOf(StoryFormat.TEXT), testStory(pageCount = 0, hasText = true).formats)
        assertEquals(setOf(StoryFormat.VIDEO), testStory(pageCount = 0, withVideo = true).formats)
    }

    @Test
    fun `the text in the preferred language is picked`() {
        val texts = listOf(telugu, english)
        assertEquals(english, texts.pick(TextLanguage.ENGLISH))
        assertEquals(telugu, texts.pick(TextLanguage.TELUGU))
    }

    @Test
    fun `without the preferred language Telugu is used, then anything`() {
        assertEquals(telugu, listOf(english, telugu).pick(preferred = null))
        assertEquals(english, listOf(english).pick(TextLanguage.TELUGU))
        assertNull(emptyList<StoryText>().pick(TextLanguage.ENGLISH))
    }

    @Test
    fun `paragraphs are split on blank lines and trimmed`() {
        val text = StoryText(TextLanguage.ENGLISH, "  First line\nstill first. \n\n\nSecond.\n   \nThird.\n")
        assertEquals(listOf("First line\nstill first.", "Second.", "Third."), text.paragraphs)
    }

    @Test
    fun `texts in unknown languages, empty texts and duplicates are not stored`() {
        val dto = StoryDto(
            id = "moral-1", categoryId = "moral", episode = 1, title = "T", coverUrl = "c", publishedAt = 0L,
            texts = listOf(
                TextDto("te", "కథ"),
                TextDto("hi", "कहानी"),
                TextDto("en", "   "),
                TextDto("te", "again"),
            ),
        )
        assertEquals(listOf("te" to "కథ"), dto.toTextEntities().map { it.language to it.body })
    }

    private val telugu = StoryText(TextLanguage.TELUGU, "కథ")
    private val english = StoryText(TextLanguage.ENGLISH, "Story")
}
