package com.vegamstories.app.domain

import com.vegamstories.app.data.local.entity.StoryEntity
import com.vegamstories.app.data.local.entity.StoryWithStateRow
import com.vegamstories.app.data.mapper.toDomain
import com.vegamstories.app.data.mapper.toNameEntities
import com.vegamstories.app.data.mapper.toPageEntities
import com.vegamstories.app.data.mapper.toTranslationEntities
import com.vegamstories.app.data.remote.dto.CategoryDto
import com.vegamstories.app.data.remote.dto.PageDto
import com.vegamstories.app.data.remote.dto.StoryDto
import com.vegamstories.app.data.remote.dto.StoryTranslationDto
import com.vegamstories.app.domain.model.Language
import com.vegamstories.app.domain.model.StoryFormat
import com.vegamstories.app.domain.model.StoryText
import com.vegamstories.app.domain.model.resolveContentLanguage
import com.vegamstories.app.testutil.testStory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Stories in several languages: formats, the language the app shows, and how server data is stored. */
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
    fun `paragraphs are split on blank lines and trimmed`() {
        val text = StoryText("en", "  First line\nstill first. \n\n\nSecond.\n   \nThird.\n")
        assertEquals(listOf("First line\nstill first.", "Second.", "Third."), text.paragraphs)
    }

    @Test
    fun `the language the user picked wins while the server still has it`() {
        assertEquals("or", resolveContentLanguage(saved = "or", deviceCode = "en", available = languages))
        // Odia removed on the server: back to the phone's language.
        assertEquals("en", resolveContentLanguage(saved = "or", deviceCode = "en", available = languages.take(2)))
    }

    @Test
    fun `without a pick the phone's language is used if stories exist in it, else the first language`() {
        assertEquals("en", resolveContentLanguage(saved = null, deviceCode = "en", available = languages))
        assertEquals("te", resolveContentLanguage(saved = null, deviceCode = "hi", available = languages))
    }

    @Test
    fun `before the language list loads the pick or the phone's language is used as is`() {
        assertEquals("or", resolveContentLanguage(saved = "or", deviceCode = "en", available = emptyList()))
        assertEquals("hi", resolveContentLanguage(saved = null, deviceCode = "hi", available = emptyList()))
        assertEquals("te", resolveContentLanguage(saved = null, deviceCode = null, available = emptyList()))
    }

    @Test
    fun `each language version keeps its own pages and text, blank versions are dropped`() {
        val dto = StoryDto(
            id = "moral-1", categoryId = "moral", episode = 1, publishedAt = 0L,
            translations = listOf(
                StoryTranslationDto("te", "కథ", coverUrl = "te.webp", pages = listOf(PageDto("te0.webp"), PageDto("te1.webp")), text = "కథ"),
                StoryTranslationDto("or", "କାହାଣୀ", coverUrl = "or.webp", pages = listOf(PageDto("or0.webp")), text = "  "),
                StoryTranslationDto("en", "   ", coverUrl = "en.webp", text = "Story"), // no title: dropped
                StoryTranslationDto("te", "again", coverUrl = "x.webp"), // second Telugu: dropped
            ),
        )
        val translations = dto.toTranslationEntities()
        assertEquals(listOf("te", "or"), translations.map { it.language })
        assertEquals(listOf(2, 1), translations.map { it.pageCount })
        assertEquals("కథ", translations[0].body)
        assertNull(translations[1].body) // blank text = no text version
        assertEquals(
            listOf("te" to "te0.webp", "te" to "te1.webp", "or" to "or0.webp"),
            dto.toPageEntities().map { it.language to it.imageUrl },
        )
    }

    @Test
    fun `category names are stored per language, blank ones skipped`() {
        val names = CategoryDto("moral", names = mapOf("te" to "నీతి కథలు", "en" to "Moral Stories", "or" to " ")).toNameEntities()
        assertEquals(listOf("te" to "నీతి కథలు", "en" to "Moral Stories"), names.map { it.language to it.name })
    }

    @Test
    fun `the cover is the cover image, else the first comic page, else none (title card)`() {
        assertEquals("cover.webp", row(cover = "cover.webp", firstPage = "p0.webp").toDomain().coverImageUrl)
        assertEquals("p0.webp", row(cover = null, firstPage = "p0.webp").toDomain().coverImageUrl)
        assertNull(row(cover = null, firstPage = null).toDomain().coverImageUrl)
    }

    @Test
    fun `a blank cover from the server counts as no cover`() {
        val dto = StoryDto(
            id = "moral-1", categoryId = "moral", episode = 1, publishedAt = 0L,
            translations = listOf(StoryTranslationDto("te", "కథ", coverUrl = " ", text = "కథ")),
        )
        assertNull(dto.toTranslationEntities().single().coverImageUrl)
    }

    private fun row(cover: String?, firstPage: String?) = StoryWithStateRow(
        story = StoryEntity(id = "moral-1", categoryId = "moral", episodeNumber = 1, publishedAt = 0L, likeCount = 0),
        language = "te", title = "కథ", summary = "", coverImageUrl = cover, firstPageUrl = firstPage,
        pageCount = if (firstPage == null) 0 else 1, hasText = true,
        videoTitle = null, videoDescription = null, videoThumbnailUrl = null, videoUrl = null,
        categoryName = "నీతి కథలు", isFavorite = false, isDownloaded = false,
        progressLastPageIndex = null, progressTotalPages = null, progressIsCompleted = null, progressUpdatedAt = null,
    )

    private val languages = listOf(
        Language("te", "Telugu", "తెలుగు"),
        Language("en", "English", "English"),
        Language("or", "Odia", "ଓଡ଼ିଆ"),
    )
}
