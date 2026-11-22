package com.vegamstories.app.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import android.os.Build
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin

/**
 * Pull to refresh as a cartoon hand. Pulling opens a gap above the list (see [followsPull]) and a
 * hand in a navy sleeve reaches down into it; while the stories load it sways and wiggles its
 * fingers; once they've loaded, it closes into a fist on the top of the list and pulls the page up
 * into it, crumpling it like cloth ([PageWrap]); then the list springs back up into place, the page
 * smoothing out, as the hand lets go and goes back up.
 */
@Stable
class HandRefresh internal constructor() {
    /** Shown as refreshing (the list held down), until the hand has grabbed the list. */
    var showing by mutableStateOf(false)
        internal set

    /** How far into its sway it is: 0 still … 1 swaying fully. */
    internal val swayIn = Animatable(0f)

    /** 0 an open hand … 1 a fist. */
    internal val close = Animatable(0f)

    /** The fist pulling the list back up: 0 where it was … 1 back in place (overshooting a little). */
    internal val lift = Animatable(0f)

    /** The hand going back up once it's let go: 0 … 1 out of sight. */
    internal val away = Animatable(0f)

    /** The page pulled up into the fist: 0 flat … 1 pulled up and creased (a little past 0 as it springs back). */
    internal val wrap = Animatable(0f)

    /** Draws the page crumpling (Android 13+); without it, creases are drawn over it instead. */
    internal val pageWrap: PageWrap? by lazy { PageWrap.orNull() }

    /** The list follows the fist ([lift]), not the pull; it was pulled [liftedFrom] of the gap. */
    internal var lifting by mutableStateOf(false)
    internal var liftedFrom by mutableStateOf(1f)
}

/** The refresh for [state], [loading] while the stories load. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberHandRefresh(state: PullToRefreshState, loading: Boolean): HandRefresh {
    val refresh = remember { HandRefresh() }
    LaunchedEffect(loading) {
        if (loading) {
            refresh.lifting = false
            refresh.close.snapTo(0f)
            refresh.lift.snapTo(0f)
            refresh.away.snapTo(0f)
            refresh.wrap.snapTo(0f)
            refresh.showing = true
            refresh.swayIn.snapTo(0f)
            refresh.swayIn.animateTo(1f, tween(SWAY_IN_MILLIS))
        } else if (refresh.showing) {
            // Loaded: the hand closes on the top of the list…
            refresh.close.animateTo(1f, tween(CLOSE_MILLIS, easing = FastOutLinearInEasing))
            // …pulls the page up into its fist, crumpling it like cloth…
            refresh.wrap.animateTo(1f, tween(WRAP_MILLIS, easing = FastOutSlowInEasing))
            // …drags the list back up, unhurried, still holding the page (it settles a touch past
            // where it rests)…
            refresh.liftedFrom = state.distanceFraction
            refresh.lifting = true
            refresh.showing = false
            val dragging = launch { refresh.lift.animateTo(1f, tween(DRAG_MILLIS, easing = DragEasing)) }
            // …and near the top lets go: the page smooths out with a wobble as the hand goes back up.
            delay(LET_GO_MILLIS)
            launch { refresh.away.animateTo(1f, tween(AWAY_MILLIS, easing = FastOutLinearInEasing)) }
            val smoothing = launch {
                refresh.wrap.animateTo(0f, spring(dampingRatio = UNWRAP_DAMPING, stiffness = Spring.StiffnessMediumLow))
            }
            dragging.join()
            snapshotFlow { state.distanceFraction }.first { it <= 0.001f }
            smoothing.join()
            refresh.lifting = false
            refresh.close.snapTo(0f)
            refresh.lift.snapTo(0f)
            refresh.away.snapTo(0f)
        }
    }
    return refresh
}

/** How far down the list is, as a part of the gap above it. */
@OptIn(ExperimentalMaterial3Api::class)
private fun pulled(state: PullToRefreshState, refresh: HandRefresh): Float =
    if (refresh.lifting) refresh.liftedFrom * (1f - refresh.lift.value) else state.distanceFraction

