package com.vegamstories.app.presentation.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** The roll's radius, as a share of the page's shorter side. */
private const val CURL_RADIUS = 0.07f

/** Bands the roll is drawn in (even: half up its front, half over its back), enough to look round. */
private const val CURL_BANDS = 10

/** How far the fold leans at most, for a finger at the page's top or bottom edge. */
private const val MAX_LEAN_DEGREES = 20.0

/** A page the app turns (auto-turn, Continue) lifts its bottom corner first. */
internal const val AUTO_TURN_LEAN = 0.5f

/** Darkness at the roll's outer edge, where it turns side-on to the light. */
private const val ROLL_SHADE = 0.32f

/** Paper colour laid over the back of the page: the print shows through only faintly. */
private const val BACK_WASH = 0.82f

/** The roll's shadow on the page underneath: darkness, and width in radii. */
private const val UNDER_SHADOW = 0.3f
private const val UNDER_SHADOW_WIDTH = 1.5f

/** The shadow the turned-back page's loose edge casts on the page: darkness, and width in radii. */
private const val EDGE_SHADOW = 0.18f
private const val EDGE_SHADOW_WIDTH = 0.6f

/** How far each band reaches into the one drawn before it (px), so no seam shows between them. */
private const val SEAM = 1f

/**
 * Draws this page curling away as in Apple Books, [progress] of the way through a turn (0 or less
 * = lying flat, 1 = turned away). Past a straight fold the page rolls up around a cylinder lying
 * on it and comes back over itself, so its back shows, the print faintly through it. Where it has
 * lifted, the page underneath shows, in the roll's shadow. The fold sweeps from the right edge to
 * the left, leaning by [lean] (see [PageCurl]). [paper] is the colour of the page's back.
 * Both lambdas are read while drawing, so a turn redraws the page without recomposing it.
 */
internal fun Modifier.pageCurl(progress: () -> Float, lean: () -> Float, paper: Color): Modifier = drawWithCache {
    if (size.minDimension <= 0f) return@drawWithCache onDrawWithContent { drawContent() }
    val radius = size.minDimension * CURL_RADIUS
    val bands = curlBands(radius)
    // Shapes rebuilt every frame without allocating: one per band, the flat page, the turned-back
    // page, and the roll's shading and shadow.
    val paths = List(bands.size + 4) { Path() }
    val matrix = Matrix()
    // The roll is drawn in bands, from the page rendered as one picture (front) and washed (back):
    // at a band's edge the whole page then blends in, not its background on its own, so no seam shows.
    val front = obtainGraphicsLayer().apply { compositingStrategy = CompositingStrategy.Offscreen }
    val back = obtainGraphicsLayer().apply { colorFilter = paperWash(paper) }
    onDrawWithContent {
        val turned = progress()
        when {
            turned <= 0f -> drawContent()
            turned >= 1f -> Unit // turned away
            else -> {
                front.record { this@onDrawWithContent.drawContent() }
                back.record { drawLayer(front) }
                drawCurl(PageCurl(size.width, size.height, turned, lean(), radius), bands, paths, matrix, front, back)
            }
        }
    }
}

/** Shows the print only faintly, through paper of colour [paper]: the back of the page. */
private fun paperWash(paper: Color): ColorFilter {
    val print = 1f - BACK_WASH
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                print, 0f, 0f, 0f, BACK_WASH * paper.red * 255f,
                0f, print, 0f, 0f, BACK_WASH * paper.green * 255f,
                0f, 0f, print, 0f, BACK_WASH * paper.blue * 255f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}

/**
 * Where the fold of a page curling away lies: a straight line across the page, [progress] of the
 * way from just past its right edge (nothing lifted yet) to where even the roll has left the page
 * on the left. It leans by [lean] (-1 … 1, up to [MAX_LEAN_DEGREES]): + lifts the bottom corner
 * first, - the top one. Distances are measured across the fold, growing toward the lifted side.
 */
internal class PageCurl(val width: Float, val height: Float, progress: Float, lean: Float, val radius: Float) {
    /** Unit vector across the fold, toward the lifted side. */
    val nx: Float
    val ny: Float

    /** The fold's middle point. */
    val fx: Float
    val fy: Float = height / 2f

    init {
        val angle = lean.coerceIn(-1f, 1f) * MAX_LEAN_DEGREES * PI / 180.0
        nx = cos(angle).toFloat()
        ny = sin(angle).toFloat()
        // How far sideways the fold's ends are from its middle.
        val slant = abs(ny / nx) * height / 2f
        val start = width + slant
        val end = -radius / nx - slant
        fx = start + (end - start) * progress
    }

