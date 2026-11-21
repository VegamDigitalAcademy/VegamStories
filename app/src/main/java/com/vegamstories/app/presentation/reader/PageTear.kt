package com.vegamstories.app.presentation.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vegamstories.app.R
import com.vegamstories.app.core.logging.AppLogger
import com.vegamstories.app.ui.theme.VegamRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

/**
 * Settings → Reading → Torn pages: turning pages too fast tears one, to be glued back. Not static:
 * the setting arrives with the story, and only the pages need to hear of it, not the whole reader.
 */
internal val LocalTornPages = compositionLocalOf { false }

/** The language of the story being read: the torn page speaks it (see [storyString]). */
internal val LocalStoryLanguage = compositionLocalOf<String?> { null }

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

/** Where a torn page is in its repair. */
internal enum class TearStage {
    /** Just torn: the glue bottle pops up, waiting to be tapped. */
    TORN,

    /** The bottle pours glue along the tear. */
    POURING,

    /** The glue is rubbed in, stretch by stretch, and the tear closes where it is. */
    RUBBING,

    /** Mended: a bounce, confetti, and Read story to carry on. */
    FIXED,
}

/**
 * A torn page: which one, how it tore, how far its loose piece hangs, how far the glue has been
 * poured along the tear, and how much each stretch of it has been rubbed in.
 */
@Stable
internal class PageTear {
    /** The torn page, or -1. */
    var page by mutableIntStateOf(-1)
        private set

    /** Where the tear runs, across the page: a new, uneven line each time. */
    var line by mutableStateOf(emptyList<Offset>())
        private set

    /** Picks this tear's paper fibres and the glue's wobbles. */
    var seed by mutableIntStateOf(0)
        private set

    var stage by mutableStateOf(TearStage.TORN)
        private set

    /** The loose piece: 0 in place … 1 hanging down. */
    val drop = Animatable(0f)

    /** Where along the tear (0 … 1 from its left end) the bottle was dropped and the glue landed. */
    var pourFrom by mutableFloatStateOf(0.5f)
        private set

    /** How far the glue has run from [pourFrom] to both ends of the tear, 0 … 1 (see [pouredRange]). */
    val pour = Animatable(0f)

    /** The puddle where the glue landed: 0 none … 1 spread out. */
    val splash = Animatable(0f)

    /** The page's happy bounce once it's fixed: 1 = its own size. */
    val bounce = Animatable(1f)

    /** How much each of the tear's [RUB_STRETCHES] stretches has been rubbed in, 0 … 1. */
    val rubbed = mutableStateListOf<Float>()

    /** How much of the tear has been rubbed in, 0 … 1. */
    val progress: Float get() = if (rubbed.isEmpty()) 0f else rubbed.sum() / rubbed.size

    val isTorn: Boolean get() = page >= 0

    val fastTurns = FastTurns()

    fun tear(page: Int, seed: Int = Random.nextInt()) {
        log.i("$TAG: Turned too fast: page ${page + 1} tore")
        this.seed = seed
        line = tearLine(seed)
        stage = TearStage.TORN
        rubbed.clear()
        repeat(RUB_STRETCHES) { rubbed += 0f }
        this.page = page
    }

    /** Back to a fresh tear's look, before it opens. */
    suspend fun resetMotion() {
        drop.snapTo(0f)
        pour.snapTo(0f)
        splash.snapTo(0f)
        bounce.snapTo(1f)
        pourFrom = 0.5f
    }

    /** The bottle was dropped (or tapped) to pour at [at] of the way along the tear. */
    fun startPouring(at: Float) {
        if (stage != TearStage.TORN) return
        pourFrom = at.coerceIn(0f, 1f)
        stage = TearStage.POURING
    }

    fun poured() {
        if (stage == TearStage.POURING) stage = TearStage.RUBBING
    }

    /** A finger at [finger] moved [moved] px along the page (all in px, as [linePx]): rub there. */
    fun rub(finger: Offset, moved: Float, linePx: List<Offset>, reach: Float, fillDistance: Float) {
        if (stage != TearStage.RUBBING) return
        rubbedAfter(rubbed, linePx, finger, moved, reach, fillDistance).forEachIndexed { i, amount ->
            if (rubbed[i] != amount) rubbed[i] = amount
        }
        if (progress >= 1f) fixed()
    }

    /** Fix it: glued at once, for anyone who can't or won't rub. */
    suspend fun fixNow() {
        if (!isTorn || stage == TearStage.FIXED) return
        log.i("$TAG: Page ${page + 1} fixed with Fix it")
        pour.snapTo(1f)
        splash.snapTo(1f)
        rubbed.indices.forEach { rubbed[it] = 1f }
        stage = TearStage.FIXED
    }

