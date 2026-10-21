package com.vegam.myapplication.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vegam.myapplication.presentation.navigation.EmphasizedEasing
import com.vegam.myapplication.presentation.navigation.GlassBottomBar
import com.vegam.myapplication.presentation.navigation.NAV_ANIM_MS
import com.vegam.myapplication.presentation.navigation.ReaderRoute
import com.vegam.myapplication.presentation.navigation.SearchRoute
import com.vegam.myapplication.presentation.navigation.VegamNavHost
import com.vegam.myapplication.presentation.navigation.glassBottomBarSpace
import com.vegam.myapplication.presentation.navigation.navigateToTopLevel
import com.vegam.myapplication.presentation.shortcuts.ShortcutTarget
import com.vegam.myapplication.ui.theme.AppBackground
import kotlinx.coroutines.launch

/**
 * @param shortcutTarget set when the app was opened from an app-icon shortcut.
 * @param onShortcutHandled called once we've navigated, so it isn't handled twice.
 */
@Composable
fun VegamApp(
    shortcutTarget: ShortcutTarget? = null,
    onShortcutHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // The reader and search are full-screen; everything else keeps the bottom bar.
    // Visible before the first destination is known, since the app starts on Home.
    val showBottomBar = currentDestination == null ||
        (!currentDestination.hasRoute(ReaderRoute::class) && !currentDestination.hasRoute(SearchRoute::class))

    var lastTabDestination by remember { mutableStateOf(currentDestination) }
    LaunchedEffect(currentDestination, showBottomBar) {
        if (showBottomBar) lastTabDestination = currentDestination
    }

    // Gradient + arches behind every screen; screens are transparent on top.
    AppBackground {
        // The bar and snackbar float over the screens instead of resizing them. If the bar's
        // space were taken from the screen area, every screen would shrink and grow while the
        // bar animates (the reader visibly squeezing on Back). Tab screens reserve the bar's
        // space themselves instead (VegamNavHost), so nothing resizes during a transition.
        Box(Modifier.fillMaxSize()) {
            VegamNavHost(
                navController = navController,
                shortcutTarget = shortcutTarget,
                onShortcutHandled = onShortcutHandled,
                onShowMessage = { message ->
                    scope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(message)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            AnimatedVisibility(
                visible = showBottomBar,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(barAnim()) { it } + fadeIn(barAnim()),
                exit = slideOutVertically(barAnim()) { it } + fadeOut(barAnim()),
            ) {
                GlassBottomBar(
                    // While sliding away, keep the tab we came from highlighted.
                    currentDestination = if (showBottomBar) currentDestination else lastTabDestination,
                    onNavigate = navController::navigateToTopLevel,
                )
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .then(if (showBottomBar) Modifier.glassBottomBarSpace() else Modifier.navigationBarsPadding()),
            )
        }
    }
}

private fun <T> barAnim() = tween<T>(NAV_ANIM_MS, easing = EmphasizedEasing)
