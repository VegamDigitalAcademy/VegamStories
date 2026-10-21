package com.vegam.myapplication.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.vegam.myapplication.presentation.downloads.DownloadsScreen
import com.vegam.myapplication.presentation.favorites.FavoritesScreen
import com.vegam.myapplication.presentation.history.HistoryScreen
import com.vegam.myapplication.presentation.home.HomeScreen
import com.vegam.myapplication.presentation.info.InfoScreen
import com.vegam.myapplication.presentation.menu.MenuScreen
import com.vegam.myapplication.presentation.reader.ReaderScreen
import com.vegam.myapplication.presentation.search.SearchScreen
import com.vegam.myapplication.presentation.settings.SettingsScreen
import com.vegam.myapplication.presentation.shortcuts.ShortcutTarget

@Composable
fun VegamNavHost(
    navController: NavHostController,
    shortcutTarget: ShortcutTarget?,
    onShortcutHandled: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val openStory: (String) -> Unit = { id -> navController.navigate(ReaderRoute(id)) }

    NavHost(
        navController = navController,
        startDestination = HomeRoute(),
        modifier = modifier,
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onStoryClick = openStory,
                onSearchClick = { navController.navigate(SearchRoute) },
                onMenuClick = { navController.navigateToTopLevel(TopLevelDestination.MENU) },
                onShowMessage = onShowMessage,
            )
        }
        composable<FavoritesRoute> {
            FavoritesScreen(onStoryClick = openStory)
        }
        composable<DownloadsRoute> {
            DownloadsScreen(onStoryClick = openStory)
        }
        composable<MenuRoute> {
            MenuScreen(
                onBack = { navController.navigateToTopLevel(TopLevelDestination.HOME) },
                onFavorites = { navController.navigateToTopLevel(TopLevelDestination.FAVORITES) },
                onDownloads = { navController.navigateToTopLevel(TopLevelDestination.DOWNLOADS) },
                onHistory = { navController.navigate(HistoryRoute) },
                onSettings = { navController.navigate(SettingsRoute) },
                onInfoPage = { page -> navController.navigate(InfoRoute(page.name)) },
            )
        }
        composable<HistoryRoute> {
            HistoryScreen(onBack = navController::popBackStack, onStoryClick = openStory)
        }
        composable<SearchRoute> {
            SearchScreen(onBack = navController::popBackStack, onStoryClick = openStory)
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = navController::popBackStack)
        }
        composable<InfoRoute> { entry ->
            val page = InfoPage.valueOf(entry.toRoute<InfoRoute>().page)
            InfoScreen(page = page, onBack = navController::popBackStack)
        }
        composable<ReaderRoute> {
            ReaderScreen(
                onBack = navController::popBackStack,
                onOpenNextStory = { nextId ->
                    navController.navigate(ReaderRoute(nextId)) {
                        popUpTo<ReaderRoute> { inclusive = true }
                    }
                },
                onShowMessage = onShowMessage,
            )
        }
    }

    // App opened from an app-icon shortcut. Runs after NavHost has set the graph.
    LaunchedEffect(shortcutTarget) {
        when (shortcutTarget) {
            is ShortcutTarget.Category -> navController.navigate(HomeRoute(shortcutTarget.categoryId)) {
                // Fresh Home showing that category, nothing behind it.
                popUpTo(navController.graph.id) { inclusive = true }
            }
            ShortcutTarget.Search -> navController.navigate(SearchRoute) { launchSingleTop = true }
            null -> return@LaunchedEffect
        }
        onShortcutHandled()
    }
}

/** Standard bottom-nav behaviour: single copy of each tab, state saved and restored. */
fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
