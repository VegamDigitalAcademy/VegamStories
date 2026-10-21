package com.vegam.myapplication.presentation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vegam.myapplication.presentation.navigation.GlassBottomBar
import com.vegam.myapplication.presentation.navigation.ReaderRoute
import com.vegam.myapplication.presentation.navigation.SearchRoute
import com.vegam.myapplication.presentation.navigation.VegamNavHost
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
    val showBottomBar = currentDestination != null &&
        !currentDestination.hasRoute(ReaderRoute::class) &&
        !currentDestination.hasRoute(SearchRoute::class)

    // Gradient + arches behind every screen; screens and the Scaffold are transparent on top.
    AppBackground {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            // Each screen draws its own top bar and handles the status-bar inset.
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (showBottomBar) {
                    GlassBottomBar(
                        currentDestination = currentDestination,
                        onNavigate = navController::navigateToTopLevel,
                    )
                }
            },
        ) { innerPadding ->
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
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}
