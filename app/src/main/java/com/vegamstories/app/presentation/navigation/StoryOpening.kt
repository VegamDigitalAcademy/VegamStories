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

    /** The story cards on the screen now, by story: where each one's picture is. */
    private val cards = mutableMapOf<String, CardSpot>()

    fun cardPlaced(storyId: String, spot: CardSpot) {
        cards[storyId] = spot
    }

    fun cardGone(storyId: String) {
        cards.remove(storyId)
    }

    fun cardSpot(storyId: String): CardSpot? = cards[storyId]

    /** Back from a story: the reader shrinking into its card, if it's on the screen behind. */
    var closing by mutableStateOf<StoryClosing?>(null)
        private set

    /** Story [storyId] is being closed from a reader at [from] on the screen. */
    fun closing(storyId: String, from: Rect) {
        closing = StoryClosing(storyId, from)
    }

    fun closed(closing: StoryClosing) {
        if (this.closing === closing) this.closing = null
    }
}

/** A story card on the screen: its picture's place, and the picture. */
class CardSpot(val bounds: Rect, val coverUrl: String?)

/** One story closing: the reader's picture shrinking [from] the reader into its card ([to], once found). */
@Stable
class StoryClosing(val storyId: String, val from: Rect) {
    /** 0 the whole reader … 1 the card. */
    val progress = Animatable(0f)

    var to by mutableStateOf<CardSpot?>(null)
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

/** How long a card's picture takes to grow into the reader's cover, and to shrink back on Back. */
const val STORY_OPEN_MS = 450
const val STORY_CLOSE_MS = 420

/** A quick, smooth start that settles gently into place. */
val StoryOpenEasing = CubicBezierEasing(0.3f, 0f, 0.2f, 1f)