    /** Read story: the page is whole again and turns as before. */
    fun finish() {
        page = -1
        rubbed.clear()
        stage = TearStage.TORN
        fastTurns.reset()
    }

    private fun fixed() {
        log.i("$TAG: Page ${page + 1} glued back by rubbing")
        stage = TearStage.FIXED
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

/**
 * The stretch of tear the glue covers ([PageTear.pour] = [poured]), as it runs from where it landed
 * ([from]) out to both ends: both ends are reached together, at [poured] = 1.
 */
internal fun pouredRange(from: Float, poured: Float): ClosedFloatingPointRange<Float> {
    val p = poured.coerceIn(0f, 1f)
    return (from * (1f - p))..(from + (1f - from) * p)
}

/** The point on [line] (left to right) at [fraction] of the way across it. */
internal fun pointAt(line: List<Offset>, fraction: Float): Offset {
    if (line.size < 2) return line.firstOrNull() ?: Offset.Zero
    val x = line.first().x + (line.last().x - line.first().x) * fraction.coerceIn(0f, 1f)
    val i = line.indexOfFirst { it.x >= x }.coerceIn(1, line.lastIndex)
    val a = line[i - 1]
    val b = line[i]
    val t = if (b.x == a.x) 0f else ((x - a.x) / (b.x - a.x)).coerceIn(0f, 1f)
    return lerp(a, b, t)
}

/**
 * Rubbing: the finger at [finger], having moved [moved], fills every stretch of [line] whose middle
 * is within [reach] of it by moved / [fillDistance] (all in px). Back and forth a few times, and a
 * stretch is done.
 */
internal fun rubbedAfter(
    rubbed: List<Float>,
    line: List<Offset>,
    finger: Offset,
    moved: Float,
    reach: Float,
    fillDistance: Float,
): List<Float> {
    val reach2 = reach * reach
    return rubbed.mapIndexed { k, amount ->
        val middle = pointAt(line, (k + 0.5f) / rubbed.size)
        if ((middle - finger).getDistanceSquared() <= reach2) (amount + moved / fillDistance).coerceAtMost(1f) else amount
    }
}

/** How much of the tear has been rubbed in at [fraction] of the way across, between stretches. */
private fun PageTear.rubbedAt(fraction: Float): Float {
    if (rubbed.isEmpty()) return 0f
    val position = fraction * rubbed.size - 0.5f
    val i = position.toInt().coerceIn(0, rubbed.lastIndex)
    val j = (i + 1).coerceAtMost(rubbed.lastIndex)
    val t = (position - i).coerceIn(0f, 1f)
    return rubbed[i] + (rubbed[j] - rubbed[i]) * t
}

/**
 * Draws page content torn along [PageTear.line]: the top piece stays; the bottom one hangs from its
 * left end as [PageTear.drop] grows, and comes back up as the glue is rubbed in. Where a stretch is
 * rubbed, the page fills the gap again. Paper fibres stick out of both torn edges, with a soft
 * shadow down in the gap and the glue over the tear. [layer] holds the page, drawn once, then shown in pieces. [paper] fills each piece,
 * for pages whose own background is clear (the book's text).
 */
internal fun Modifier.tornPage(tear: PageTear, layer: GraphicsLayer, paper: Color): Modifier =
    drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        val line = tear.line.map { Offset(it.x * size.width, it.y * size.height) }
        if (line.size < 2) {
            drawLayer(layer)
            return@drawWithContent
        }
        scale(tear.bounce.value) {
            drawTear(tear, line, layer, paper)
        }
    }

private fun DrawScope.drawTear(tear: PageTear, line: List<Offset>, layer: GraphicsLayer, paper: Color) {
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
    val fibres = paperFibres(tear.seed, line)
    // The loose piece hangs in one piece, and comes back up as more of the tear is rubbed in.
    val drop = tear.drop.value * (1f - tear.progress)
    val down = drop * size.height * DROP_SHARE
    val degrees = drop * DROP_DEGREES
    val slope = tan(degrees * PI.toFloat() / 180f)
    /** How far below the tear the loose piece's edge is at x. */
    fun gapAt(x: Float): Float = down + slope * (x - line.first().x)

    // The gap, sampled finely: where it's been rubbed in, the page fills it again from the top, as
    // far as that stretch is rubbed; below that, a soft shadow, darkest under the top piece.
    val samples = (size.width / 3.dp.toPx()).toInt().coerceAtLeast(2)
    val points = List(samples + 1) { pointAt(line, it / samples.toFloat()) }
    fun band(upper: (Offset) -> Float, lower: (Offset) -> Float) = Path().apply {
        points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, upper(p)) else lineTo(p.x, upper(p)) }
        points.asReversed().forEach { p -> lineTo(p.x, lower(p)) }
        close()
    }
    val healed = { p: Offset -> p.y + gapAt(p.x) * tear.rubbedAt(p.x / size.width) }
    val open = band(upper = healed, lower = { p -> p.y + gapAt(p.x) + 1f })
    clipPath(open) {
        drawRect(Color.Black.copy(alpha = 0.16f))
        val edge = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, healed(p)) else lineTo(p.x, healed(p)) } }
        drawPath(edge, Color.Black.copy(alpha = 0.2f), style = Stroke(width = 14.dp.toPx()))
        drawPath(edge, Color.Black.copy(alpha = 0.2f), style = Stroke(width = 6.dp.toPx()))
    }
    if (tear.rubbed.any { it > 0f }) {
        clipPath(band(upper = { it.y - 1f }, lower = { healed(it) + 1f })) {
            drawRect(paper)
            drawLayer(layer)
        }
    }

    // The top piece, with its fibres hanging into the gap.
    clipPath(top) {
        drawRect(paper)
        drawLayer(layer)
    }
    drawTornEdge(tear, line, fibres.top, down = true)

    // The loose piece, with its own fibres reaching up.
    translate(top = down) {
        rotate(degrees = degrees, pivot = line.first()) {
            clipPath(bottom) {
                drawRect(paper)
                drawLayer(layer)
            }
            drawTornEdge(tear, line, fibres.bottom, down = false)
        }
    }
    drawGlue(tear, line)
}

