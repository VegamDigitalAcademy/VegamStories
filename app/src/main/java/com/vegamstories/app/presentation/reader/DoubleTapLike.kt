package com.vegamstories.app.presentation.reader

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.vegamstories.app.ui.theme.VegamRed
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Double-tapping a comic page likes the story (see [doubleTapToLike]): this likes it. Given by the
 * comic reader only, so the text, the video and the cover never take double taps.
 */
internal val LocalLikeOnDoubleTap = staticCompositionLocalOf<(() -> Unit)?> { null }

/** A heart popping where a page was double-tapped, [born] at that time (uptime, in ms). */
internal class TapHeart(val at: Offset, val born: Long, val seed: Int)

/** The hearts popping over the pages, and the time they're drawn at. */
@Stable
internal class TapHearts {
    val hearts = mutableStateListOf<TapHeart>()
    var now by mutableLongStateOf(0L)

    fun pop(at: Offset, atMillis: Long) {
        hearts += TapHeart(at, atMillis, seed = (at.x * 31 + at.y).toInt() xor atMillis.toInt())
    }
}

/** Hearts for [doubleTapToLike]: each plays out and goes by itself. */
@Composable
internal fun rememberTapHearts(): TapHearts {
    val hearts = remember { TapHearts() }
    LaunchedEffect(hearts.hearts.isNotEmpty()) {
        while (hearts.hearts.isNotEmpty()) {
            withFrameMillis { frame ->
                hearts.now = frame
                hearts.hearts.removeAll { frame - it.born > TAP_HEART_MILLIS }
            }
        }
    }
    return hearts
}

/**
 * Double-tapping the page likes the story: a big heart pops where the finger is, with a ring and a
 * few sparks, and the heart in the top bar fills and counts up (LikeButton does that, as for any
 * like). Already liked, it only pops another heart: a double tap never unlikes. Taps a button on the
 * page takes, a swipe, or a long press (which pauses auto-turn) don't count, nor do taps where
 * [counts] says no (the cover, the like page, the sides that turn pages).
 */
@Composable
internal fun Modifier.doubleTapToLike(hearts: TapHearts, counts: (at: Offset, size: IntSize) -> Boolean): Modifier {
    val like = LocalLikeOnDoubleTap.current ?: return this
    val currentLike by rememberUpdatedState(like)
    val currentCounts by rememberUpdatedState(counts)
    val haptics = LocalHapticFeedback.current
    return this
        .pointerInput(hearts) {
            awaitEachGesture {
                // Watched last (Final), once the page and its buttons have had the taps.
                val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                val firstUp = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitStillUp(first) }
                if (firstUp == null || firstUp.isConsumed || !currentCounts(first.position, size)) return@awaitEachGesture
                val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                } ?: return@awaitEachGesture
                if ((second.position - first.position).getDistance() > DOUBLE_TAP_REACH.toPx()) return@awaitEachGesture
                val secondUp = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitStillUp(second) }
                if (secondUp == null || secondUp.isConsumed) return@awaitEachGesture
                hearts.pop(second.position, secondUp.uptimeMillis)
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                currentLike()
            }
        }
        .drawWithContent {
            drawContent()
            hearts.hearts.forEach { drawTapHeart(it, hearts.now) }
        }
}

/** The finger [down] lifting where it went down: its up; null if it moved away (a swipe). */
private suspend fun AwaitPointerEventScope.awaitStillUp(down: PointerInputChange): PointerInputChange? {
    while (true) {
        val change = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull { it.id == down.id } ?: return null
        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) return null
        if (!change.pressed) return change
    }
}

/**
 * One heart, [now] into its life: it pops up big with a wobble, settles, then floats up a little
 * and fades. A ring spreads out from it and sparks fly off as it pops.
 */
