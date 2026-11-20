package com.vegamstories.app.presentation

import com.vegamstories.app.domain.model.Bookmark
import com.vegamstories.app.domain.model.StoryFormat
import com.vegamstories.app.presentation.navigation.ReaderRoute
import com.vegamstories.app.presentation.navigation.toReaderRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Opening a bookmark: the reader route carries its place, and opens the story there. */
class ReaderRouteTest {

    @Test
    fun `a bookmark goes to the reader and comes back the same`() {
        val comic = Bookmark("moral-1", "te", StoryFormat.COMIC, position = 4)
        val text = Bookmark("moral-1", "en", StoryFormat.TEXT, position = 3, offset = 120)
        assertEquals(comic, comic.toReaderRoute().bookmark)
        assertEquals(text, text.toReaderRoute().bookmark)
    }

    @Test
    fun `a story opened from a list has no bookmark, and opens where the reader left off`() {
        assertNull(ReaderRoute("moral-1").bookmark)
        assertNull(ReaderRoute("moral-1", bookmarkFormat = "SOMETHING_NEW", bookmarkLanguage = "te").bookmark)
    }
}