/** A torn edge: a pale rough line with fibres sticking out, gone where the tear has been rubbed shut. */
private fun DrawScope.drawTornEdge(tear: PageTear, line: List<Offset>, fibres: List<Fibre>, down: Boolean) {
    val rough = 2.5.dp.toPx()
    line.zipWithNext { a, b ->
        val open = 1f - tear.rubbedAt((a.x + b.x) / 2f / size.width)
        if (open > 0.02f) drawLine(EdgeColor.copy(alpha = open), a, b, strokeWidth = rough, cap = StrokeCap.Round)
    }
    val direction = if (down) 1f else -1f
    fibres.forEach { fibre ->
        val open = 1f - tear.rubbedAt(fibre.at.x / size.width)
        if (open <= 0.02f) return@forEach
        val length = fibre.length.dp.toPx()
        val end = fibre.at + Offset(cos(fibre.angle) * length, sin(fibre.angle) * length * direction)
        if (fibre.isBit) {
            drawCircle(EdgeColor.copy(alpha = open), radius = fibre.length.dp.toPx() / 4f, center = end)
        } else {
            drawLine(EdgeColor.copy(alpha = 0.95f * open), fibre.at, end, strokeWidth = 1.4.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

/** A fibre (or a tiny bit) of paper at a torn edge: where, how long, and at what angle. */
private class Fibre(val at: Offset, val length: Float, val angle: Float, val isBit: Boolean)

private class Fibres(val top: List<Fibre>, val bottom: List<Fibre>)

/** The fibres along both edges of this tear (the same each time it's drawn). */
private fun paperFibres(seed: Int, line: List<Offset>): Fibres {
    val random = Random(seed xor 0x5F1B)
    fun edge() = List(FIBRES_PER_EDGE) { i ->
        val at = pointAt(line, random.nextFloat())
        // Mostly straight out from the edge, leaning either way.
        val angle = (PI / 2 + (random.nextFloat() - 0.5f) * 1.3f).toFloat()
        Fibre(at, 3f + random.nextFloat() * 6f, angle, isBit = i % 4 == 0)
    }
    return Fibres(top = edge(), bottom = edge())
}

/**
 * The glue: a puddle where the bottle landed, and from it a glossy light blue run of liquid along
 * the tear, slightly wobbly, with round, wet ends where it's still flowing. Each stretch spreads
 * and clears as it's rubbed in.
 */
private fun DrawScope.drawGlue(tear: PageTear, line: List<Offset>) {
    val poured = tear.pour.value
    if (poured <= 0f && tear.splash.value <= 0f) return
    val range = pouredRange(tear.pourFrom, poured)
    val wobble = 1.8.dp.toPx()
    val width = GLUE_WIDTH.toPx()
    fun stroke(w: Float) = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val stretches = tear.rubbed.size.coerceAtLeast(1)
    for (k in 0 until stretches) {
        val from = maxOf(k / stretches.toFloat(), range.start)
        val to = minOf((k + 1) / stretches.toFloat(), range.endInclusive)
        if (to <= from) continue
        val rubbed = tear.rubbed.getOrElse(k) { 0f }
        val wet = 1f - rubbed
        if (wet <= 0.01f) continue
        val path = Path()
        val steps = 6
        for (i in 0..steps) {
            val f = from + (to - from) * i / steps
            val p = pointAt(line, f)
            val y = p.y + sin(f * 40f + tear.seed) * wobble
            if (i == 0) path.moveTo(p.x, y) else path.lineTo(p.x, y)
        }
        val spread = width * (1f + rubbed * 0.8f)
        drawPath(path, GlueRim.copy(alpha = 0.35f * wet), style = stroke(spread + 2.5.dp.toPx()))
        drawPath(path, GlueLiquid.copy(alpha = 0.62f * wet), style = stroke(spread))
        translate(top = -spread * 0.2f) {
            drawPath(path, Color.White.copy(alpha = 0.85f * wet), style = stroke(spread * 0.2f))
        }
    }
    // Still flowing: round, wet fronts at both ends.
    if (poured in 0.001f..0.999f) {
        listOf(range.start, range.endInclusive).forEach { f ->
            val c = pointAt(line, f)
            drawLiquidDrop(c, width * 0.9f, alpha = 1f - tear.rubbedAt(f))
        }
    }
    // The puddle where it landed.
    val splash = tear.splash.value
    if (splash > 0f) {
        val c = pointAt(line, tear.pourFrom)
        val wet = 1f - tear.rubbedAt(tear.pourFrom)
        if (wet > 0.01f) {
            val r = width * 2.4f * splash
            val puddle = Path().apply {
                val lobes = 7
                for (i in 0..64) {
                    val a = i / 64f * 2f * PI.toFloat()
                    val rr = r * (1f + 0.16f * sin(a * lobes + tear.seed)) * (if (sin(a) > 0f) 0.7f else 0.55f)
                    val p = c + Offset(cos(a) * rr * 1.5f, sin(a) * rr)
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
                close()
            }
            drawPath(puddle, GlueRim.copy(alpha = 0.3f * wet), style = Stroke(width = 2.dp.toPx()))
            drawPath(puddle, GlueLiquid.copy(alpha = 0.55f * wet))
            drawCircle(Color.White.copy(alpha = 0.8f * wet), radius = r * 0.18f, center = c - Offset(r * 0.45f, r * 0.2f))
        }
    }
}

/** A round drop of glue, with its shine. */
private fun DrawScope.drawLiquidDrop(center: Offset, radius: Float, alpha: Float) {
    if (alpha <= 0.01f) return
    drawCircle(GlueRim.copy(alpha = 0.35f * alpha), radius = radius + 1.dp.toPx(), center = center)
    drawCircle(GlueLiquid.copy(alpha = 0.7f * alpha), radius = radius, center = center)
    drawCircle(Color.White.copy(alpha = 0.9f * alpha), radius = radius * 0.3f, center = center - Offset(radius * 0.3f, radius * 0.3f))
}

/**
 * Over a torn page: the glue bottle pops up. Dropped on the page (or tapped), it tips over the tear
 * there and the glue pools and runs along the tear both ways; then rubbing a finger back and forth
 * along the tear rubs it in, closing the tear where it's done, with sparkles under the finger. Once it's all in, [onFixed]: confetti, and Read story ([onDone]). Fix it skips
 * straight there, for anyone who can't or won't rub. Everything is in the story's language.
 */
@Composable
internal fun BoxScope.TearRepair(tear: PageTear, onFixed: () -> Unit, onDone: () -> Unit) {
    BoxWithConstraints(Modifier.matchParentSize()) {
        val density = LocalDensity.current
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val bottleWidth = with(density) { BottleWidth.toPx() }
        val bottleHeight = with(density) { BottleHeight.toPx() }
        val reach = with(density) { RubReach.toPx() }
        val fillDistance = with(density) { RubToFill.toPx() }
        val corner = with(density) {
            Offset(width - CornerEnd.toPx() - bottleWidth / 2f, height - CornerBottom.toPx() - bottleHeight / 2f)
        }
        val line = tear.line.map { Offset(it.x * width, it.y * height) }
        // Turned over to pour, nozzle down onto the tear, leaning towards the middle of the page so it
        // stays on screen. Where its middle has to be for the nozzle to touch [point]:
        fun pourTilt(at: Float) = if (at > 0.5f) POUR_TILT else -POUR_TILT
        fun pouringAt(point: Offset, tiltDegrees: Float): Offset {
            val tilt = tiltDegrees * PI.toFloat() / 180f
            return point + Offset(-sin(tilt) * bottleHeight / 2f, cos(tilt) * bottleHeight / 2f)
        }

        val scope = rememberCoroutineScope()
        val haptics = LocalHapticFeedback.current
        val bottle = remember(tear.page) { BottleMotion(corner) }
        val sparkles = remember(tear.page) { mutableStateListOf<Sparkle>() }
        var now by remember { mutableLongStateOf(0L) }

        // The bottle pops in once the page has torn.
        LaunchedEffect(tear.page) {
            bottle.position.snapTo(corner)
            delay(BOTTLE_POP_DELAY_MILLIS)
            bottle.scale.animateTo(1.15f, tween(durationMillis = 220, easing = FastOutSlowInEasing))
            bottle.scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow))
        }
        // Fixed: the page gives a little hop of joy, and "Page fixed!" goes by itself after a moment
        // (or sooner, with Read story).
        val currentOnDone by rememberUpdatedState(onDone)
        LaunchedEffect(tear.stage) {
            if (tear.stage != TearStage.FIXED) return@LaunchedEffect
            onFixed()
            launch {
                tear.bounce.animateTo(1.04f, tween(durationMillis = 140, easing = FastOutSlowInEasing))
                tear.bounce.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
            }
            delay(FIXED_SHOWN_MILLIS)
            currentOnDone()
        }
        // Sparkles fade on their own.
        LaunchedEffect(sparkles.isNotEmpty()) {
            while (sparkles.isNotEmpty()) {
                withFrameMillis { frame ->
                    now = frame
                    sparkles.removeAll { frame - it.born > SPARKLE_MILLIS }
                }
            }
        }

        // Pours at [at] of the way along the tear: the bottle tips over it there, the glue lands in
        // a puddle and runs along the tear both ways, and the bottle goes back to its corner.
        val pourAt: (at: Float) -> Unit = { at ->
            scope.launch {
                tear.startPouring(at)
                val tilt = pourTilt(tear.pourFrom)
                val target = pouringAt(pointAt(line, tear.pourFrom), tilt)
                launch { bottle.scale.animateTo(1f, tween(durationMillis = 200)) }
                launch { bottle.tilt.animateTo(tilt, tween(durationMillis = 420)) }
                bottle.position.animateTo(target, tween(durationMillis = 420, easing = FastOutSlowInEasing))
                launch { tear.splash.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
                tear.pour.animateTo(1f, tween(durationMillis = POUR_MILLIS, easing = FastOutSlowInEasing))
                launch { bottle.tilt.animateTo(0f, tween(durationMillis = 380)) }
                bottle.position.animateTo(corner, tween(durationMillis = 480, easing = FastOutSlowInEasing))
                tear.poured()
            }
        }
        // Dropped with its nozzle at [nozzleX] across the page: pour there.
        val dropAt: (nozzleX: Float) -> Unit = { nozzleX -> pourAt((nozzleX / width).coerceIn(DROP_MARGIN, 1f - DROP_MARGIN)) }

        // Every touch on the torn page is the repair's: rubbing, once the glue is on.
        Box(
            Modifier
                .matchParentSize()
                // Rubbing from the screen's edge along the tear is glue, not Android's Back gesture.
                .systemGestureExclusion { box ->
                    val ys = tear.line.map { it.y * box.size.height }
                    if (ys.isEmpty()) Rect.Zero else Rect(0f, ys.min() - reach * 2, box.size.width.toFloat(), ys.max() + reach * 2)
                }
                .pointerInput(tear) {
                    var lastTick = 0L
                    var lastSparkle = 0L
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        if (tear.stage != TearStage.RUBBING) return@awaitEachGesture
                        drag(down.id) { change ->
                            change.consume()
                            if (tear.stage != TearStage.RUBBING) return@drag
                            val moved = (change.position - change.previousPosition).getDistance()
                            tear.rub(change.position, moved, line, reach, fillDistance)
                            val at = change.uptimeMillis
                            if (at - lastSparkle >= SPARKLE_EVERY_MILLIS && moved > 0f) {
                                lastSparkle = at
                                sparkles += Sparkle(change.position, born = at, seed = sparkles.size + at.toInt())
                            }
                            if (at - lastTick >= HAPTIC_EVERY_MILLIS && moved > 0f) {
                                lastTick = at
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            }
                        }
                        log.d("$TAG: Rubbed in: ${(tear.progress * 100).toInt()}% of the tear")
                    }
                },
        )

        // Sparkles under the finger, and the confetti once fixed.
        Canvas(Modifier.matchParentSize()) {
            sparkles.forEach { drawSparkle(it, now) }
        }
        if (tear.stage == TearStage.FIXED) Confetti(origin = pointAt(line, 0.5f), seed = tear.seed)

        val language = LocalStoryLanguage.current

        @Composable
        fun words(id: Int) = if (language != null) storyString(id, language) else stringResource(id)

        // The glue bottle: popping up, picked up and dropped on the page (or tapped), pouring, then
        // back in its corner. The glue inside sloshes, stays level as the bottle tips, and runs low.
        if (tear.stage != TearStage.FIXED) {
            val description = words(R.string.tear_glue_bottle)
            val slosh by rememberInfiniteTransition(label = "glueSlosh").animateFloat(
                initialValue = 0f,
                targetValue = 2f * PI.toFloat(),
                animationSpec = infiniteRepeatable(tween(durationMillis = 1_400, easing = LinearEasing)),
                label = "slosh",
            )
            val canPour = tear.stage == TearStage.TORN
            Box(
                Modifier
                    .offset {
                        val at = bottle.position.value
                        IntOffset((at.x - bottleWidth / 2f).roundToInt(), (at.y - bottleHeight / 2f).roundToInt())
                    }
                    .size(BottleWidth, BottleHeight)
                    .graphicsLayer {
                        scaleX = bottle.scale.value
                        scaleY = bottle.scale.value
                        rotationZ = bottle.tilt.value
                    }
                    .pointerInput(canPour) {
                        if (!canPour) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            var dragged = false
                            scope.launch { bottle.scale.animateTo(1.12f, tween(durationMillis = 120)) }
                            drag(down.id) { change ->
                                val delta = change.position - change.previousPosition
                                change.consume()
                                if (!dragged && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) dragged = true
                                if (dragged) scope.launch { bottle.position.snapTo(bottle.position.value + delta) }
                            }
                            // Picked up and dropped: pour where it is. Just tapped: pour mid-tear.
                            if (dragged) dropAt(bottle.position.value.x) else pourAt(0.5f)
                        }
                    }
                    .semantics {
                        contentDescription = description
                        role = Role.Button
                        if (canPour) onClick { pourAt(0.5f); true }
                    },
            ) {
                Canvas(Modifier.matchParentSize()) {
                    drawGlueBottle(tilt = bottle.tilt.value, level = 1f - tear.pour.value * 0.55f, slosh = slosh)
                }
            }
        }

        // What to do next.
        when (tear.stage) {
            TearStage.TORN -> HintBubble(
                title = words(R.string.tear_hint),
                hint = words(R.string.tear_tap_glue),
                fix = words(R.string.tear_fix),
                onFix = { scope.launch { tear.fixNow() } },
                progress = null,
            )
            TearStage.POURING -> Unit
            TearStage.RUBBING -> HintBubble(
                title = null,
                hint = words(R.string.tear_rub_glue),
                fix = words(R.string.tear_fix),
                onFix = { scope.launch { tear.fixNow() } },
                progress = { tear.progress },
            )
            TearStage.FIXED -> Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .widthIn(max = 360.dp),
            ) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎉", fontSize = 40.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = words(R.string.tear_fixed),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onDone) { Text(words(R.string.tear_read_story)) }
                }
            }
        }
    }
}

