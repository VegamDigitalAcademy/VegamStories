package com.vegamstories.app.presentation

import com.vegamstories.app.domain.model.StoryDetail
import com.vegamstories.app.domain.model.StoryPage
import com.vegamstories.app.presentation.reader.ReaderUiState
import com.vegamstories.app.testutil.testStory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The reader's pages: comic pages, then "Did you like this story?", then the video if there is one. */
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
    fun `a video adds one more page after the like page`() {
        val state = ReaderUiState(isLoading = false, detail = detail(pages = 3, withVideo = true))
        assertEquals(3, state.likePageIndex)
        assertEquals(5, state.totalPagerPages) // 3 comic + like + video
        assertTrue(state.hasVideo)
    }
}
