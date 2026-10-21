package com.vegam.myapplication.presentation.navigation

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
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import com.vegam.myapplication.presentation.preview.ComponentPreviews
import com.vegam.myapplication.presentation.preview.PreviewTheme
import com.vegam.myapplication.ui.theme.VegamRed

/** Space the bar takes above the system navigation bar: 6 (top gap) + 68 (bar) + 10 (bottom gap). */
val GlassBottomBarHeight = 84.dp

/**
 * Keeps a screen's content clear of the floating bar. The bar floats over the screens instead of
 * resizing them, so screens that show it reserve this fixed space themselves (see VegamNavHost).
 */
fun Modifier.glassBottomBarSpace(): Modifier = navigationBarsPadding().padding(bottom = GlassBottomBarHeight)

/**
 * Floating "frosted glass" bottom bar: a translucent pill over AppBackground with a bright
 * highlight along its top edge and a hairline border. The selected tab gets a soft red lens
 * behind its icon, which fades in while the icon pops.
 *
 * It's translucent, not blurred: Compose can't blur what's behind a view without extra
 * libraries, and the backdrop here is soft gradients anyway, so the glass reads the same.
 */
@Composable
fun GlassBottomBar(
    currentDestination: NavDestination?,
    onNavigate: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) NightGlass else DayGlass
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp) // keep in sync with GlassBottomBarHeight
            .fillMaxWidth()
            .height(68.dp)
            .clip(shape)
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
                onClick = { onNavigate(destination) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun GlassBarItem(
    destination: TopLevelDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val contentColor by animateColorAsState(
        if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "tabContent",
    )
    val lensColor by animateColorAsState(
        if (selected) primary.copy(alpha = 0.16f) else Color.Transparent,
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
)

/** Milky glass on the light background. */
private val DayGlass = GlassColors(
    fillTop = Color.White.copy(alpha = 0.78f),
    fillBottom = Color.White.copy(alpha = 0.58f),
    edgeTop = Color.White.copy(alpha = 0.95f),
    edgeBottom = VegamRed.copy(alpha = 0.10f),
    sheen = Color.White.copy(alpha = 0.45f),
)

/** Smoked glass on the dark background. */
private val NightGlass = GlassColors(
    fillTop = Color.White.copy(alpha = 0.12f),
    fillBottom = Color.White.copy(alpha = 0.05f),
    edgeTop = Color.White.copy(alpha = 0.22f),
    edgeBottom = Color.White.copy(alpha = 0.05f),
    sheen = Color.White.copy(alpha = 0.06f),
)

// ---------------- Previews ----------------

@ComponentPreviews
@Composable
private fun GlassBottomBarPreview() = PreviewTheme {
    GlassBottomBar(currentDestination = null, onNavigate = {})
}
