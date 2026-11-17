package com.vegamstories.app.presentation.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import com.vegamstories.app.domain.model.Story

/**
 * Opening a story from its card. The card's picture grows into the reader's cover on the screen
 * it is on, over the reader's bars fading in (StoryOpeningOverlay); only then does the reader
 * open, underneath, looking just the same, and the overlay goes. Nothing heavy is put together
 * while the picture moves, so it moves smoothly, even on a slow phone.
 */
@Stable
class StoryOpener {
    /** The story whose card was tapped last: the reader shows it while the story itself loads. */
    var story: Story? = null
        private set

    /** Where that card's picture was on the screen. */
    private var tappedAt: Rect? = null

    /** The opening under way, if any. */
    var opening by mutableStateOf<StoryOpening?>(null)
        private set

    /** A card is tapped: its [story], with its picture at [pictureBounds] on the screen. */
    fun tapped(story: Story, pictureBounds: Rect?) {
        this.story = story
        tappedAt = pictureBounds
    }

    /**
     * Opens story [storyId] with [navigate]: after growing its card, if that card was just tapped
     * and the phone [animates]; else straight away.
     */
    fun open(storyId: String, animates: Boolean, navigate: () -> Unit) {
        val story = story?.takeIf { it.id == storyId }
        val from = tappedAt
        tappedAt = null
        if (story == null || from == null || !animates || opening != null) {
            navigate()
            return
        }
        opening = StoryOpening(story, from, navigate)
    }

    /** The reader for [storyId] is on the screen, under the overlay: the overlay can go. */
    fun arrived(storyId: String) {
        val current = opening ?: return
        if (current.story.id == storyId && current.navigated) opening = null
    }

    /** Ends [opening] whatever happened (the reader never said it arrived). */
    fun done(opening: StoryOpening) {
        if (this.opening === opening) this.opening = null
    }
}

/** One story opening: the picture moving [from] its card [to] the reader's cover. */
@Stable
class StoryOpening(val story: Story, val from: Rect, val navigate: () -> Unit) {
    /** 0 on the card … 1 as the reader's cover. */
    val progress = Animatable(0f)

    /** Where the cover will be in the reader, once laid out. */
    var to by mutableStateOf<Rect?>(null)

    /** The reader has been opened, under the overlay. */
    var navigated by mutableStateOf(false)
}

val LocalStoryOpener = staticCompositionLocalOf { StoryOpener() }

/** How long a card's picture takes to grow into the reader's cover. */
const val STORY_OPEN_MS = 450

/** A quick, smooth start that settles gently into place. */
val StoryOpenEasing = CubicBezierEasing(0.3f, 0f, 0.2f, 1f)
