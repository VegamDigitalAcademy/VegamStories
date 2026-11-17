package com.vegamstories.app.presentation.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vegamstories.app.R
import com.vegamstories.app.core.logging.AppLogger
import kotlin.random.Random

/**
 * Settings → Reading → Torn pages: turning pages too fast tears one, to be glued back. Not static:
 * the setting arrives with the story, and only the pages need to hear of it, not the whole reader.
 */
internal val LocalTornPages = compositionLocalOf { false }

/**
 * Counts quick page turns in a row. A turn less than [FAST_TURN_GAP_MILLIS] after the one before
 * is fast; after [FAST_TURNS_TO_TEAR] of them, [turned] says the page just reached tears.
 */
internal class FastTurns {
    private var lastAt: Long? = null
    private var streak = 0

    /** A page turned by the reader at [atMillis]: true when it's one fast turn too many. */
    fun turned(atMillis: Long): Boolean {
        val last = lastAt
        lastAt = atMillis
        streak = if (last != null && atMillis - last < FAST_TURN_GAP_MILLIS) streak + 1 else 0
        if (streak < FAST_TURNS_TO_TEAR) return false
        reset()
        return true
    }

    /** A turn the reader didn't make (auto-turn, a jump), or a page glued back: start again. */
    fun reset() {
        lastAt = null
        streak = 0
    }
}

/**
 * A torn page: which one, how it tore, how far its loose piece hangs, and the glue rubbed on so far.
 */
@Stable
internal class PageTear {
    /** The torn page, or -1. */
    var page by mutableIntStateOf(-1)
        private set

    /** Where the tear runs, across the page: a new, uneven line each time. */
    var line by mutableStateOf(emptyList<Offset>())
        private set

    /** The loose piece: 0 in place … 1 hanging down. */
    val drop = Animatable(0f)

    /** The glue: 1 wet and shiny, fading to 0 as it dries. */
    val wet = Animatable(1f)

    /** The glue's path, in shares of the page's size; [Offset.Unspecified] between strokes. */
    val glue = mutableStateListOf<Offset>()

    /** How much of the tear has glue on it, 0 … 1. */
    var covered by mutableFloatStateOf(0f)

    /** Glued: the pieces are going back together. */
    var mending by mutableStateOf(false)
        private set

    val isTorn: Boolean get() = page >= 0

    val fastTurns = FastTurns()

    fun tear(page: Int, seed: Int = Random.nextInt()) {
        log.i("$TAG: Turned too fast: page ${page + 1} tore")
        line = tearLine(seed)
        glue.clear()
        covered = 0f
        this.page = page
    }

    /** The pieces press back together, [onJoined], the glue dries, and the page turns again. */
    suspend fun mend(onJoined: () -> Unit) {
        if (mending || !isTorn) return
        log.i("$TAG: Page ${page + 1} glued back (${(covered * 100).toInt()}% of the tear rubbed)")
        mending = true
        drop.animateTo(0f, tween(durationMillis = 280, easing = EaseIn))
        onJoined()
        wet.animateTo(0f, tween(durationMillis = 700))
        page = -1
        glue.clear()
        covered = 0f
        wet.snapTo(1f)
        fastTurns.reset()
        mending = false
    }
}

/**
 * The tear: a jagged line from the left edge to the right one, about halfway down, in shares of
 * the page's size. The same [seed] gives the same line.
 */
internal fun tearLine(seed: Int, segments: Int = 18): List<Offset> {
    val random = Random(seed)
    val startY = 0.38f + random.nextFloat() * 0.2f
    val endY = 0.38f + random.nextFloat() * 0.2f
    return List(segments + 1) { i ->
        val x = i / segments.toFloat()
        val jag = if (i == 0 || i == segments) 0f else (random.nextFloat() - 0.5f) * TEAR_JAG
        Offset(x, startY + (endY - startY) * x + jag)
    }
}

/** How much of [line] has [glue] within [reach] of it, 0 … 1 (all in pixels). */
internal fun glueCoverage(line: List<Offset>, glue: List<Offset>, reach: Float): Float {
    if (line.size < 2) return 0f
    val spots = line.zipWithNext { a, b -> List(SPOTS_PER_SEGMENT) { lerp(a, b, it / SPOTS_PER_SEGMENT.toFloat()) } }
        .flatten() + line.last()
    val reach2 = reach * reach
    val glued = spots.count { spot -> glue.any { it.isSpecified && (it - spot).getDistanceSquared() <= reach2 } }
    return glued / spots.size.toFloat()
}

/**
 * Draws page content torn along [PageTear.line]: the top piece stays, the bottom one hangs from its
 * left end as [PageTear.drop] grows, with the paper's rough edge along both, and the glue over it.
 * [layer] holds the page, drawn once, then shown in two pieces. [paper] fills each piece, for pages
 * whose own background is clear (the book's text).
 */
