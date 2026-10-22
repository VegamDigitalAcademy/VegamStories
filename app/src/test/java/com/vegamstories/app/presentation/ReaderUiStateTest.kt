package com.vegamstories.app.presentation

import com.vegamstories.app.domain.model.ReaderSettings
import com.vegamstories.app.domain.model.StoryDetail
import com.vegamstories.app.domain.model.StoryFormat
import com.vegamstories.app.domain.model.StoryText
import com.vegamstories.app.domain.model.StoryPage
import com.vegamstories.app.presentation.reader.ReaderUiState
import com.vegamstories.app.testutil.testStory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The reader: comic pages then "Did you like this story?"; the video and text have their own tabs. */
class ReaderUiStateTest {

    private fun detail(pages: Int, withVideo: Boolean) = StoryDetail(
        story = testStory(withVideo = withVideo),
        pages = List(pages) { StoryPage(index = it, imageUrl = "https://example.com/p$it.webp") },
    )

    @Test
    fun `while loading there are no pages`() {
        val state = ReaderUiState()
        assertEquals(0, state.totalPagerPages)
        assertEquals(0, state.likePageIndex)
        assertFalse(state.hasVideo)
    }

    @Test
    fun `the like page comes right after the comic pages`() {
        val state = ReaderUiState(isLoading = false, detail = detail(pages = 3, withVideo = false))
        assertEquals(3, state.likePageIndex)
        assertEquals(4, state.totalPagerPages) // 3 comic + like
        assertFalse(state.hasVideo)
    }

    @Test
    fun `a video has its own tab, not a page after the comic`() {
        val state = ReaderUiState(isLoading = false, detail = detail(pages = 3, withVideo = true))
        assertEquals(3, state.likePageIndex)
        assertEquals(4, state.totalPagerPages) // 3 comic + like
        assertTrue(state.hasVideo)
        assertEquals(setOf(StoryFormat.COMIC, StoryFormat.VIDEO), state.formats)
    }

    @Test
    fun `opens in the saved favourite format when the story has it, else the first it has`() {
        val withVideo = detail(pages = 3, withVideo = true)
        val watch = ReaderSettings(preferredFormat = StoryFormat.VIDEO)
        assertEquals(StoryFormat.VIDEO, ReaderUiState(isLoading = false, detail = withVideo, settings = watch).format)
        val read = ReaderSettings(preferredFormat = StoryFormat.TEXT) // this story has no text
        assertEquals(StoryFormat.COMIC, ReaderUiState(isLoading = false, detail = withVideo, settings = read).format)
    }

    @Test
    fun `a tab picked in the reader wins over the saved favourite`() {
        val state = ReaderUiState(
            isLoading = false,
            detail = detail(pages = 3, withVideo = true),
            settings = ReaderSettings(preferredFormat = StoryFormat.VIDEO),
            selectedFormat = StoryFormat.COMIC,
        )
        assertEquals(StoryFormat.COMIC, state.format)
    }

    @Test
    fun `text follows the language picked here, then the one picked last time, then the app language`() {
        val texts = listOf(StoryText("te", "తెలుగు", "కథ"), StoryText("en", "English", "Story"), StoryText("or", "ଓଡ଼ିଆ", "କାହାଣୀ"))
        val detail = detail(pages = 0, withVideo = false).copy(texts = texts)
        val state = ReaderUiState(isLoading = false, detail = detail, appTextLanguage = "en")
        assertEquals("en", state.text?.languageCode)
        assertEquals("or", state.copy(settings = ReaderSettings(textLanguage = "or")).text?.languageCode)
        assertEquals(
            "te",
            state.copy(settings = ReaderSettings(textLanguage = "or"), selectedTextLanguage = "te").text?.languageCode,
        )
        // An app language the story doesn't have (Hindi): the first language in server order.
        assertEquals("te", state.copy(appTextLanguage = "hi").text?.languageCode)
    }
}
