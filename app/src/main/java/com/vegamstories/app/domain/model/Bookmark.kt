package com.vegamstories.app.domain.model

/**
 * A page the reader marked with the ribbon, in one language version of a story. A comic page is
 * marked by its number ([position]); text by where the page starts in it (paragraph [position],
 * character [offset]), because text pages change with the text size and the screen.
 */
data class Bookmark(
    val storyId: String,
    val language: String,
    val format: StoryFormat,
    val position: Int,
    val offset: Int = 0,
)

/** A bookmark in the library, with its story (in the bookmark's language) and what the page shows. */
data class BookmarkItem(
    val bookmark: Bookmark,
    val story: Story,
    /** The marked comic page's picture. */
    val pageImageUrl: String? = null,
    /** The first words of the marked text page. */
    val snippet: String? = null,
)
