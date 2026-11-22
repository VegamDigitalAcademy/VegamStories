package com.vegamstories.app.domain

import com.vegamstories.app.domain.model.CONTENT_CHECK_INTERVAL_MILLIS
import com.vegamstories.app.domain.model.isContentRefreshDue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the app checks the story catalogue by itself: when it opens, at most every few minutes, or when it has none. */
class ContentRefreshTest {

    private val minute = 60 * 1000L
    private val hour = 60 * minute
    private val now = 1_000 * hour

    @Test
    fun `nothing saved yet downloads straight away`() {
        assertTrue(isContentRefreshDue(hasContent = false, lastRefreshAt = null, now = now))
        // Even right after a download, if it left nothing (no stories or no languages).
        assertTrue(isContentRefreshDue(hasContent = false, lastRefreshAt = now - 1, now = now))
    }

    @Test
    fun `opening the app again within minutes doesn't check again`() {
        assertFalse(isContentRefreshDue(hasContent = true, lastRefreshAt = now - 10_000, now = now))
        assertFalse(isContentRefreshDue(hasContent = true, lastRefreshAt = now - 4 * minute, now = now))
    }

    @Test
    fun `a few minutes after the last check, opening the app checks`() {
        assertTrue(isContentRefreshDue(hasContent = true, lastRefreshAt = now - CONTENT_CHECK_INTERVAL_MILLIS, now = now))
        assertTrue(isContentRefreshDue(hasContent = true, lastRefreshAt = now - 30 * hour, now = now))
    }

    @Test
    fun `saved stories with no record of a download, or a clock turned back, download`() {
        assertTrue(isContentRefreshDue(hasContent = true, lastRefreshAt = null, now = now))
        assertTrue(isContentRefreshDue(hasContent = true, lastRefreshAt = now + hour, now = now))
    }
}