/** The bottle's place (its middle, in px), tilt and size. */
@Stable
private class BottleMotion(corner: Offset) {
    val position = Animatable(corner, Offset.VectorConverter)
    val tilt = Animatable(0f)
    val scale = Animatable(0f)
}

/** The hint next to the bottle, with how far the rubbing has got and Fix it. */
@Composable
private fun BoxScope.HintBubble(title: String?, hint: String, fix: String, onFix: () -> Unit, progress: (() -> Float)?) {
    val pulse by rememberInfiniteTransition(label = "hintPulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 520), RepeatMode.Reverse),
        label = "finger",
    )
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = 16.dp, end = CornerEnd + BottleWidth + 8.dp, bottom = CornerBottom)
            .widthIn(max = 420.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(hint, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (progress == null) "👉" else "👆",
                    fontSize = 18.sp,
                    modifier = Modifier.graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                    },
                )
            }
            if (progress != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(progress = progress, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text("${(progress() * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                }
            }
            TextButton(onClick = onFix, modifier = Modifier.align(Alignment.End)) {
                Text(fix, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * A see-through light blue glue bottle with a darker blue cap and nozzle, glue inside it, and a
 * white label with a smiley. The glue's surface stays level as the bottle [tilt]s (degrees),
 * sloshes gently ([slosh], a phase), and sinks as the glue runs out ([level], 1 full … 0 empty).
 */
private fun DrawScope.drawGlueBottle(tilt: Float, level: Float, slosh: Float) {
    val w = size.width
    val h = size.height
    // Nozzle and cap.
    val nozzle = Path().apply {
        moveTo(w * 0.44f, h * 0.17f)
        lineTo(w * 0.47f, h * 0.02f)
        lineTo(w * 0.53f, h * 0.02f)
        lineTo(w * 0.56f, h * 0.17f)
        close()
    }
    drawPath(nozzle, BottleCap)
    drawRoundRect(BottleCap, topLeft = Offset(w * 0.3f, h * 0.15f), size = Size(w * 0.4f, h * 0.13f), cornerRadius = CornerRadius(w * 0.06f))
    drawRoundRect(Color.White.copy(alpha = 0.35f), topLeft = Offset(w * 0.34f, h * 0.165f), size = Size(w * 0.08f, h * 0.09f), cornerRadius = CornerRadius(w * 0.03f))

    // The body: clear blue plastic.
    val bodyTop = h * 0.26f
    val bodyLeft = w * 0.08f
    val bodySize = Size(w * 0.84f, h - bodyTop - h * 0.02f)
    val body = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(Offset(bodyLeft, bodyTop), bodySize), CornerRadius(w * 0.2f)))
    }
    drawPath(body, Brush.horizontalGradient(listOf(BottleGlassLight, BottleGlass, BottleGlassDeep)))

    // The glue inside: level in the world, so turned back by the bottle's tilt.
    clipPath(body) {
        val centre = Offset(w / 2f, bodyTop + bodySize.height / 2f)
        rotate(degrees = -tilt, pivot = centre) {
            val surface = bodyTop + bodySize.height * (1f - 0.72f * level.coerceIn(0f, 1f))
            val wave = h * 0.012f
            val liquid = Path().apply {
                moveTo(-w, surface)
                val steps = 24
                for (i in 0..steps) {
                    val x = -w + 3f * w * i / steps
                    lineTo(x, surface + sin(slosh + x / w * 5f) * wave)
                }
                lineTo(2f * w, 2f * h)
                lineTo(-w, 2f * h)
                close()
            }
            drawPath(liquid, Brush.verticalGradient(listOf(GlueInsideTop, GlueInsideBottom), startY = surface, endY = h))
            // Its surface catching the light, and a few bubbles.
            drawLine(Color.White.copy(alpha = 0.7f), Offset(bodyLeft, surface), Offset(bodyLeft + bodySize.width, surface), strokeWidth = 1.2.dp.toPx())
            listOf(0.3f to 0.86f, 0.62f to 0.78f, 0.45f to 0.93f, 0.72f to 0.9f).forEach { (fx, fy) ->
                val y = bodyTop + bodySize.height * fy - sin(slosh + fx * 7f) * h * 0.01f
                if (y > surface + 3f) drawCircle(Color.White.copy(alpha = 0.6f), radius = w * 0.035f, center = Offset(w * fx, y))
            }
        }
    }
    drawPath(body, BottleOutline, style = Stroke(width = 1.2.dp.toPx()))
    // The plastic's shine down the left.
    drawRoundRect(Color.White.copy(alpha = 0.75f), topLeft = Offset(w * 0.16f, bodyTop + h * 0.05f), size = Size(w * 0.07f, h * 0.42f), cornerRadius = CornerRadius(w * 0.04f))

    // Label and its smiley.
    val labelTop = h * 0.5f
    drawRoundRect(Color.White.copy(alpha = 0.92f), topLeft = Offset(w * 0.2f, labelTop), size = Size(w * 0.6f, h * 0.28f), cornerRadius = CornerRadius(w * 0.08f))
    val face = Offset(w * 0.5f, labelTop + h * 0.14f)
    val faceR = w * 0.15f
    drawCircle(BottleSmile, radius = faceR, center = face)
    drawCircle(SmileInk, radius = faceR * 0.14f, center = face + Offset(-faceR * 0.38f, -faceR * 0.22f))
    drawCircle(SmileInk, radius = faceR * 0.14f, center = face + Offset(faceR * 0.38f, -faceR * 0.22f))
    drawArc(
        color = SmileInk,
        startAngle = 20f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = face - Offset(faceR * 0.55f, faceR * 0.45f),
        size = Size(faceR * 1.1f, faceR * 1.1f),
        style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round),
    )
}

