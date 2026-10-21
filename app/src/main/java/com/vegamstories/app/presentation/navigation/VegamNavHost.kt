package com.vegamstories.app.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.vegamstories.app.presentation.downloads.DownloadsScreen
import com.vegamstories.app.presentation.favorites.FavoritesScreen
import com.vegamstories.app.presentation.history.HistoryScreen
import com.vegamstories.app.presentation.home.HomeScreen
import com.vegamstories.app.presentation.info.InfoScreen
import com.vegamstories.app.presentation.menu.MenuScreen
import com.vegamstories.app.presentation.reader.ReaderScreen
import com.vegamstories.app.presentation.search.SearchScreen
import com.vegamstories.app.presentation.settings.SettingsScreen
import com.vegamstories.app.presentation.shortcuts.ShortcutTarget

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
        // See NavTransitions.kt: slide + fade for screens, fade-through for tabs.
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) {
        composable<HomeRoute> {
            AboveBottomBar {
                HomeScreen(
                    onStoryClick = openStory,
                    onSearchClick = { navController.navigate(SearchRoute) },
                    onMenuClick = { navController.navigateToTopLevel(TopLevelDestination.MENU) },
                    onShowMessage = onShowMessage,
                )
            }
        }
        composable<FavoritesRoute> {
            AboveBottomBar { FavoritesScreen(onStoryClick = openStory) }
        }
        composable<DownloadsRoute> {
            AboveBottomBar { DownloadsScreen(onStoryClick = openStory) }
        }
        composable<MenuRoute> {
            AboveBottomBar {
                MenuScreen(
                    onBack = { navController.navigateToTopLevel(TopLevelDestination.HOME) },
                    onFavorites = { navController.navigateToTopLevel(TopLevelDestination.FAVORITES) },
                    onDownloads = { navController.navigateToTopLevel(TopLevelDestination.DOWNLOADS) },
                    onHistory = { navController.navigate(HistoryRoute) },
                    onSettings = { navController.navigate(SettingsRoute) },
                    onInfoPage = { page -> navController.navigate(InfoRoute(page.name)) },
                )
            }
        }
        composable<HistoryRoute> {
            AboveBottomBar { HistoryScreen(onBack = navController::popBackStack, onStoryClick = openStory) }
        }
        composable<SearchRoute> {
            SearchScreen(onBack = navController::popBackStack, onStoryClick = openStory)
        }
        composable<SettingsRoute> {
            AboveBottomBar { SettingsScreen(onBack = navController::popBackStack) }
        }
        composable<InfoRoute> { entry ->
            val page = InfoPage.valueOf(entry.toRoute<InfoRoute>().page)
            AboveBottomBar { InfoScreen(page = page, onBack = navController::popBackStack) }
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

    // App opened from an app-icon shortcut or the widget. Runs after NavHost has set the graph.
    LaunchedEffect(shortcutTarget) {
        when (shortcutTarget) {
            is ShortcutTarget.Category -> navController.navigate(HomeRoute(shortcutTarget.categoryId)) {
                // Fresh Home showing that category, nothing behind it.
                popUpTo(navController.graph.id) { inclusive = true }
            }
            ShortcutTarget.Search -> navController.navigate(SearchRoute) { launchSingleTop = true }
            // From the widget: the story over Home, so Back lands on Home.
            is ShortcutTarget.Story -> navController.navigate(ReaderRoute(shortcutTarget.storyId)) {
                popUpTo(navController.graph.findStartDestination().id)
            }
            null -> return@LaunchedEffect
        }
        onShortcutHandled()
    }
}

/**
 * For screens that show the bottom bar (everything except the reader and search). The bar
 * floats over the screens, so these keep a fixed space for it. Fixed, not animated, so no
 * screen changes size while the bar slides in or out.
 */
@Composable
private fun AboveBottomBar(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().glassBottomBarSpace()) { content() }
}

/**
 * Bottom-nav behaviour: one copy of each tab, with its state saved and restored (scroll
 * position, selected category…). Except Menu: it's a hub, so tapping it always shows the Menu
 * screen itself, never a page opened from it earlier (Settings, History, an info page).
 */
fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = destination != TopLevelDestination.MENU
    }
}
