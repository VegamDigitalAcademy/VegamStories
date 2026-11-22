package com.vegamstories.app.domain.model

/** The shortest time between two checks the app makes by itself: opening it again sooner doesn't check again. */
const val CONTENT_CHECK_INTERVAL_MILLIS: Long = 5L * 60 * 1000

/**
 * Whether the app should check the story catalogue without being asked. A check reads one small
 * file (the catalogue's version) and downloads the catalogue only when it changed, so the app
 * checks whenever it opens, at most every few minutes; at once when nothing is saved to show (no
 * stories or no languages yet, as on first launch). A check the reader asks for (pull to refresh)
 * always runs. [lastRefreshAt] is when the last check succeeded (null: never), [now] the time now.
 */
fun isContentRefreshDue(hasContent: Boolean, lastRefreshAt: Long?, now: Long): Boolean = when {
    !hasContent -> true
    lastRefreshAt == null -> true
    now < lastRefreshAt -> true // the clock was turned back: no telling how old the stories are
    else -> now - lastRefreshAt >= CONTENT_CHECK_INTERVAL_MILLIS
}
