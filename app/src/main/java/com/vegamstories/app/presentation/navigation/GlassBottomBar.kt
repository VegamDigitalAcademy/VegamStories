package com.vegamstories.app.presentation.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
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

/** Space the bar takes above the system navigation bar: 6 (top gap) + 68 (bar) + 10 (bottom gap). */
val GlassBottomBarHeight = 84.dp

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
 * by night), which fades in while the icon pops.
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
    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp) // keep in sync with GlassBottomBarHeight
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
        Row(
            modifier = Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(glass.fillTop, glass.fillBottom)))
                .border(1.dp, Brush.verticalGradient(listOf(glass.edgeTop, glass.edgeBottom)), shape)
                .drawWithContent {
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
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any { it.hasRoute(destination.route::class) } == true
            GlassBarItem(
                destination = destination,
                selected = selected,
                lens = glass.lens,
                // Over the colours seen through the glass, the full text colour reads better than grey.
                idleColor = if (blurs) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { onNavigate(destination) },
                modifier = Modifier.weight(1f),
            )
        }
        }
    }
}

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
private val BlurReach = BlurRadius * 2
private const val GLASS_SATURATION = 1.25f

@Composable
private fun GlassBarItem(
    destination: TopLevelDestination,
    selected: Boolean,
    lens: Color,
    idleColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val contentColor by animateColorAsState(
        if (selected) primary else idleColor,
        label = "tabContent",
    )
    val lensColor by animateColorAsState(
        if (selected) lens else Color.Transparent,
        label = "tabLens",
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
                .clip(RoundedCornerShape(50))
                .background(lensColor)
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
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
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
    lens = Color.White.copy(alpha = 0.14f),
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
    // Neutral, not red: a red tint on smoked glass turns into a heavy maroon block.
    lens = Color.White.copy(alpha = 0.10f),
)

// ---------------- Previews ----------------

@ComponentPreviews
@Composable
private fun GlassBottomBarPreview() = PreviewTheme {
    GlassBottomBar(currentDestination = null, onNavigate = {})
}