/**
 * The hand, drawn in the gap above the list (over the whole pull-to-refresh box, without taking
 * touches).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.HandRefreshIndicator(state: PullToRefreshState, refresh: HandRefresh) {
    val creases = MaterialTheme.colorScheme.onBackground
    // While the stories load: its sway and its fingers wiggling, looping.
    val time: () -> Float = if (refresh.showing) {
        val loop = rememberInfiniteTransition(label = "refreshing")
        val phase = loop.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(SWAY_MILLIS, easing = LinearEasing), RepeatMode.Restart),
            label = "sway",
        );
        { phase.value }
    } else {
        { 0f }
    }
    Canvas(Modifier.matchParentSize()) {
        val u = HAND_UNIT.toPx()
        val listTop = pulled(state, refresh) * REFRESH_GAP.toPx()
        val closed = refresh.close.value
        val gone = refresh.away.value
        // Hanging, its fingertips just above the list; closing, it reaches down onto the list's top
        // edge; then it rides up with it, and once it lets go it goes back up out of sight.
        val hanging = listTop - REACH_GAP.toPx() - HAND_LENGTH * u
        val gripping = listTop + GRIP_INTO.toPx() - FIST_LENGTH * u
        // Pulling the page up, the fist rises with it.
        val pulledUp = refresh.wrap.value.coerceAtLeast(0f) * WRAP_PULL.toPx()
        val wrist = hanging + (gripping - hanging) * closed - pulledUp - gone * (listTop + HAND_LENGTH * u + 80.dp.toPx())
        if (wrist + HAND_LENGTH * u <= 0f) return@Canvas
        val anchor = Offset(size.width / 2f, -12.dp.toPx())
        val t = time()
        val sway = SWAY_DEGREES * refresh.swayIn.value * sin(2f * PI.toFloat() * t) * (1f - closed)

        // Without the page crumpling (before Android 13): creases drawn under the fist instead.
        if (refresh.wrap.value > 0f && refresh.pageWrap == null) {
            val strength = refresh.wrap.value.coerceIn(0f, 1f) * (1f - gone)
            drawCreases(Offset(anchor.x, listTop + GRIP_INTO.toPx() - pulledUp), creases, strength)
        }
        rotate(sway, pivot = anchor) {
            translate(anchor.x, wrist) {
                drawSleeve(length = wrist - anchor.y, u = u)
                drawHand(closed, wiggle = { i -> FINGER_WIGGLE * refresh.swayIn.value * (1f - closed) * sin(2f * PI.toFloat() * (t * 3f) + i * 0.9f) }, u = u)
            }
        }
    }
}

/**
 * The list under [HandRefreshIndicator]: pulled down with the pull, opening a gap above it for the
 * hand; held down while the stories load; then pulled back up by the fist.
 */
@OptIn(ExperimentalMaterial3Api::class)
fun Modifier.followsPull(state: PullToRefreshState, refresh: HandRefresh): Modifier = graphicsLayer {
    translationY = pulled(state, refresh) * REFRESH_GAP.toPx()
    // The page pulled up into the fist, crumpling like cloth (Android 13+).
    val wrapped = refresh.wrap.value
    val wrap = if (abs(wrapped) > 0.002f) refresh.pageWrap else null
    renderEffect = if (wrap != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // The fist rises [WRAP_PULL], gathering more of the page than that up into it (the card
        // under the heading reaches right up to the fist).
        val risen = wrapped * WRAP_PULL.toPx()
        wrap.effect(
            pinch = Offset(size.width / 2f, GRIP_INTO.toPx() - risen),
            pull = wrapped * (WRAP_PULL + WRAP_BUNCH).toPx(),
            reach = WRAP_REACH.toPx(),
            creases = wrapped.coerceAtLeast(0f),
            gather = WRAP_GATHER * wrapped,
        )
    } else {
        null
    }
}