private fun DrawScope.drawTapHeart(heart: TapHeart, now: Long) {
    val t = ((now - heart.born).toFloat() / TAP_HEART_MILLIS).coerceIn(0f, 1f)
    if (t >= 1f) return
    val random = Random(heart.seed)
    val lean = if (random.nextBoolean()) 1f else -1f
    val size = TapHeartSize.toPx()

    // The ring.
    val ringT = share(t, 0f, 0.4f)
    if (ringT in 0.001f..0.999f) {
        val r = size * (0.35f + 0.55f * LinearOutSlowInEasing.transform(ringT))
        drawCircle(VegamRed.copy(alpha = 0.75f * (1f - ringT)), radius = r, center = heart.at, style = Stroke(width = 5.dp.toPx() * (1f - ringT) + 1.5.dp.toPx()))
    }

    // The sparks: off in all directions as it pops, shrinking and fading.
    val sparkT = share(t, 0.06f, 0.55f)
    if (sparkT in 0.001f..0.999f) {
        val out = LinearOutSlowInEasing.transform(sparkT)
        val turn = random.nextFloat() * 2f * PI.toFloat()
        repeat(SPARKS) { i ->
            val angle = turn + i * 2f * PI.toFloat() / SPARKS + (random.nextFloat() - 0.5f) * 0.4f
            // From just outside the heart's edge, so they're seen leaving it.
            val reach = size * (0.58f + (0.55f + random.nextFloat() * 0.3f) * out)
            val c = heart.at + Offset(cos(angle) * reach, sin(angle) * reach)
            val r = (6f + random.nextFloat() * 3f).dp.toPx() * (1f - 0.5f * sparkT)
            val fade = 1f - sparkT * sparkT
            val color = SparkColors[i % SparkColors.size].copy(alpha = fade)
            // A soft shadow under each, so they show on bright pages too.
            val shadow = Color.Black.copy(alpha = 0.22f * fade)
            val nudge = Offset(0.8.dp.toPx(), 1.2.dp.toPx())
            if (i % 2 == 0) {
                drawSpark(c + nudge, r * 1.5f, shadow)
                drawSpark(c, r * 1.5f, color)
            } else {
                drawCircle(shadow, radius = r * 0.62f, center = c + nudge)
                drawCircle(color, radius = r * 0.62f, center = c)
            }
        }
    }

    // The heart: pops (overshooting), settles, then floats up and fades.
    val grow = share(t, 0f, 0.16f)
    val settle = share(t, 0.16f, 0.32f)
    val leave = FastOutSlowInEasing.transform(share(t, 0.68f, 1f))
    val scale = when {
        t < 0.16f -> 1.25f * FastOutSlowInEasing.transform(grow)
        t < 0.32f -> 1.25f - 0.25f * FastOutSlowInEasing.transform(settle)
        else -> 1f - 0.15f * leave
    }
    val wobble = lean * (14f * sin(share(t, 0f, 0.42f) * PI.toFloat() * 2f) * (1f - share(t, 0f, 0.42f)))
    val center = heart.at + Offset(0f, -size * 0.35f * leave)
    val alpha = 1f - leave
    drawHeart(center, width = size * scale, degrees = wobble, alpha = alpha)
}

/** A glossy red heart [width] wide round [center], white-rimmed so it shows on any page. */
private fun DrawScope.drawHeart(center: Offset, width: Float, degrees: Float, alpha: Float) {
    if (width <= 1f || alpha <= 0.01f) return
    val k = width / HEART_PATH_WIDTH
    translate(center.x, center.y) {
        rotate(degrees, pivot = Offset.Zero) {
            scale(k, k, pivot = Offset.Zero) {
                translate(-HEART_PATH_CENTER.x, -HEART_PATH_CENTER.y) {
                    translate(0.5f, 0.9f) { drawPath(HeartPath, Color.Black.copy(alpha = 0.28f * alpha)) }
                    drawPath(HeartPath, Color.White.copy(alpha = alpha), style = Stroke(width = 1.4f))
                    drawPath(
                        HeartPath,
                        Brush.verticalGradient(listOf(HeartLight, VegamRed, HeartDeep), startY = 3f, endY = 21.5f),
                        alpha = alpha,
                    )
                    // The shine, top left.
                    rotate(-35f, pivot = Offset(7.2f, 7f)) {
                        drawOval(Color.White.copy(alpha = 0.6f * alpha), topLeft = Offset(5f, 5.8f), size = Size(4.4f, 2.4f))
                    }
                }
            }
        }
    }
}

/** A four-pointed spark. */
private fun DrawScope.drawSpark(center: Offset, radius: Float, color: Color) {
    val waist = radius * 0.25f
    val star = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + waist, center.y - waist)
        lineTo(center.x + radius, center.y)
        lineTo(center.x + waist, center.y + waist)
        lineTo(center.x, center.y + radius)
        lineTo(center.x - waist, center.y + waist)
        lineTo(center.x - radius, center.y)
        lineTo(center.x - waist, center.y - waist)
        close()
    }
    drawPath(star, color)
}

/** How far [t] is through [from] … [to], 0 … 1. */
private fun share(t: Float, from: Float, to: Float): Float = ((t - from) / (to - from)).coerceIn(0f, 1f)

/** The heart's outline (Material's favourite icon, 24 × 24): 20 wide, its middle at about (12, 12). */
private val HeartPath: Path = PathParser().parsePathString(
    "M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09" +
        "C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z",
).toPath()
private const val HEART_PATH_WIDTH = 20f
private val HEART_PATH_CENTER = Offset(12f, 12.2f)

/** A heart's size, how long it lasts, its sparks, and how near the first tap the second must be. */
private val TapHeartSize = 96.dp
internal const val TAP_HEART_MILLIS = 900L
private const val SPARKS = 8
private val DOUBLE_TAP_REACH = 48.dp

private val HeartLight = Color(0xFFFF7A7A)
private val HeartDeep = Color(0xFFB71C1C)
private val SparkColors = listOf(Color(0xFFFFC107), Color(0xFFFF4D79), Color(0xFFFF9800), Color(0xFFFFE082))
