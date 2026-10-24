package com.vegamstories.app.domain.model

/** How old the saved stories may get before the app downloads them again by itself. */
const val CONTENT_MAX_AGE_MILLIS: Long = 24L * 60 * 60 * 1000

/**
 * Whether the app should download the story catalogue without being asked. Server calls cost
 * money, so at most once a day, unless there is nothing saved to show (no stories or no languages
 * yet, as on first launch). A refresh the reader asks for (pull to refresh) always downloads.
 * [lastRefreshAt] is when the last download succeeded (null: never), [now] the time now.
 */
fun isContentRefreshDue(hasContent: Boolean, lastRefreshAt: Long?, now: Long): Boolean = when {
    !hasContent -> true
    lastRefreshAt == null -> true
    now < lastRefreshAt -> true // the clock was turned back: no telling how old the stories are
    else -> now - lastRefreshAt >= CONTENT_MAX_AGE_MILLIS
}
