package com.vegam.myapplication.presentation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vegam.myapplication.presentation.navigation.ReaderRoute
import com.vegam.myapplication.presentation.navigation.SearchRoute
import com.vegam.myapplication.presentation.navigation.TopLevelDestination
import com.vegam.myapplication.presentation.navigation.VegamNavHost
import com.vegam.myapplication.presentation.navigation.navigateToTopLevel
import com.vegam.myapplication.presentation.preview.ComponentPreviews
import com.vegam.myapplication.presentation.preview.PreviewTheme
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
                    VegamBottomBar(
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

@Composable
private fun VegamBottomBar(
    currentDestination: NavDestination?,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    // Mostly opaque so labels stay crisp, with a hint of the background showing through.
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)) {
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any { it.hasRoute(destination.route::class) } == true
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(destination) },
                icon = {
                    Icon(
                        painter = painterResource(if (selected) destination.selectedIcon else destination.unselectedIcon),
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(destination.labelRes)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

// ---------------- Previews ----------------
// VegamApp itself needs Hilt + a real NavController, so only the bottom bar is previewed here.
// Each screen has its own previews next to it.

@ComponentPreviews
@Composable
private fun BottomBarPreview() = PreviewTheme {
    VegamBottomBar(currentDestination = null, onNavigate = {})
}
