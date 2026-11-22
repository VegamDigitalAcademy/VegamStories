package com.vegamstories.app.presentation.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vegamstories.app.R
import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.presentation.components.StoryCover
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The reader's download button, for the story's little book to drop into: where it is on the screen
 * ([bounds]), whether a book is on its way ([incoming]: the button shows the jar, lid open), and how
 * many have landed ([landings]: each one squashes it).
 */
@Stable
internal class DownloadSpot {
    var bounds: Rect? = null
    var incoming by mutableStateOf(false)
    var landings by mutableIntStateOf(0)
}

internal val LocalDownloadSpot = staticCompositionLocalOf<DownloadSpot?> { null }

/** Marks the flying book, for tests. */
internal const val DOWNLOAD_DROP_TAG = "download_drop"

/**
 * Download tapped: the story's cover pops up in the middle of the reader, then shrinks into a little
 * book and is tossed up and over into the download button ([spot]), dropping in through the jar's open
 * lid; the button squashes as it lands and fills as the story saves (see [DownloadIcon]). [onDone]
 * once it's in. Over the reader, without taking its touches.
 */
@Composable
internal fun BoxScope.DownloadDrop(story: Story, spot: DownloadSpot, onDone: () -> Unit) {
    val currentOnDone by rememberUpdatedState(onDone)
    val pop = remember { Animatable(0f) }
    val flight = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        spot.incoming = true
        try {
            pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
            delay(DROP_SHOWN_MILLIS)
            flight.animateTo(1f, tween(durationMillis = DROP_FLIGHT_MILLIS, easing = TossEasing))
            spot.landings++
        } finally {
            spot.incoming = false
        }
        currentOnDone()
    }

    // Where this sits on the screen: the button's place is on the screen too.
    val origin = remember { arrayOf(Offset.Zero) }
    BoxWithConstraints(
        Modifier
            .matchParentSize()
            .onGloballyPositioned { origin[0] = it.positionInRoot() },
    ) {
        val density = LocalDensity.current
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val bookWidth = with(density) { DROP_BOOK_WIDTH.toPx() }
        val bookHeight = bookWidth * 4f / 3f
        val landedScale = with(density) { DROP_LANDED_WIDTH.toPx() } / bookWidth
        Box(
            Modifier
                .offset {
                    val t = flight.value
                    val start = Offset(width / 2f, height * DROP_START)
                    val end = spot.bounds?.center?.minus(origin[0]) ?: Offset(width - 72.dp.toPx(), 24.dp.toPx())
                    // Tossed: up past the button, then down into it.
                    val over = Offset(start.x + (end.x - start.x) * 0.45f, end.y - DROP_TOSS.toPx())
                    val a = 1f - t
                    val at = start * (a * a) + over * (2f * a * t) + end * (t * t)
                    IntOffset((at.x - bookWidth / 2f).roundToInt(), (at.y - bookHeight / 2f).roundToInt())
                }
                .size(DROP_BOOK_WIDTH, DROP_BOOK_WIDTH * 4f / 3f)
                .graphicsLayer {
                    val t = flight.value
                    // It pops up a little big, then shrinks to fit in the jar, mostly early on.
                    val shrink = t.toDouble().pow(0.6).toFloat()
                    val s = pop.value * (1f + (landedScale - 1f) * shrink)
                    scaleX = s
                    scaleY = s
                    rotationZ = -24f * sin(t * PI.toFloat()) + 6f * (1f - pop.value)
                    alpha = pop.value.coerceIn(0f, 1f) * (1f - ((t - 0.9f) / 0.1f).coerceIn(0f, 1f))
                }
                .shadow(12.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .testTag(DOWNLOAD_DROP_TAG),
        ) {
            StoryCover(story = story, contentDescription = null, modifier = Modifier.matchParentSize())
        }
    }
}

/**
 * The download button's icon: an arrow. Tapped, a little jar with its lid open for the story's book
 * to drop into; it squashes as the book lands, closes its lid, and fills with red as the story saves,
 * the surface rippling. Saved, a tick that pops in. Without the flying book (animations off), the
 * jar just fills.
 */
