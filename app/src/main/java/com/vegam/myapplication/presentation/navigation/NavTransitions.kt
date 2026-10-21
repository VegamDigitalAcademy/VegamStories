package com.vegam.myapplication.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute

/*
 * Screen-to-screen motion, following Material's patterns:
 *  - Opening a screen (shared axis X): the new screen slides in a little from the right while
 *    fading in. Back is instant (no animation).
 *  - Switching bottom-bar tabs (fade through): the old tab fades out, then the new one fades in
 *    with a slight zoom.
 *
 * The outgoing screen is gone within the first 90 ms and the incoming one only starts fading in
 * after that. Screens are transparent over AppBackground, so letting them overlap (the default
 * 700 ms crossfade) shows both screens' text on top of each other, which looks like a flicker.
 */

/** Length of every screen transition; the bottom bar uses it too so they move together. */
const val NAV_ANIM_MS = 200
private const val FADE_OUT_MS = 90
private const val FADE_IN_MS = NAV_ANIM_MS - FADE_OUT_MS

/** Material 3 "emphasized" easing: quick start, long gentle settle. */
val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** How far screens travel sideways: a tenth of the width, so it reads as a nudge, not a swipe. */
private fun nudge(fullWidth: Int) = fullWidth / 10

private val fadeInAfterExit = fadeIn(tween(FADE_IN_MS, delayMillis = FADE_OUT_MS, easing = LinearOutSlowInEasing))
private val quickFadeOut = fadeOut(tween(FADE_OUT_MS, easing = FastOutLinearInEasing))

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.enterTransition(): EnterTransition =
    if (isTabSwitch()) {
        fadeInAfterExit + scaleIn(tween(NAV_ANIM_MS, easing = EmphasizedEasing), initialScale = 0.96f)
    } else {
        slideInHorizontally(tween(NAV_ANIM_MS, easing = EmphasizedEasing)) { nudge(it) } + fadeInAfterExit
    }

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.exitTransition(): ExitTransition =
    if (isTabSwitch()) {
        quickFadeOut
    } else {
        slideOutHorizontally(tween(NAV_ANIM_MS, easing = EmphasizedEasing)) { -nudge(it) } + quickFadeOut
    }

/**
 * Back has no animation: the previous screen simply comes back. (Opening screens and switching
 * tabs are still animated.) Android's predictive-back preview is switched off in the manifest
 * (enableOnBackInvokedCallback="false"), so a back swipe doesn't shrink the screen either.
 */
internal fun popEnterTransition(): EnterTransition = EnterTransition.None

internal fun popExitTransition(): ExitTransition = ExitTransition.None

/** Both ends are bottom-bar tabs (Home, Favorites, Downloads, Menu). */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    initialState.destination.isTopLevel() && targetState.destination.isTopLevel()

private fun NavDestination.isTopLevel(): Boolean =
    TopLevelDestination.entries.any { hasRoute(it.route::class) }