/** A twinkle where the finger rubbed. */
private class Sparkle(val at: Offset, val born: Long, val seed: Int)

private fun DrawScope.drawSparkle(sparkle: Sparkle, now: Long) {
    val age = ((now - sparkle.born).toFloat() / SPARKLE_MILLIS).coerceIn(0f, 1f)
    if (age >= 1f) return
    val random = Random(sparkle.seed)
    val drift = Offset((random.nextFloat() - 0.5f) * 40f, -20f - random.nextFloat() * 30f) * age
    val r = (6f + random.nextFloat() * 5f).dp.toPx() * (0.4f + age)
    val alpha = 1f - age
    val color = if (random.nextBoolean()) SparkleGold else Color.White
    val c = sparkle.at + drift
    rotate(degrees = age * 90f, pivot = c) {
        drawFourPointStar(c, r, color.copy(alpha = alpha))
    }
}

private fun DrawScope.drawFourPointStar(center: Offset, radius: Float, color: Color) {
    val waist = radius * 0.22f
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

/** A burst of confetti and stars from [origin], falling away. */
@Composable
internal fun BoxScope.Confetti(origin: Offset, seed: Int) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(durationMillis = CONFETTI_MILLIS, easing = LinearEasing)) }
    val pieces = remember(seed) {
        val random = Random(seed)
        List(CONFETTI_PIECES) {
            val angle = (-PI / 2 + (random.nextFloat() - 0.5f) * 2.4f).toFloat()
            val speed = 0.55f + random.nextFloat() * 0.65f
            ConfettiPiece(
                velocity = Offset(cos(angle) * speed, sin(angle) * speed),
                spin = (random.nextFloat() - 0.5f) * 900f,
                color = ConfettiColors[random.nextInt(ConfettiColors.size)],
                isStar = random.nextFloat() < 0.35f,
            )
        }
    }
    Canvas(Modifier.matchParentSize()) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        val reach = size.height
        val alpha = if (t < 0.7f) 1f else (1f - t) / 0.3f
        pieces.forEach { piece ->
            val p = origin + piece.velocity * (t * reach) + Offset(0f, CONFETTI_GRAVITY * t * t * reach)
            if (piece.isStar) {
                drawFourPointStar(p, 7.dp.toPx(), piece.color.copy(alpha = alpha))
            } else {
                rotate(degrees = piece.spin * t, pivot = p) {
                    drawRect(piece.color.copy(alpha = alpha), topLeft = p - Offset(3.dp.toPx(), 5.dp.toPx()), size = Size(6.dp.toPx(), 10.dp.toPx()))
                }
            }
        }
    }
}