internal fun Modifier.tornPage(tear: PageTear, layer: GraphicsLayer, paper: Color): Modifier =
    drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        val line = tear.line.map { Offset(it.x * size.width, it.y * size.height) }
        if (line.size < 2) {
            drawLayer(layer)
            return@drawWithContent
        }
        val top = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            line.asReversed().forEach { lineTo(it.x, it.y) }
            close()
        }
        val bottom = Path().apply {
            moveTo(line.first().x, line.first().y)
            line.forEach { lineTo(it.x, it.y) }
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        val edge = Path().apply {
            moveTo(line.first().x, line.first().y)
            line.forEach { lineTo(it.x, it.y) }
        }
        val roughEdge = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Bevel)
        val drop = tear.drop.value

        clipPath(top) {
            drawRect(paper)
            drawLayer(layer)
        }
        drawPath(edge, EdgeColor, style = roughEdge)

        translate(top = drop * size.height * DROP_SHARE) {
            rotate(degrees = drop * DROP_DEGREES, pivot = line.first()) {
                // Its shadow on whatever lies beneath, then the piece itself.
                drawPath(edge, Color.Black.copy(alpha = 0.3f * drop), style = Stroke(width = 10.dp.toPx()))
                clipPath(bottom) {
                    drawRect(paper)
                    drawLayer(layer)
                }
                drawPath(edge, EdgeColor, style = roughEdge)
            }
        }
        drawGlue(tear)
    }

/** The glue rubbed on so far: a wide, glossy trail with a shine down its middle. */
private fun DrawScope.drawGlue(tear: PageTear) {
    val wet = tear.wet.value
    if (wet <= 0f || tear.glue.isEmpty()) return
    val trail = Path()
    var penDown = false
    tear.glue.forEach { point ->
        if (!point.isSpecified) {
            penDown = false
        } else {
            val x = point.x * size.width
            val y = point.y * size.height
            if (penDown) trail.lineTo(x, y) else trail.moveTo(x, y)
            penDown = true
        }
    }
    fun line(width: Float) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    // A darker rim, so it shows on cream paper as well as on a comic.
    drawPath(trail, GlueRim.copy(alpha = 0.35f * wet), style = line(GLUE_WIDTH.toPx() + 3.dp.toPx()))
    drawPath(trail, GlueColor.copy(alpha = 0.75f * wet), style = line(GLUE_WIDTH.toPx()))
    drawPath(trail, Color.White.copy(alpha = 0.7f * wet), style = line(3.dp.toPx()))
}

/**
 * Over a torn page: rubbing a finger on it puts glue on, and once most of the tear is covered
 * [onGlued] is called. Below, the glue bottle with a hint, how far along it is, and Fix it, which
 * mends the page at once (for anyone who can't or won't rub).
 */
@Composable
internal fun BoxScope.TearGlue(tear: PageTear, onGlued: () -> Unit) {
    val glueReach = with(LocalDensity.current) { GLUE_WIDTH.toPx() }
    Box(
        Modifier
            .matchParentSize()
            // Rubbing from the screen's edge along the tear is glue, not Android's Back gesture.
            .systemGestureExclusion { box ->
                val ys = tear.line.map { it.y * box.size.height }
                if (ys.isEmpty()) Rect.Zero else Rect(0f, ys.min() - glueReach, box.size.width.toFloat(), ys.max() + glueReach)
            }
            .pointerInput(tear) {
                val reach = GLUE_WIDTH.toPx()
                fun rubbed(at: Offset) {
                    tear.glue += Offset(at.x / size.width, at.y / size.height)
                    val line = tear.line.map { Offset(it.x * size.width, it.y * size.height) }
                    val glue = tear.glue.map { if (it.isSpecified) Offset(it.x * size.width, it.y * size.height) else it }
                    tear.covered = glueCoverage(line, glue, reach)
                    if (tear.covered >= GLUED_ENOUGH) onGlued()
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    if (tear.mending) return@awaitEachGesture
                    tear.glue += Offset.Unspecified
                    rubbed(down.position)
                    drag(down.id) { change ->
                        change.consume()
                        if (!tear.mending) rubbed(change.position)
                    }
                    log.d("$TAG: Glue rubbed: ${(tear.covered * 100).toInt()}% of the tear")
                }
            },
    )
    if (tear.mending) return
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .widthIn(max = 480.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { tear.covered / GLUED_ENOUGH }, modifier = Modifier.size(40.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_glue),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = stringResource(R.string.tear_hint),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onGlued) {
                Text(stringResource(R.string.tear_fix), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private const val TAG = "PageTear"
private val log = AppLogger(TAG)

/** A turn less than this after the one before counts as fast. A page takes about a second to turn,
 * so this is quick turning, not normal reading. */
private const val FAST_TURN_GAP_MILLIS = 1_500L

/** This many fast turns in a row, and the page tears. */
private const val FAST_TURNS_TO_TEAR = 3

/** How uneven the tear is, as a share of the page's height. */
private const val TEAR_JAG = 0.035f

/** The loose piece, fully dropped: how far down (a share of the page's height) and how tilted. */
private const val DROP_SHARE = 0.035f
private const val DROP_DEGREES = 4f

/** Glue on this much of the tear, and the page is fixed. */
private const val GLUED_ENOUGH = 0.8f

/** How wide the glue goes on, which is also how near the tear a finger has to pass. */
private val GLUE_WIDTH = 26.dp

private const val SPOTS_PER_SEGMENT = 3

private val EdgeColor = Color(0xFFF7F1E3)
private val GlueColor = Color(0xFFFFE08A)
private val GlueRim = Color(0xFFB8860B)
