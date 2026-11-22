package com.vegamstories.app.presentation.reader

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.vegamstories.app.presentation.navigation.STORY_CLOSE_MS
import com.vegamstories.app.presentation.navigation.CardSpot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.vegamstories.app.domain.model.ReaderSettings
import com.vegamstories.app.domain.usecase.GetReaderSettingsUseCase
import com.vegamstories.app.presentation.navigation.STORY_OPEN_MS
import com.vegamstories.app.presentation.navigation.StoryOpenEasing
import com.vegamstories.app.presentation.navigation.StoryOpener
import com.vegamstories.app.presentation.navigation.StoryOpening
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/** The reader's look (page colour, last tab), ready before any story is opened. */
@HiltViewModel
class ReaderLookViewModel @Inject constructor(getReaderSettings: GetReaderSettingsUseCase) : ViewModel() {
    val settings: StateFlow<ReaderSettings> = getReaderSettings()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderSettings())
}

/**
 * Over the whole app while a story opens from its card (see StoryOpener): the card's picture grows
 * into where the reader's cover will be, while the reader's background and bars fade in around it.
 * Everything here is drawn, not laid out, frame by frame, so it stays smooth. At the end the reader
 * opens underneath, looking the same, and this goes once the reader is on the screen.
 */
@Composable
fun StoryOpeningOverlay(opener: StoryOpener, look: ReaderLookViewModel = hiltViewModel()) {
    val opening = opener.opening ?: return
    val settings by look.settings.collectAsStateWithLifecycle()
    LaunchedEffect(opening) {
        // Once the reader's cover has been laid out (below), so the picture knows where to go. If
        // that never happens, the story just opens.
        val placed = withTimeoutOrNull(PLACE_TIMEOUT_MILLIS) { snapshotFlow { opening.to }.filterNotNull().first() }
        if (placed != null) opening.progress.animateTo(1f, tween(STORY_OPEN_MS, easing = StoryOpenEasing))
        opening.navigated = true
        opening.navigate()
        // The reader says when it's on the screen (StoryOpener.arrived); in case it never does,
        // this mustn't stay over the app.
        delay(GIVE_UP_MILLIS)
        opener.done(opening)
    }
    val progress = opening.progress
    ReaderPageTheme(background = settings.pageBackground) {
        val background = MaterialTheme.colorScheme.background
        Box(
            Modifier
                .fillMaxSize()
                // Taps wait until the story is open.
                .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false).consume() } }
                .drawBehind { drawRect(background, alpha = progress.value) },
        ) {
            // The reader as it first shows (the same as ReaderScreenContent's), fading in.
            Box(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .graphicsLayer { alpha = ((progress.value - BARS_FADE_FROM) / (1f - BARS_FADE_FROM)).coerceIn(0f, 1f) },
            ) {
                ReaderOpening(
                    story = opening.story,
                    format = settings.preferredFormat,
                    onBack = {},
                    // Its own cover shows once the moving picture has landed on it.
                    coverAlpha = { if (progress.value >= 1f) 1f else 0f },
                    onCoverPlaced = { opening.to = it },
                )
            }
            // To the cover, it lands on it; to a page the story was left on, it fades as it grows.
            MovingPicture(opening, fades = !opening.story.opensOnCover)
        }
    }
}

/**
 * The card's picture, from the card's place and shape to the cover's, filling the shape between
 * them the way the card and the cover each crop it.
 */
@Composable
private fun MovingPicture(opening: StoryOpening, fades: Boolean) {
    val url = opening.story.coverImageUrl ?: return
    val context = LocalPlatformContext.current
    // The card's own copy, from memory: it's there at once.
    val painter = rememberAsyncImagePainter(
        model = remember(url) {
            ImageRequest.Builder(context)
                .data(url)
                .memoryCacheKey("cover:$url")
                .placeholderMemoryCacheKey("cover:$url")
                .build()
        },
    )
    val cardCorner = with(LocalDensity.current) { CARD_CORNER.toPx() }
    val coverCorner = with(LocalDensity.current) { COVER_CORNER.toPx() }
    // Where this sits on the screen: the card and cover bounds are given from the screen's corner.
    val origin = remember { arrayOf(Offset.Zero) }
    Canvas(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin[0] = it.positionInRoot() },
    ) {
        val to = opening.to ?: return@Canvas
        val p = opening.progress.value
        if (p >= 1f) return@Canvas
        val box = lerp(opening.from, to, p).translate(-origin[0])
        val image = painter.intrinsicSize
        if (image.isUnspecified() || image.width <= 0f || image.height <= 0f) return@Canvas
        // Fill the box, cropping the rest (as ContentScale.Crop).
        val scale = maxOf(box.width / image.width, box.height / image.height)
        val drawn = Size(image.width * scale, image.height * scale)
        val corner = cardCorner + (coverCorner - cardCorner) * p
        val clip = Path().apply { addRoundRect(RoundRect(box, CornerRadius(corner))) }
        val alpha = if (fades) (1f - p * p) else 1f
        clipPath(clip) {
            translate(box.center.x - drawn.width / 2f, box.center.y - drawn.height / 2f) {
                with(painter) { draw(drawn, alpha = alpha) }
            }
        }
    }
}