private class ConfettiPiece(val velocity: Offset, val spin: Float, val color: Color, val isStar: Boolean)

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


/** The tear is rubbed in stretch by stretch: this many. */
internal const val RUB_STRETCHES = 24

/** Paper fibres and bits along each torn edge. */
private const val FIBRES_PER_EDGE = 46

/** The glue run's width, before it's rubbed in and spreads. */
private val GLUE_WIDTH = 8.dp

/** A stretch counts as rubbed when the finger passes this near its middle; this much rubbing fills it. */
private val RubReach = 32.dp
private val RubToFill = 80.dp

/** The glue bottle: its size, its corner, how it tilts to pour, and how long the pour takes. */
private val BottleWidth = 56.dp
private val BottleHeight = 88.dp
private val CornerEnd = 16.dp
private val CornerBottom = 24.dp
/** Turned over to pour: upside down, leaning 35° from straight down. */
private const val POUR_TILT = 145f
private const val POUR_MILLIS = 1_600

/** Dropped right at the page's edge, the bottle still pours this far in from it. */
private const val DROP_MARGIN = 0.06f
private const val BOTTLE_POP_DELAY_MILLIS = 450L

/** Sparkles: how long each lasts, how often one appears, and how often the finger feels a tick. */
private const val SPARKLE_MILLIS = 480L
private const val SPARKLE_EVERY_MILLIS = 40L
private const val HAPTIC_EVERY_MILLIS = 90L

