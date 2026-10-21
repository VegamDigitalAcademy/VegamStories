package com.vegam.myapplication.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * The app-wide backdrop behind every screen except the reader: a soft vertical gradient,
 * two glows (brand red top-left, warm gold bottom-right) and faint concentric arches from the
 * top-right corner and the bottom-left (like a rainbow). Screens draw on top with transparent
 * backgrounds; cards, sheets and bars stay opaque so text keeps its contrast.
 *
 * Follows the app theme (light/dark), not the phone, by looking at the scheme's background.
 *
 * It also sets the default text/icon colour to onBackground. A transparent Scaffold or Surface
 * can't work out a content colour from its own colour, so without this Text falls back to
 * black, which is unreadable in dark mode.
 */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val palette = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) NightPalette else DayPalette
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height
                val base = Brush.verticalGradient(listOf(palette.top, palette.middle, palette.bottom))
                val redGlow = Brush.radialGradient(
                    colors = listOf(palette.redGlow, Color.Transparent),
                    center = Offset(0f, h * 0.06f),
                    radius = w * 0.95f,
                )
                val goldGlow = Brush.radialGradient(
                    colors = listOf(palette.goldGlow, Color.Transparent),
                    center = Offset(w, h * 0.92f),
                    radius = w * 0.9f,
                )
                val arch = Stroke(width = 20.dp.toPx())
                val thinArch = Stroke(width = 12.dp.toPx())
                onDrawBehind {
                    drawRect(base)
                    drawRect(redGlow)
                    drawRect(goldGlow)
                    // Top-right: three arches fading outwards.
                    palette.topArches.forEachIndexed { i, color ->
                        drawCircle(color, radius = w * (0.42f + 0.17f * i), center = Offset(w, 0f), style = arch)
                    }
                    // Bottom-left: a small rainbow rising from the corner.
                    palette.bottomArches.forEachIndexed { i, color ->
                        drawCircle(color, radius = w * (0.28f + 0.09f * i), center = Offset(0f, h), style = thinArch)
                    }
                }
            },
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            content()
        }
    }
}

private class BackgroundPalette(
    val top: Color,
    val middle: Color,
    val bottom: Color,
    val redGlow: Color,
    val goldGlow: Color,
    val topArches: List<Color>,
    val bottomArches: List<Color>,
)

private val Gold = Color(0xFFFFB300)
private val Rose = Color(0xFFFF6F91)

private val DayPalette = BackgroundPalette(
    top = Color(0xFFFFEFEB), // blush
    middle = Color(0xFFFFFAF6), // warm white
    bottom = Color(0xFFFFF1E0), // cream
    redGlow = VegamRed.copy(alpha = 0.10f),
    goldGlow = Gold.copy(alpha = 0.14f),
    topArches = listOf(
        VegamRed.copy(alpha = 0.08f),
        VegamRed.copy(alpha = 0.055f),
        VegamRed.copy(alpha = 0.035f),
    ),
    bottomArches = listOf(
        VegamRed.copy(alpha = 0.07f),
        Gold.copy(alpha = 0.10f),
        Rose.copy(alpha = 0.07f),
    ),
)

private val NightPalette = BackgroundPalette(
    top = Color(0xFF1E1517), // dark wine
    middle = NightPaper,
    bottom = Color(0xFF19141C), // dark plum
    redGlow = VegamRed.copy(alpha = 0.16f),
    goldGlow = Gold.copy(alpha = 0.06f),
    topArches = listOf(
        VegamRed.copy(alpha = 0.12f),
        VegamRed.copy(alpha = 0.08f),
        VegamRed.copy(alpha = 0.05f),
    ),
    bottomArches = listOf(
        VegamRed.copy(alpha = 0.10f),
        Gold.copy(alpha = 0.07f),
        Rose.copy(alpha = 0.08f),
    ),
)

@Preview(name = "Light", widthDp = 360, heightDp = 760)
@Composable
private fun AppBackgroundLightPreview() = MyApplicationTheme(darkTheme = false) { AppBackground {} }

@Preview(name = "Dark", widthDp = 360, heightDp = 760)
@Composable
private fun AppBackgroundDarkPreview() = MyApplicationTheme(darkTheme = true) { AppBackground {} }
