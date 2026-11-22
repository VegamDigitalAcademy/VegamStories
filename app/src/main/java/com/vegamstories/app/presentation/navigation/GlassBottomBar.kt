package com.vegamstories.app.presentation.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.math.sin
import kotlin.math.floor
import kotlin.math.ceil
import kotlin.math.PI
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.annotation.RequiresApi
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Shader
import android.os.Build
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import com.vegamstories.app.presentation.preview.ComponentPreviews
import com.vegamstories.app.presentation.preview.PreviewTheme
import com.vegamstories.app.ui.theme.VegamRed

/**
 * Space the bar takes above the system navigation bar: 6 (top gap) + 68 (bar), less the
 * [BarSink] it rests down into the navigation bar's area.
 */
val GlassBottomBarHeight = 70.dp

/**
 * The bar rests this far down into the system navigation bar's area (it used to sit 10dp above
 * it): low, so it covers less of the stories, and still clear of the gesture line at the bottom.
 */
private val BarSink = 4.dp

/** Room a scrolling list leaves at its end for the bar it scrolls under; 0 where there's no bar. */
val LocalBottomBarSpace = staticCompositionLocalOf { 0.dp }

/**
 * Keeps a screen's content clear of the floating bar. The bar floats over the screens instead of
 * resizing them, so screens that show it reserve this fixed space themselves (see VegamNavHost).
 */
fun Modifier.glassBottomBarSpace(): Modifier = navigationBarsPadding().padding(bottom = GlassBottomBarHeight)

/**
 * Floating glass bottom bar, like iPhone's: what scrolls behind it shows through, blurred and a
 * little more colourful, under a light tint, with a bright highlight along its top edge and a
 * hairline border. The selected tab gets a soft lens behind its icon (pink by day, frosted white
 * by night), which slides over from the last tab while the icon pops.
 *
 * [backdrop] is everything under the bar, as drawn (VegamApp). Android before 12 can't blur it:
 * there the bar is a plain translucent pill.
 */
@Composable
fun GlassBottomBar(
    currentDestination: NavDestination?,
    onNavigate: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GraphicsLayer? = null,
) {
    val night = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val blurs = backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val glass = when {
        blurs && night -> NightBlurGlass
        blurs -> DayBlurGlass
        night -> NightGlass
        else -> DayGlass
    }
    val shape = RoundedCornerShape(28.dp)
    // Where the bar is on the screen, to line the backdrop up behind it. Read while drawing only.
    val origin = remember { arrayOf(Offset.Zero) }
    val blurRadius = with(LocalDensity.current) { BlurRadius.toPx() }
    val frosting = remember(blurRadius) { if (blurs) frostedGlass(blurRadius) else null }
    // Off the screen's edge there's nothing recorded: the page colour stands in there.
    val page = MaterialTheme.colorScheme.background
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
            // Keep in sync with GlassBottomBarHeight.
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = (navigationBar - BarSink).coerceAtLeast(4.dp))
            .fillMaxWidth()
            .height(68.dp)
            .onGloballyPositioned { origin[0] = it.positionInRoot() }
            .shadow(if (blurs) 10.dp else 0.dp, shape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
            .clip(shape),
    ) {
        if (backdrop != null && frosting != null) {
            // What's behind the bar, frosted. Its own layer, so only it is blurred, not the tabs.
            // It takes in what's around the bar too (BlurReach), not just the strip behind it:
            // the blur then fades in what's about to pass under the glass, as iPhone's does.
            // Blurring only the strip repeats its edge rows, which flash as bands while scrolling.
            Box(
                Modifier
                    .matchParentSize()
                    .layout { measurable, constraints ->
                        val reach = BlurReach.roundToPx()
                        val wider = measurable.measure(
                            Constraints.fixed(constraints.maxWidth + reach * 2, constraints.maxHeight + reach * 2),
                        )
                        layout(constraints.maxWidth, constraints.maxHeight) { wider.place(-reach, -reach) }
                    }
                    .graphicsLayer { renderEffect = frosting }
                    .drawBehind {
                        drawRect(page)
                        val reach = BlurReach.toPx()
                        translate(reach - origin[0].x, reach - origin[0].y) { drawLayer(backdrop) }
                    },
            )
        }
        // The selected tab's lens: one drop of glass that slides from tab to tab, stretching a little
        // on the way, rather than one fading out and another in.
        val selectedIndex = TopLevelDestination.entries.indexOfFirst { destination ->
            currentDestination?.hierarchy?.any { it.hasRoute(destination.route::class) } == true
        }
        val lensSpots = remember { arrayOfNulls<Rect>(TopLevelDestination.entries.size) }
        val rowOrigin = remember { arrayOf(Offset.Zero) }
        val lensAt = remember { Animatable(selectedIndex.coerceAtLeast(0).toFloat()) }
        val lensShown by animateFloatAsState(if (selectedIndex >= 0) 1f else 0f, label = "lensShown")
        LaunchedEffect(selectedIndex) {
            if (selectedIndex >= 0) lensAt.animateTo(selectedIndex.toFloat(), spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow))
        }
        val lensStretch = with(LocalDensity.current) { LensStretch.toPx() }
        Row(
            modifier = Modifier
                .matchParentSize()
                .onGloballyPositioned { rowOrigin[0] = it.positionInRoot() }
                .background(Brush.verticalGradient(listOf(glass.fillTop, glass.fillBottom)))
                .border(1.dp, Brush.verticalGradient(listOf(glass.edgeTop, glass.edgeBottom)), shape)
                .drawWithContent {
                    drawLens(lensSpots, rowOrigin[0], lensAt.value, lensShown, lensStretch, glass.lens, glass.lensEdge, LensEdgeWidth.toPx())
                    drawContent()
                    // Light catching the upper edge of the glass.
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(glass.sheen, Color.Transparent),
                            startY = 0f,
                            endY = size.height * 0.45f,
                        ),
                        topLeft = Offset.Zero,
                        size = size.copy(height = size.height * 0.45f),
                    )
                }
                .padding(horizontal = 6.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
        // Stories downloaded in the reader: the Downloads tab hops once the bar is back, to show where they went.
        val newDownloads = LocalNewDownloads.current
        TopLevelDestination.entries.forEachIndexed { index, destination ->
            GlassBarItem(
                destination = destination,
                selected = index == selectedIndex,
                hop = destination == TopLevelDestination.DOWNLOADS && newDownloads.count > 0,
                onHopped = newDownloads::shown,
                onLensPlaced = { lensSpots[index] = it },
                // Over the colours seen through the glass, the full text colour reads better than grey.
                idleColor = if (blurs) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { onNavigate(destination) },
                modifier = Modifier.weight(1f),
            )
        }
        }
    }
}