/**
 * Back from a story opened from its card: the story's picture shrinks from the whole reader into
 * its card on the screen behind, while the reader fades away. Nothing if the card isn't there
 * (opened from Bookmarks, say).
 */
@Composable
fun StoryClosingOverlay(opener: StoryOpener) {
    val closing = opener.closing ?: return
    LaunchedEffect(closing) {
        // The card, once the screen behind is back and laid out.
        val spot = withTimeoutOrNull(FIND_CARD_MILLIS.milliseconds) {
            var found: CardSpot? = null
            while (found == null) {
                withFrameNanos {}
                found = opener.cardSpot(closing.storyId)
            }
            found
        }
        if (spot?.coverUrl != null) {
            closing.to = spot
            closing.progress.animateTo(1f, tween(STORY_CLOSE_MS, easing = FastOutSlowInEasing))
        }
        opener.closed(closing)
    }
    val spot = closing.to ?: return
    val url = spot.coverUrl ?: return
    val painter = rememberCoverPainter(url)
    val cardCorner = with(LocalDensity.current) { CARD_CORNER.toPx() }
    val origin = remember { arrayOf(Offset.Zero) }
    Canvas(Modifier.fillMaxSize().onGloballyPositioned { origin[0] = it.positionInRoot() }) {
        val p = closing.progress.value
        if (p >= 1f) return@Canvas
        val box = lerp(closing.from, spot.bounds, p).translate(-origin[0])
        // It appears over the fading reader, then shrinks into the card.
        drawPictureIn(painter, box, corner = cardCorner * p, alpha = (p / CLOSE_FADE_IN).coerceAtMost(1f))
    }
}

/** The card's cover picture, from memory: it's there at once. */
@Composable
private fun rememberCoverPainter(url: String): Painter {
    val context = LocalPlatformContext.current
    return rememberAsyncImagePainter(
        model = remember(url) {
            ImageRequest.Builder(context)
                .data(url)
                .memoryCacheKey("cover:$url")
                .placeholderMemoryCacheKey("cover:$url")
                .build()
        },
    )
}

/** [painter] filling [box] (cropping the rest, as ContentScale.Crop), with rounded [corner]s. */
private fun DrawScope.drawPictureIn(painter: Painter, box: Rect, corner: Float, alpha: Float) {
    val image = painter.intrinsicSize
    if (image.isUnspecified() || image.width <= 0f || image.height <= 0f) return
    val scale = maxOf(box.width / image.width, box.height / image.height)
    val drawn = Size(image.width * scale, image.height * scale)
    clipPath(Path().apply { addRoundRect(RoundRect(box, CornerRadius(corner))) }) {
        translate(box.center.x - drawn.width / 2f, box.center.y - drawn.height / 2f) {
            with(painter) { draw(drawn, alpha = alpha) }
        }
    }
}

private fun Size.isUnspecified() = this == Size.Unspecified

/** Back: how long to wait for the card to be laid out, and how soon the shrinking picture is fully there. */
private const val FIND_CARD_MILLIS = 250L
private const val CLOSE_FADE_IN = 0.3f

/** The reader's bars start fading in this far through. */
private const val BARS_FADE_FROM = 0.35f

/** How long the reader's cover has to be laid out, for the picture to know where to go. */
private const val PLACE_TIMEOUT_MILLIS = 300L

/** The overlay goes after this, even if the reader never says it's on the screen. */
private const val GIVE_UP_MILLIS = 1_500L

/** The card's corners, and the picture's in the book cover's frame. */
private val CARD_CORNER = 12.dp
private val COVER_CORNER = 4.dp