    /** How far the point [x], [y] is across the fold. */
    fun distance(x: Float, y: Float): Float = (x - fx) * nx + (y - fy) * ny

    /** The point [d] across the fold from its middle. */
    fun at(d: Float): Offset = Offset(fx + d * nx, fy + d * ny)
}

/**
 * One band of the roll: the page's points [from]..[to] across the fold (an arc of the cylinder) are
 * drawn [drawnFrom]..[drawnTo] across it, as seen from above. [isBack]: the arc is on the cylinder's
 * upper half, where the page has turned over.
 */
internal class CurlBand(
    val from: Float,
    val to: Float,
    val drawnFrom: Float,
    val drawnTo: Float,
    val isBack: Boolean,
) {
    /** The band is drawn at scale·d + shift for a point d across the fold: straight between its ends. */
    val scale = (drawnTo - drawnFrom) / (to - from)
    val shift = drawnFrom - scale * from
}

/**
 * The roll in [count] bands, from where the page leaves the paper (on the fold) once around half
 * the cylinder: up its side, front showing, then over the top, back showing, down to where the rest
 * of the page lies back flat.
 */
internal fun curlBands(radius: Float, count: Int = CURL_BANDS): List<CurlBand> = List(count) { i ->
    val a = PI * i / count
    val b = PI * (i + 1) / count
    CurlBand(
        from = (radius * a).toFloat(),
        to = (radius * b).toFloat(),
        drawnFrom = (radius * sin(a)).toFloat(),
        drawnTo = (radius * sin(b)).toFloat(),
        isBack = 2 * i >= count,
    )
}

/**
 * Darkness of the roll [across] the fold ([radius] = its outer edge), as seen from above. The page
 * there faces away from the light by the same angle whichever side shows (its front rolling up, or
 * its back coming over), so the shading depends only on where it is drawn.
 */
internal fun rollShade(across: Float, radius: Float): Float {
    val x = (across / radius).coerceIn(0f, 1f)
    return ROLL_SHADE * (1f - sqrt(1f - x * x))
}

/** [rollShade] as gradient stops from the fold (0) to the roll's outer edge (1). */
private val rollShadeStops: Array<Pair<Float, Color>> =
    floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 0.8f, 0.9f, 0.95f, 1f)
        .map { x -> x to Color.Black.copy(alpha = rollShade(x, 1f)) }
        .toTypedArray()

/**
 * Sets this to draw the page so that its point d across [curl]'s fold lands at [scale]·d + [shift]
 * across it, staying where it was along the fold.
 */
internal fun Matrix.setAcross(curl: PageCurl, scale: Float, shift: Float) {
    val k = scale - 1f
    val move = shift - k * (curl.fx * curl.nx + curl.fy * curl.ny)
    reset()
    this[0, 0] = 1f + k * curl.nx * curl.nx
    this[0, 1] = k * curl.nx * curl.ny
    this[1, 0] = k * curl.nx * curl.ny
    this[1, 1] = 1f + k * curl.ny * curl.ny
    this[3, 0] = move * curl.nx
    this[3, 1] = move * curl.ny
}

/** Sets this to the strip of the page between [from] and [to] across [curl]'s fold. */
private fun Path.setBand(curl: PageCurl, from: Float, to: Float): Path {
    // Along the fold, far enough to pass every corner of the page.
    val reach = 2f * (curl.width + curl.height)
    val ax = -curl.ny * reach
    val ay = curl.nx * reach
    val a = curl.at(from)
    val b = curl.at(to)
    reset()
    moveTo(a.x - ax, a.y - ay)
    lineTo(b.x - ax, b.y - ay)
    lineTo(b.x + ax, b.y + ay)
    lineTo(a.x + ax, a.y + ay)
    close()
    return this
}

/**
 * Draws the page in parts, bottom up: the roll's shadow on the page underneath, the page lying flat
 * up to the fold, the roll band by band and its shading, and the rest of the page lying back over
 * it. [front] and [back] hold the page's two sides.
 */