/** The navy sleeve, [length] up from the wrist (at 0, 0), with an orange cuff. */
private fun DrawScope.drawSleeve(length: Float, u: Float) {
    if (length <= 0f) return
    val half = 10f * u
    drawRect(
        Brush.horizontalGradient(
            0f to NAVY_DARK,
            0.45f to NAVY_LIGHT,
            1f to NAVY_DARK,
            startX = -half,
            endX = half,
        ),
        topLeft = Offset(-half, -length),
        size = Size(half * 2f, length - 4f * u),
    )
    drawRoundRect(ORANGE, topLeft = Offset(-12f * u, -9f * u), size = Size(24f * u, 9f * u), cornerRadius = CornerRadius(3f * u))
    drawRoundRect(ORANGE_DARK, topLeft = Offset(-12f * u, -9f * u), size = Size(24f * u, 9f * u), cornerRadius = CornerRadius(3f * u), style = Stroke(1.2f * u))
}

/**
 * A cartoon hand hanging from the wrist (at 0, 0), the back of it towards us, [closed] from open
 * (0) to a fist (1). [wiggle] tilts each finger a little (degrees, by finger 0 … 3).
 */
private fun DrawScope.drawHand(closed: Float, wiggle: (Int) -> Float, u: Float) {
    val stroke = 1.3f * u
    // Fingers, behind the palm, curling away as it closes.
    for (i in 0..3) {
        val length = FINGER_LENGTHS[i] * (1f - closed)
        if (length > 0.5f) {
            capsule(
                base = Offset(FINGER_X[i] * u, 17f * u),
                length = length * u,
                width = 5.6f * u,
                degrees = FINGER_SPREAD[i] * (1f - closed) + wiggle(i),
                stroke = stroke,
            )
        }
    }
    // Knuckles, as it closes into a fist.
    if (closed > 0f) {
        for (i in 0..3) {
            val knuckle = Offset(FINGER_X[i] * 1.08f * u, 21.5f * u)
            drawCircle(SKIN.copy(alpha = closed), 3.5f * u, knuckle)
            drawCircle(OUTLINE.copy(alpha = closed), 3.5f * u, knuckle, style = Stroke(stroke))
        }
    }
    // The palm (its back), a little rounder as a fist, with a soft shade down one side.
    val palmWidth = (22f + 2f * closed) * u
    val palmHeight = (20f + 2f * closed) * u
    val palmTopLeft = Offset(-palmWidth / 2f, 0f)
    drawRoundRect(SKIN, palmTopLeft, Size(palmWidth, palmHeight), CornerRadius(8f * u))
    drawRoundRect(SKIN_SHADE, Offset(palmWidth / 2f - 6f * u, 3f * u), Size(3.5f * u, palmHeight - 7f * u), CornerRadius(2f * u))
    drawRoundRect(OUTLINE, palmTopLeft, Size(palmWidth, palmHeight), CornerRadius(8f * u), style = Stroke(stroke))
    // The thumb: out to the side, open; across the front of the fist, closed.
    capsule(
        base = Offset((-10f - 1f * closed) * u, (6f + 7f * closed) * u),
        length = (11f + 2f * closed) * u,
        width = 6f * u,
        degrees = 35f + (-125f) * closed,
        stroke = stroke,
    )
}

/** A rounded finger from [base], [length] long, turned [degrees] from pointing straight down. */
private fun DrawScope.capsule(base: Offset, length: Float, width: Float, degrees: Float, stroke: Float) {
    rotate(degrees, pivot = base) {
        val topLeft = Offset(base.x - width / 2f, base.y - width / 2f)
        val size = Size(width, length + width / 2f)
        drawRoundRect(SKIN, topLeft, size, CornerRadius(width / 2f))
        drawRoundRect(OUTLINE, topLeft, size, CornerRadius(width / 2f), style = Stroke(stroke))
    }
}