/** How long "Page fixed!" stays, unless Read story is tapped first. */
private const val FIXED_SHOWN_MILLIS = 3_000L

private const val CONFETTI_PIECES = 46
private const val CONFETTI_MILLIS = 1_600
private const val CONFETTI_GRAVITY = 0.9f

private val EdgeColor = Color(0xFFF7F1E3)
/** The glue: clear light blue, with a deeper rim so it shows on pale paper. */
private val GlueLiquid = Color(0xFFA9DCF7)
private val GlueRim = Color(0xFF3D8FC4)
private val GlueInsideTop = Color(0xCC8FD0F2)
private val GlueInsideBottom = Color(0xE65AB0E2)

/** The bottle: see-through light blue plastic, a darker blue cap, a smiley on its label. */
private val BottleGlassLight = Color(0xD9F2FAFF)
private val BottleGlass = Color(0xBFD6EEFC)
private val BottleGlassDeep = Color(0xB3B4DCF5)
private val BottleOutline = Color(0xFF5BA9D6)
private val BottleCap = Color(0xFF2E86C1)
private val BottleSmile = Color(0xFFFFE07A)
private val SmileInk = Color(0xFF5A3A12)
private val SparkleGold = Color(0xFFFFD54F)
private val ConfettiColors = listOf(VegamRed, Color(0xFFFFC94D), Color(0xFF4FC3F7), Color(0xFF81C784), Color(0xFFF48FB1), Color(0xFFB39DDB))