private fun ContentDrawScope.drawCurl(
    curl: PageCurl,
    bands: List<CurlBand>,
    paths: List<Path>,
    matrix: Matrix,
    front: GraphicsLayer,
    back: GraphicsLayer,
) {
    val r = curl.radius
    val behind = -2f * (curl.width + curl.height) // past every corner, on the flat side
    clipRect {
        // First, so the page covers it wherever the page still lies.
        drawPath(
            paths[0].setBand(curl, 0f, r * (1f + UNDER_SHADOW_WIDTH)),
            Brush.linearGradient(
                0f to Color.Black.copy(alpha = UNDER_SHADOW),
                1f to Color.Transparent,
                start = curl.at(r),
                end = curl.at(r * (1f + UNDER_SHADOW_WIDTH)),
            ),
        )
        clipPath(paths[1].setBand(curl, behind, 0f)) { this@drawCurl.drawContent() }
        bands.forEachIndexed { i, band ->
            val low = minOf(band.drawnFrom, band.drawnTo)
            val high = maxOf(band.drawnFrom, band.drawnTo)
            // The band drawn before is nearer the fold on the way up, further from it on the way back.
            val clip = when {
                !band.isBack -> paths[i + 2].setBand(curl, low - SEAM, high)
                high < r -> paths[i + 2].setBand(curl, low, high + SEAM)
                else -> paths[i + 2].setBand(curl, low, high) // the roll's outer edge
            }
            matrix.setAcross(curl, band.scale, band.shift)
            withTransform({
                clipPath(clip)
                transform(matrix)
            }) {
                drawLayer(if (band.isBack) back else front)
            }
        }
        drawPath(
            paths[bands.size + 2].setBand(curl, 0f, r),
            Brush.linearGradient(*rollShadeStops, start = curl.at(0f), end = curl.at(r)),
        )
        // Past half the cylinder the page lies flat again, back up, over the page it came from.
        matrix.setAcross(curl, scale = -1f, shift = PI.toFloat() * r)
        withTransform({
            clipPath(paths[bands.size + 3].setBand(curl, behind, SEAM))
            transform(matrix)
        }) {
            drawLayer(back)
            // Its loose edge (the page's outer edge) shades the page beside it.
            val shadow = r * EDGE_SHADOW_WIDTH
            drawRect(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = EDGE_SHADOW),
                    1f to Color.Transparent,
                    startX = size.width,
                    endX = size.width + shadow,
                ),
                topLeft = Offset(size.width, 0f),
                size = Size(shadow, size.height),
            )
        }
    }
}

/**
 * Which way the fold of a turning page leans (see [PageCurl]). A finger leans it toward the corner
 * it holds the page by, which leads the way it moves; a page the app turns leans [AUTO_TURN_LEAN].
 */
@Stable
internal class PageLean {
    /** Where the finger puts it. */
    var target by mutableFloatStateOf(AUTO_TURN_LEAN)

    /** What the curl draws: follows [target], smoothly mid-turn so the fold never jumps. */
    val current = Animatable(AUTO_TURN_LEAN)

    /** Keeps [current] on [target], back to the app's own lean whenever the pages lie still. */
    suspend fun follow(pagerState: PagerState) = coroutineScope {
        launch {
            snapshotFlow { pagerState.isScrollInProgress }.collect { turning ->
                if (!turning) target = AUTO_TURN_LEAN
            }
        }
        snapshotFlow { target }.collectLatest { lean ->
            // Nothing shows it while no page turns: jump there. Mid-turn, swing over.
            if (pagerState.isScrollInProgress) {
                current.animateTo(lean, spring(stiffness = Spring.StiffnessMediumLow))
            } else {
                current.snapTo(lean)
            }
        }
    }
}

/**
 * Leans the fold of the turning page (see [PageLean]) after the finger: watches it, without
 * taking any of its moves from the pager.
 */
internal fun Modifier.leanWithFinger(lean: PageLean, pagerState: PagerState): Modifier =
    pointerInput(lean, pagerState) {
        val step = viewConfiguration.touchSlop / 4f
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var lastX = down.position.x
            var forward: Boolean? = null
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) break
                val moved = change.position.x - lastX
                if (abs(moved) >= step) {
                    forward = moved < 0f
                    lastX = change.position.x
                }
                forward?.let { lean.target = fingerLean(change.position.y, size.height.toFloat(), forward = it) }
            }
            // A tap, not a turn: the app's own turns lean as before.
            if (!pagerState.isScrollInProgress) lean.target = AUTO_TURN_LEAN
        }
    }

/**
 * The lean for a finger at [y] on a page [height] tall, turning it [forward] (moving left) or back:
 * toward the corner nearest the finger, leading the way it moves.
 */
internal fun fingerLean(y: Float, height: Float, forward: Boolean): Float {
    val grip = (2f * y / height - 1f).coerceIn(-1f, 1f) // -1 top edge … +1 bottom edge
    return if (forward) grip else -grip
}
