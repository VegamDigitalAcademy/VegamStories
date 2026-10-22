package com.vegamstories.app.domain.model

/** The newest story the user has already been told about, or has seen in the app. */
data class SeenStory(val id: String, val publishedAt: Long)

/** What to do about the newest story after a refresh. */
sealed interface NewStoryDecision {
    /** Nothing new. */
    data object NoChange : NewStoryDecision

    /** Remember [story] as seen, without a notification. */
    data class MarkSeen(val story: Story) : NewStoryDecision

    /** Remember [story] as seen and tell the user about it. */
    data class Announce(val story: Story) : NewStoryDecision
}

/**
 * Decides whether [newest] deserves a "New story" notification.
 *
 * Rules:
 *  - First run (nothing seen yet): only remember it. Installing the app must not trigger an alert.
 *  - Same story as last time: nothing to do.
 *  - Not newer than the one seen (e.g. that one was unpublished): just remember it.
 *  - Already opened (it has reading progress): the user knows about it, just remember it.
 *  - [canAnnounce] false (alerts switched off, or the app is open): just remember it, so turning
 *    alerts on later doesn't announce a story from days ago.
 *  - Otherwise: announce it.
 */
fun decideNewStoryAlert(newest: Story?, lastSeen: SeenStory?, canAnnounce: Boolean): NewStoryDecision = when {
    newest == null -> NewStoryDecision.NoChange
    lastSeen == null -> NewStoryDecision.MarkSeen(newest)
    newest.id == lastSeen.id -> NewStoryDecision.NoChange
    newest.publishedAt <= lastSeen.publishedAt -> NewStoryDecision.MarkSeen(newest)
    newest.progress != null -> NewStoryDecision.MarkSeen(newest)
    !canAnnounce -> NewStoryDecision.MarkSeen(newest)
    else -> NewStoryDecision.Announce(newest)
}