/**
 * The lens at [at] (a tab's index, or between two while sliding), behind the tabs' icons: between
 * two tabs it's drawn between their spots, a little wider mid-way ([stretch]), like a drop moving.
 */
private fun DrawScope.drawLens(
    spots: Array<Rect?>,
    origin: Offset,
    at: Float,
    shown: Float,
    stretch: Float,
    color: Color,
    edge: Color?,
    edgeWidth: Float,
) {
    if (shown <= 0f) return
    val from = spots.getOrNull(floor(at).toInt()) ?: return
    val to = spots.getOrNull(ceil(at).toInt()) ?: from
    val t = at - floor(at)
    val spot = lerp(from, to, t).translate(-origin)
    val extra = stretch * sin(PI.toFloat() * t)
    val topLeft = Offset(spot.left - extra / 2f, spot.top)
    val size = Size(spot.width + extra, spot.height)
    val corner = CornerRadius(spot.height / 2f)
    drawRoundRect(color = color.copy(alpha = color.alpha * shown), topLeft = topLeft, size = size, cornerRadius = corner)
    if (edge != null) {
        // The rim just inside the lens, so it isn't cut by the next tab.
        val inset = edgeWidth / 2f
        drawRoundRect(
            color = edge.copy(alpha = edge.alpha * shown),
            topLeft = topLeft + Offset(inset, inset),
            size = Size(size.width - edgeWidth, size.height - edgeWidth),
            cornerRadius = CornerRadius(corner.x - inset),
            style = Stroke(width = edgeWidth),
        )
    }
}

/** The lens's rim, where it has one (by night). */
private val LensEdgeWidth = 1.dp

/** Blur, then a little more colour (as iPhone's glass does, so what's behind doesn't turn grey). */
@RequiresApi(Build.VERSION_CODES.S)
private fun frostedGlass(radius: Float): RenderEffect {
    val saturate = ColorMatrix().apply { setSaturation(GLASS_SATURATION) }
    return android.graphics.RenderEffect.createChainEffect(
        android.graphics.RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(saturate)),
        android.graphics.RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP),
    ).asComposeRenderEffect()
}

/**
 * How soft what's behind the glass is, how far around the bar it reaches for it (the blur spreads
 * about twice its radius), and how much more colourful it is (a little: more makes moving blobs).
 */
private val BlurRadius = 26.dp

/** How much wider the lens gets half-way between two tabs. */
private val LensStretch = 18.dp

/** A new download's hops on the Downloads tab: after the bar has slid in, how many, and how high. */
private const val TAB_HOP_DELAY_MILLIS = NAV_ANIM_MS + 150L
private const val TAB_HOPS = 2
private val TabHopHeight = 9.dp
private val BlurReach = BlurRadius * 2
private const val GLASS_SATURATION = 1.25f

