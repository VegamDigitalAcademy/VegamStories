package com.vegamstories.app.presentation.reader

/**
 * Comic pages two at a time, as an open book (wide screens on their side). The pager turns
 * spreads: spread s shows pages 2s and 2s + 1, the last one may hold a single page, and the like
 * page comes after them all, on its own. Progress, bookmarks and the bar below still count comic
 * pages, so a story read on a tablet carries on where it was on the phone, and back.
 */
data class ComicSpreads(val comicPageCount: Int) {
    /** Spreads of comic pages; the like page is the one after them. */
    val spreadCount: Int get() = (comicPageCount + 1) / 2
    val likeSpread: Int get() = spreadCount
    /** Everything the pager turns: the spreads and the like page. */
    val pagerPageCount: Int get() = spreadCount + 1

    /** The spread a page is on; the like page ([comicPageCount]) is on [likeSpread]. */
    fun spreadOf(page: Int): Int = if (page >= comicPageCount) likeSpread else page.coerceAtLeast(0) / 2

    /** The comic pages on [spread], left to right; none on the like page. */
    fun pagesOn(spread: Int): IntRange =
        if (spread >= spreadCount) IntRange.EMPTY else 2 * spread until minOf(2 * spread + 2, comicPageCount)

    /** How far the reader has got on [spread]: its last page (both are in view), or the like page. */
    fun readTo(spread: Int): Int = if (spread >= spreadCount) comicPageCount else pagesOn(spread).last
}
