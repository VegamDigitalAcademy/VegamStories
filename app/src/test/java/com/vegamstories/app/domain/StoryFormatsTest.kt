package com.vegamstories.app.domain

import com.vegamstories.app.data.mapper.toTextEntities
import com.vegamstories.app.data.remote.dto.StoryDto
import com.vegamstories.app.data.remote.dto.TextDto
import com.vegamstories.app.domain.model.StoryFormat
import com.vegamstories.app.domain.model.StoryText
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
    fun `the first preferred language the story has is picked`() {
        val texts = listOf(telugu, english, odia)
        assertEquals(odia, texts.pick("or", "en"))
        assertEquals(english, texts.pick(null, "hi", "en"))
    }

    @Test
    fun `without a match the first text in server order is used`() {
        assertEquals(english, listOf(english, odia).pick("te"))
        assertEquals(telugu, listOf(telugu, english).pick())
        assertNull(emptyList<StoryText>().pick("en"))
    }

    @Test
    fun `paragraphs are split on blank lines and trimmed`() {
        val text = StoryText("en", "English", "  First line\nstill first. \n\n\nSecond.\n   \nThird.\n")
        assertEquals(listOf("First line\nstill first.", "Second.", "Third."), text.paragraphs)
    }

    @Test
    fun `any language code is stored, but not empty texts or duplicates`() {
        val dto = StoryDto(
            id = "moral-1", categoryId = "moral", episode = 1, title = "T", coverUrl = "c", publishedAt = 0L,
            texts = listOf(
                TextDto("te", "కథ"),
                TextDto("or", "କାହାଣୀ"),
                TextDto("en", "   "),
                TextDto("te", "again"),
            ),
        )
        assertEquals(listOf("te" to "కథ", "or" to "କାହାଣୀ"), dto.toTextEntities().map { it.language to it.body })
    }

    private val telugu = StoryText("te", "తెలుగు", "కథ")
    private val english = StoryText("en", "English", "Story")
    private val odia = StoryText("or", "ଓଡ଼ିଆ", "କାହାଣୀ")
}
