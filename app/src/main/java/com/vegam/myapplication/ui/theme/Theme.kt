package com.vegam.myapplication.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val LightColorScheme = lightColorScheme(
    primary = VegamRed,
    onPrimary = Color.White,
    primaryContainer = VegamRedLight,
    onPrimaryContainer = VegamRedDark,
    secondary = Ink,
    onSecondary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperDim,
    onSurfaceVariant = InkMuted,
    surfaceContainer = Paper,
    outline = Outline,
    outlineVariant = Outline,
)

internal val DarkColorScheme = darkColorScheme(
    primary = VegamRed,
    onPrimary = Color.White,
    primaryContainer = VegamRedDark,
    onPrimaryContainer = Color.White,
    secondary = NightInk,
    onSecondary = NightPaper,
    background = NightPaper,
    onBackground = NightInk,
    surface = NightPaper,
    onSurface = NightInk,
    surfaceVariant = NightSurface,
    onSurfaceVariant = NightInkMuted,
    surfaceContainer = NightSurface,
    outline = NightOutline,
    outlineVariant = NightOutline,
)

/** Light scheme on warm paper, for the reader's sepia page background. */
internal val SepiaColorScheme = LightColorScheme.copy(
    background = SepiaPaper,
    onBackground = SepiaInk,
    surface = SepiaPaper,
    onSurface = SepiaInk,
    surfaceVariant = SepiaSurface,
    onSurfaceVariant = SepiaInkMuted,
    surfaceContainer = SepiaSurface,
    surfaceContainerLow = SepiaSurface,
    surfaceContainerHigh = SepiaSurface,
    outline = SepiaOutline,
    outlineVariant = SepiaOutline,
)

/** Brand theme: fixed Vegam red instead of wallpaper-based dynamic colour. */
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