/**
 * Creases in the list fanning out from where the fist holds it, at [from], as in pulled cloth:
 * each a soft shadow with a thin highlight beside it, curving outwards; [strength] 0 … 1.
 */
private fun DrawScope.drawCreases(from: Offset, light: Color, strength: Float) {
    for (degrees in CREASE_ANGLES) {
        val angle = degrees * (PI.toFloat() / 180f)
        val length = (CREASE_LENGTH - abs(degrees) * 0.3f).dp.toPx()
        val outwards = sign(degrees)
        val start = from + Offset(sin(angle) * 6.dp.toPx(), 0f)
        val bend = from + Offset(sin(angle) * length * 0.25f + outwards * 6.dp.toPx(), cos(angle) * length * 0.6f)
        val end = from + Offset(sin(angle) * length, cos(angle) * length)
        val crease = Path().apply {
            moveTo(start.x, start.y)
            quadraticTo(bend.x, bend.y, end.x, end.y)
        }
        drawPath(crease, Color.Black.copy(alpha = 0.4f * strength), style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round))
        translate(left = 1.4.dp.toPx() * outwards) {
            drawPath(crease, light.copy(alpha = 0.2f * strength), style = Stroke(1.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

/** The gap pulling opens above the list; the hand hangs this far above it. */
private val REFRESH_GAP = 104.dp
private val REACH_GAP = 10.dp

/** The hand's size: one unit of its drawing, and its length (wrist to fingertips; as a fist). */
private val HAND_UNIT = 1.35.dp
private const val HAND_LENGTH = 36f
private const val FIST_LENGTH = 25f

/** The fist grips the list this far down from its top edge (past its padding, at its heading). */
private val GRIP_INTO = 22.dp

/** The creases under the fist: their angles from straight down, and how long the middle ones are (dp). */
private val CREASE_ANGLES = floatArrayOf(-36f, -20f, -7f, 7f, 20f, 36f)
private const val CREASE_LENGTH = 40f

/** The fingers, index to little finger: where along the palm, how long, how spread. */
private val FINGER_X = floatArrayOf(-7.6f, -2.5f, 2.6f, 7.6f)
private val FINGER_LENGTHS = floatArrayOf(14f, 17f, 16f, 12.5f)
private val FINGER_SPREAD = floatArrayOf(9f, 3f, -3f, -9f)

/** While loading: how far it sways each way, one sway there and back, how soon it sways fully, how much the fingers wiggle. */
private const val SWAY_DEGREES = 12f
private const val SWAY_MILLIS = 1_800
private const val SWAY_IN_MILLIS = 500
private const val FINGER_WIGGLE = 5f

/**
 * Once loaded: how fast it makes a fist, and pulls the page up into it; how long it takes to drag
 * the list back up (ending a touch past where it rests, and settling), when on the way it lets go,
 * and how fast it then goes.
 */
private const val CLOSE_MILLIS = 200
private const val WRAP_MILLIS = 650
private const val DRAG_MILLIS = 650
private val DragEasing = CubicBezierEasing(0.35f, 0f, 0.25f, 1.08f)
private const val LET_GO_MILLIS = 420L
private const val AWAY_MILLIS = 300

/** The page pulled into the fist: how far up, how far around it's pulled, how much drawn in, and its wobble smoothing out. */
private val WRAP_PULL = 40.dp
private val WRAP_BUNCH = 30.dp
private val WRAP_REACH = 150.dp
private const val WRAP_GATHER = 0.35f
private const val UNWRAP_DAMPING = 0.4f

/** A warm cartoon skin, the app's navy sleeve and the logo's orange cuff. */
private val SKIN = Color(0xFFF7C9A9)
private val SKIN_SHADE = Color(0xFFE8AE8E)
private val OUTLINE = Color(0xFF8A4F3A)
private val NAVY_DARK = Color(0xFF1B1A45)
private val NAVY_LIGHT = Color(0xFF34337A)
private val ORANGE = Color(0xFFF59E0B)
private val ORANGE_DARK = Color(0xFFB8740A)
