package com.vegamstories.app.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vegamstories.app.domain.model.Story
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A story card's cover under the finger (see [StoryCard]): how far it leans towards the finger
 * each way, how high it's lifted, and how far its cover is open on the first page.
 */
@Stable
internal class CoverPress(private val scope: CoroutineScope) {
    /** Where the finger is across the cover, -1 (left) … 1 (right). */
    val leanX = Animatable(0f)

    /** Where the finger is down the cover, -1 (top) … 1 (bottom). */
    val leanY = Animatable(0f)

    /** 0 lying flat … 1 lifted off the page. */
    val lift = Animatable(0f)

    /** 0 shut … 1 open on the first page. */
    val peek = Animatable(0f)

    /** Held long enough to lean: from now the first page is there under the cover (and loading). */
    var touched by mutableStateOf(false)
        private set

    /** Open (or opening): held long enough. The hold then has the finger to itself until it lifts. */
    var isOpen = false
        private set

    private var fingerDown = false
    private var autoClose: Job? = null

    /** The finger at [at] on a cover [width] wide and [height] tall: lean towards it and lift. */
    fun follow(at: Offset, width: Float, height: Float) {
        touched = true
        fingerDown = true
        val x = ((at.x / width).coerceIn(0f, 1f) - 0.5f) * 2f
        val y = ((at.y / height).coerceIn(0f, 1f) - 0.5f) * 2f
        scope.launch { leanX.animateTo(x, LeanSpring) }
        scope.launch { leanY.animateTo(y, LeanSpring) }
        scope.launch { lift.animateTo(1f, LeanSpring) }
    }

    /** Held: the cover opens on the first page. Without a finger (TalkBack), it closes again by itself. */
    fun open() {
        touched = true
        isOpen = true
        autoClose?.cancel()
        scope.launch { peek.animateTo(1f, OpenSpring) }
        if (!fingerDown) {
            autoClose = scope.launch {
                delay(PEEK_WITHOUT_FINGER_MILLIS)
                release()
            }
        }
    }

    /** Let go (or the list scrolled): the cover closes and the card settles back. */
    fun release() {
        fingerDown = false
        isOpen = false
        scope.launch { leanX.animateTo(0f, SettleSpring) }
        scope.launch { leanY.animateTo(0f, SettleSpring) }
        scope.launch { lift.animateTo(0f, SettleSpring) }
        scope.launch { peek.animateTo(0f, SettleSpring) }
    }
}

@Composable
internal fun rememberCoverPress(): CoverPress {
    val scope = rememberCoroutineScope()
    return remember { CoverPress(scope) }
}

/**
 * Watches the finger on a card (without taking its taps, holds or the list's scrolling from it):
 * held still a moment, the cover leans towards it and follows it; let go, or the list scrolls, and
 * it settles. The moment first means a finger that's about to scroll the list doesn't tilt every
 * card it lands on. [coverHeight] is the cover's share of the card, from the top (the title's below).
 */
internal fun Modifier.coverPress(press: CoverPress, coverHeight: (width: Float) -> Float): Modifier =
    pointerInput(press) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val endedEarly = withTimeoutOrNull(LEAN_AFTER_MILLIS) { awaitEnd(down, press) {} }
            if (endedEarly != null) return@awaitEachGesture
            val width = size.width.toFloat()
            val height = coverHeight(width)
            try {
                press.follow(down.position, width, height)
                awaitEnd(down, press) { at -> press.follow(at, width, height) }
            } finally {
                press.release()
            }
        }
    }

/**
 * Follows [down]'s finger ([onMove]) until it lifts, or the list takes it to scroll. Once the
 * cover's open the hold has the finger to itself (it takes every move): only lifting it ends it.
 */
private suspend fun AwaitPointerEventScope.awaitEnd(down: PointerInputChange, press: CoverPress, onMove: (Offset) -> Unit) {
    while (true) {
        val change = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull { it.id == down.id } ?: return
        if (!change.pressed) return
        if (change.isConsumed && change.positionChanged() && !press.isOpen) return
        onMove(change.position)
    }
}

/** The card leaning towards the finger, lifted with a shadow under it. */
internal fun GraphicsLayerScope.leanAndLift(press: CoverPress) {
    cameraDistance = CAMERA_DISTANCE * density
    // The side under the finger goes down, as if pressed.
    rotationX = -press.leanY.value * MAX_LEAN_DEGREES
    rotationY = press.leanX.value * MAX_LEAN_DEGREES
    val scale = 1f + LIFT_SCALE * press.lift.value
    scaleX = scale
    scaleY = scale
    shadowElevation = LiftShadow.toPx() * press.lift.value
    shape = CardShape
    clip = false
}

/** The cover swinging open from its left edge, like a book's. */
internal fun GraphicsLayerScope.coverOpening(press: CoverPress) {
    cameraDistance = CAMERA_DISTANCE * density
    transformOrigin = TransformOrigin(0f, 0.5f)
    rotationY = -OPEN_DEGREES * press.peek.value
    shadowElevation = OpenShadow.toPx() * press.peek.value
    shape = CardShape
    clip = true
}

/** The open cover facing away from the light: a little darker the further it opens. */
internal fun Modifier.coverShade(press: CoverPress): Modifier = drawWithContent {
    drawContent()
    val p = press.peek.value
    if (p > 0.01f) drawRect(Color.Black.copy(alpha = 0.25f * p))
}

/**
 * The story's first page, under its cover: the top of its first comic page (or its video's picture),
 * else its summary on paper. The open cover's shadow falls across it from the spine.
 */
@Composable
internal fun BoxScope.FirstPage(story: Story, press: CoverPress) {
    val picture = story.firstPageUrl ?: story.video?.thumbnailUrl
    Box(
        Modifier
            .matchParentSize()
            .testTag(FIRST_PAGE_TAG)
            .clip(CardShape)
            .background(PagePaper)
            .drawWithContent {
                drawContent()
                val p = press.peek.value
                if (p > 0.01f) {
                    drawRect(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = 0.4f * p),
                            0.45f to Color.Transparent,
                            endX = size.width,
                        ),
                    )
                }
            },
    ) {
        if (picture != null) {
            AsyncImage(
                model = picture,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Text(
                text = story.summary.ifBlank { story.title },
                color = PageInk,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 10.dp),
            )
        }
    }
}

internal val CardShape = RoundedCornerShape(12.dp)

/** Marks the first page under a held card's cover, for tests. */
internal const val FIRST_PAGE_TAG = "first_page"

/** A finger held this long leans the card (shorter is a tap or the start of a scroll). */
private const val LEAN_AFTER_MILLIS = 90L

/** How far the card leans at most, how much it grows and how high its shadow is as it lifts. */
private const val MAX_LEAN_DEGREES = 8f
private const val LIFT_SCALE = 0.05f
private val LiftShadow = 18.dp

/** How far the cover opens, and its shadow. */
private const val OPEN_DEGREES = 56f
private val OpenShadow = 8.dp

/** Far enough that the lean and the cover's swing look like a card in the hand, not a fisheye. */
private const val CAMERA_DISTANCE = 14f

/** Opened from TalkBack (no finger to lift), the cover closes again after this long. */
private const val PEEK_WITHOUT_FINGER_MILLIS = 2_500L

private val LeanSpring = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
private val OpenSpring = spring<Float>(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow)
private val SettleSpring = spring<Float>(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)

/** The first page's paper and writing, when it's words. */
private val PagePaper = Color(0xFFFFF8EC)
private val PageInk = Color(0xFF3A2C1E)