@Composable
private fun GlassBarItem(
    destination: TopLevelDestination,
    selected: Boolean,
    /** Where the lens goes behind this tab's icon, on the screen. */
    onLensPlaced: (Rect) -> Unit,
    idleColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Something new is in this tab: its icon hops twice, with a dot, once the bar has slid in; then [onHopped]. */
    hop: Boolean = false,
    onHopped: () -> Unit = {},
) {
    val primary = MaterialTheme.colorScheme.primary
    val hopUp = remember { Animatable(0f) }
    val currentOnHopped by rememberUpdatedState(onHopped)
    LaunchedEffect(hop) {
        if (!hop) return@LaunchedEffect
        delay(TAB_HOP_DELAY_MILLIS)
        repeat(TAB_HOPS) {
            hopUp.animateTo(1f, tween(durationMillis = 170, easing = FastOutSlowInEasing))
            hopUp.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
        }
        currentOnHopped()
    }
    val contentColor by animateColorAsState(
        if (selected) primary else idleColor,
        label = "tabContent",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tabIconScale",
    )
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(22.dp))
            .selectable(selected = selected, onClick = onClick, role = Role.Tab),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .onGloballyPositioned { onLensPlaced(it.boundsInRoot()) }
                .padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(if (selected) destination.selectedIcon else destination.unselectedIcon),
                contentDescription = null, // the label below names the tab
                tint = contentColor,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        // Up and a little bigger on each hop; the spring back squashes it slightly.
                        translationY = -TabHopHeight.toPx() * hopUp.value
                        scaleX = iconScale * (1f + 0.22f * hopUp.value)
                        scaleY = iconScale * (1f + 0.22f * hopUp.value)
                    },
            )
            if (hop) {
                // A dot on the icon's top right corner, just outside it.
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 5.dp, y = 1.dp)
                        .size(8.dp)
                        .background(primary, CircleShape),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(destination.labelRes),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
        )
    }
}

private class GlassColors(
    val fillTop: Color,
    val fillBottom: Color,
    val edgeTop: Color,
    val edgeBottom: Color,
    val sheen: Color,
    /** Behind the selected tab's icon. */
    val lens: Color,
    /** A thin rim round [lens], or none. */
    val lensEdge: Color? = null,
)

/**
 * Frosted glass, by day: the stories behind show through as soft colour, under enough white that
 * the labels read the same over a dark cover as over a light one.
 */
private val DayBlurGlass = GlassColors(
    fillTop = Color.White.copy(alpha = 0.58f),
    fillBottom = Color.White.copy(alpha = 0.46f),
    edgeTop = Color.White.copy(alpha = 0.90f),
    edgeBottom = Color.White.copy(alpha = 0.30f),
    sheen = Color.White.copy(alpha = 0.30f),
    lens = VegamRed.copy(alpha = 0.16f),
)

/** Clear glass, by night: darkened a little, so the labels stay readable over bright covers. */
private val NightBlurGlass = GlassColors(
    fillTop = Color.Black.copy(alpha = 0.42f),
    fillBottom = Color.Black.copy(alpha = 0.52f),
    edgeTop = Color.White.copy(alpha = 0.28f),
    edgeBottom = Color.White.copy(alpha = 0.06f),
    sheen = Color.White.copy(alpha = 0.08f),
    // A soft red tint with a thin red rim: a tinted glass lens that goes with the red icon, rather
    // than a flat grey pill (which looked dull, like a pressed button) or a solid maroon block.
    lens = VegamRed.copy(alpha = 0.2f),
    lensEdge = VegamRed.copy(alpha = 0.45f),
)

/** Milky glass on the light background, where it can't blur (Android before 12). */
private val DayGlass = GlassColors(
    fillTop = Color.White.copy(alpha = 0.78f),
    fillBottom = Color.White.copy(alpha = 0.58f),
    edgeTop = Color.White.copy(alpha = 0.95f),
    edgeBottom = VegamRed.copy(alpha = 0.10f),
    sheen = Color.White.copy(alpha = 0.45f),
    lens = VegamRed.copy(alpha = 0.16f),
)

/** Smoked glass on the dark background, where it can't blur. */
private val NightGlass = GlassColors(
    fillTop = Color.White.copy(alpha = 0.12f),
    fillBottom = Color.White.copy(alpha = 0.05f),
    edgeTop = Color.White.copy(alpha = 0.22f),
    edgeBottom = Color.White.copy(alpha = 0.05f),
    sheen = Color.White.copy(alpha = 0.06f),
    // As by night with blur: a light red tint and a thin red rim (a stronger tint turns into a heavy maroon block).
    lens = VegamRed.copy(alpha = 0.16f),
    lensEdge = VegamRed.copy(alpha = 0.4f),
)

// ---------------- Previews ----------------

@ComponentPreviews
@Composable
private fun GlassBottomBarPreview() = PreviewTheme {
    GlassBottomBar(currentDestination = null, onNavigate = {})
}
