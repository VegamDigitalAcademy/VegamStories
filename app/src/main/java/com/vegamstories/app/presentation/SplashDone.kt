package com.vegamstories.app.presentation

import androidx.compose.runtime.compositionLocalOf

/**
 * Whether the launch splash has gone. Screens are drawn under it before then, so an animation that
 * should be seen as the app opens (Home's cards rising in) waits for this. True where there's no
 * splash (previews, tests, the screen recreated).
 */
val LocalSplashDone = compositionLocalOf { true }
