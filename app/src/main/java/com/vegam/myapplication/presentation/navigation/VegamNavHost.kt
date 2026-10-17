package com.vegam.myapplication.presentation.navigation

import androidx.compose.runtime.Composable
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

@Composable
fun VegamNavHost(
    navController: NavHostController,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val openStory: (String) -> Unit = { id -> navController.navigate(ReaderRoute(id)) }

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
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
            FavoritesScreen(onStoryClick = openStory, onShowMessage = onShowMessage)
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
                onInfoPage = { page -> navController.navigate(InfoRoute(page.name)) },
            )
        }
        composable<HistoryRoute> {
            HistoryScreen(onBack = navController::popBackStack, onStoryClick = openStory)
        }
        composable<SearchRoute> {
            SearchScreen(onBack = navController::popBackStack, onStoryClick = openStory, onShowMessage = onShowMessage)
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
}

/** Standard bottom-nav behaviour: single copy of each tab, state saved and restored. */
fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