@Composable
internal fun DownloadIcon(isDownloaded: Boolean, progress: Float?) {
    val spot = LocalDownloadSpot.current
    val primary = MaterialTheme.colorScheme.primary
    val saving = progress != null
    val incoming = spot?.incoming == true
    val jar = saving || incoming
    val level by animateFloatAsState(
        targetValue = when {
            progress != null -> progress
            isDownloaded -> 1f
            else -> 0f
        },
        animationSpec = tween(durationMillis = 300),
        label = "jarLevel",
    )
    val lid by animateFloatAsState(
        targetValue = if (incoming) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "jarLid",
    )
    // The book landing: a squash, and back with a wobble.
    val squash = remember { Animatable(0f) }
    LaunchedEffect(spot?.landings) {
        if ((spot?.landings ?: 0) > 0) {
            squash.snapTo(1f)
            squash.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessMedium))
        }
    }
    val tick = remember { Animatable(1f) }
    val showTick = isDownloaded && !jar
    var hadTick by remember { mutableStateOf(showTick) }
    LaunchedEffect(showTick) {
        if (showTick && !hadTick) {
            tick.snapTo(0.4f)
            tick.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
        hadTick = showTick
    }
    val description = stringResource(R.string.cd_download)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(30.dp)
            .onGloballyPositioned { spot?.bounds = it.boundsInRoot() }
            .graphicsLayer {
                // Wide and short as the book lands, from the bottom, like something soft.
                scaleX = 1f + 0.35f * squash.value
                scaleY = 1f - 0.3f * squash.value
                transformOrigin = TransformOrigin(0.5f, 1f)
            },
    ) {
        if (jar) {
            val wave by rememberInfiniteTransition(label = "jarWave").animateFloat(
                initialValue = 0f,
                targetValue = 2f * PI.toFloat(),
                animationSpec = infiniteRepeatable(tween(durationMillis = 1_200, easing = LinearEasing)),
                label = "wave",
            )
            Canvas(
                Modifier
                    .size(26.dp)
                    .semantics { contentDescription = description },
            ) {
                drawJar(level = level, wave = wave, lid = lid, color = primary)
            }
        } else {
            Icon(
                painter = painterResource(if (showTick) R.drawable.ic_download_done else R.drawable.ic_download),
                contentDescription = description,
                tint = if (isDownloaded) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = tick.value
                        scaleY = tick.value
                    },
            )
        }
    }
}

/**
 * A little glass jar in [color]: [level] (0 … 1) full of it, its surface rippling ([wave], a phase),
 * and its lid lifted and tipped back as far as [lid] (0 shut … 1 open).
 */
private fun DrawScope.drawJar(level: Float, wave: Float, lid: Float, color: Color) {
    val w = size.width
    val h = size.height
    val stroke = 1.7.dp.toPx()
    val body = RoundRect(Rect(w * 0.17f, h * 0.3f, w * 0.83f, h * 0.96f), CornerRadius(w * 0.2f))
    val bodyPath = Path().apply { addRoundRect(body) }
    // The glass, faintly tinted, and what's in it.
    drawPath(bodyPath, color.copy(alpha = 0.12f))
    clipPath(bodyPath) {
        val filled = level.coerceIn(0f, 1f)
        if (filled > 0.01f) {
            val surface = body.bottom - (body.bottom - body.top) * filled
            val ripple = if (filled < 0.99f) h * 0.035f else 0f
            val liquid = Path().apply {
                moveTo(0f, surface)
                val steps = 16
                for (i in 0..steps) {
                    val x = w * i / steps
                    lineTo(x, surface + sin(wave + x / w * 3f * PI.toFloat()) * ripple)
                }
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(liquid, color.copy(alpha = 0.9f))
            drawLine(Color.White.copy(alpha = 0.55f), Offset(body.left, surface), Offset(body.right, surface), strokeWidth = 1.dp.toPx())
        }
    }
    drawPath(bodyPath, color, style = Stroke(width = stroke))
    // The glass's shine.
    drawLine(Color.White.copy(alpha = 0.7f), Offset(w * 0.3f, h * 0.45f), Offset(w * 0.3f, h * 0.7f), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
    // The neck, and the lid on it (lifted and tipped back to let the book in).
    drawLine(color, Offset(w * 0.24f, h * 0.3f), Offset(w * 0.76f, h * 0.3f), strokeWidth = stroke, cap = StrokeCap.Round)
    val lidTopLeft = Offset(w * 0.22f, h * 0.12f - h * 0.14f * lid)
    rotate(degrees = -38f * lid, pivot = Offset(w * 0.22f, h * 0.24f)) {
        drawRoundRect(color, topLeft = lidTopLeft, size = Size(w * 0.56f, h * 0.13f), cornerRadius = CornerRadius(w * 0.05f))
        drawRoundRect(color, topLeft = lidTopLeft + Offset(w * 0.22f, -h * 0.06f), size = Size(w * 0.12f, h * 0.08f), cornerRadius = CornerRadius(w * 0.03f))
    }
}

/** Tossed: off quickly, slowing at the top, then dropping in. */
private val TossEasing = CubicBezierEasing(0.25f, 0.1f, 0.45f, 1f)

/**
 * The flying book: how wide it pops up, how far down the reader (a share of its height), how long
 * it's shown before it's tossed, how long the toss takes, how far above the button it goes, and how
 * wide it is as it drops into the jar.
 */
private val DROP_BOOK_WIDTH = 120.dp
private const val DROP_START = 0.42f
private const val DROP_SHOWN_MILLIS = 220L
private const val DROP_FLIGHT_MILLIS = 700
private val DROP_TOSS = 60.dp
private val DROP_LANDED_WIDTH = 14.dp
