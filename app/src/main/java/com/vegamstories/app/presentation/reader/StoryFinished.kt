package com.vegamstories.app.presentation.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vegamstories.app.R
import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.presentation.components.StoryCover
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Called when the reader reaches the end of the story (its like page). */
internal val LocalOnReachedEnd = staticCompositionLocalOf<() -> Unit> { {} }

/** Where the like page's cover is on the screen: the celebrating book lifts off it. */
internal class LikeCoverSpot {
    var bounds: Rect? = null
}

internal val LocalLikeCover = staticCompositionLocalOf<LikeCoverSpot?> { null }

/**
 * A story read to the end for the first time: confetti, and the story's little book lifts off the
 * like page's cover and swoops down into the "Story finished! Added to your bookshelf" note,
 * landing on its shelf; the note then goes by itself ([onDone]). Over the reader, without taking
 * its touches.
 */
@Composable
internal fun BoxScope.StoryFinished(story: Story, onDone: () -> Unit, likeCover: LikeCoverSpot? = null) {
    val currentOnDone by rememberUpdatedState(onDone)
    val note = remember { Animatable(0f) } // 0 below the screen … 1 in place
    val flight = remember { Animatable(0f) } // the little book: 0 mid-screen … 1 in the note
    val landed = remember { Animatable(1f) } // the shelf icon's bounce as the book lands
    val gone = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { note.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) }
        delay(BOOK_SHOWN_MILLIS)
        flight.animateTo(1f, tween(durationMillis = FLIGHT_MILLIS, easing = FastOutSlowInEasing))
        landed.snapTo(1.35f)
        landed.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        delay(NOTE_SHOWN_MILLIS)
        gone.animateTo(1f, tween(durationMillis = 300))
        currentOnDone()
    }

    // Where this sits on the screen, and the note's shelf icon (the book lands on it).
    val origin = remember { arrayOf(Offset.Zero) }
    var shelfAt by remember { mutableStateOf<Offset?>(null) }
    BoxWithConstraints(Modifier.matchParentSize().onGloballyPositioned { origin[0] = it.positionInRoot() }) {
        val density = LocalDensity.current
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        Confetti(origin = Offset(width / 2f, height * 0.38f), seed = story.id.hashCode())

        // The note at the bottom, rising into place.
        val noteRise = with(density) { NOTE_RISE.toPx() }
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 48.dp, start = 24.dp, end = 24.dp)
                .graphicsLayer {
                    translationY = (1f - note.value) * noteRise
                    alpha = note.value.coerceIn(0f, 1f) * (1f - gone.value)
                },
        ) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_bookshelf),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(24.dp)
                        .onGloballyPositioned { shelfAt = it.boundsInRoot().center }
                        .graphicsLayer {
                            scaleX = landed.value
                            scaleY = landed.value
                        },
                )
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.story_finished), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }

        // The story's little book, from the like page's cover (at the top) down into the note's shelf,
        // swinging out to the side so it never sits over the heart.
        if (flight.value < 1f) {
            // Just over the like page's cover, the same size (else about where it usually is).
            val cover = remember { likeCover?.bounds }
            val bookWidth = cover?.width ?: with(density) { BOOK_WIDTH.toPx() }
            val bookHeight = cover?.height ?: (bookWidth * 4f / 3f)
            val landedScale = with(density) { LANDED_WIDTH.toPx() } / bookWidth
            Box(
                Modifier
                    .offset {
                        val t = flight.value
                        val start = cover?.center?.minus(origin[0]) ?: Offset(width / 2f, height * BOOK_START)
                        val end = shelfAt?.minus(origin[0]) ?: Offset(width / 2f, height - 72.dp.toPx())
                        val swing = Offset(width * 0.32f * (4f * t * (1f - t)), 0f)
                        val at = lerp(start, end, t) + swing
                        IntOffset((at.x - bookWidth / 2f).roundToInt(), (at.y - bookHeight / 2f).roundToInt())
                    }
                    .size(with(density) { bookWidth.toDp() }, with(density) { bookHeight.toDp() })
                    .graphicsLayer {
                        val t = flight.value
                        // It lifts off the cover (a little bigger), then shrinks to fit the shelf.
                        val s = (LIFT + (landedScale - LIFT) * t) * note.value.coerceIn(0f, 1f)
                        scaleX = s
                        scaleY = s
                        rotationZ = 18f * (4f * t * (1f - t)) - 8f * t
                        alpha = 1f - (t - 0.85f).coerceAtLeast(0f) / 0.15f
                    }
                    .shadow(10.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                StoryCover(story = story, contentDescription = null, modifier = Modifier.matchParentSize())
            }
        }
    }
}

/**
 * The little book: how big and how far down it starts if the like page's cover isn't known (that
 * cover's size; about where it is), how much it lifts off it, how big it lands on the shelf, how
 * long it's shown before it flies, and how long the flight takes.
 */
private val BOOK_WIDTH = 120.dp
private const val BOOK_START = 0.19f
private const val LIFT = 1.06f
private val LANDED_WIDTH = 22.dp
private const val BOOK_SHOWN_MILLIS = 450L
private const val FLIGHT_MILLIS = 700

/** The note: how far below it starts, and how long it stays once the book has landed. */
private val NOTE_RISE = 140.dp
private const val NOTE_SHOWN_MILLIS = 1_800L
