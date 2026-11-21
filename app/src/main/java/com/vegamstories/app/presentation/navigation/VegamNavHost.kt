package com.vegamstories.app.presentation.navigation

import com.vegamstories.app.presentation.bookmarks.BookmarksScreen
import com.vegamstories.app.presentation.bookshelf.BookshelfScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.vegamstories.app.BuildConfig
import com.vegamstories.app.presentation.downloads.DownloadsScreen
import com.vegamstories.app.presentation.favorites.FavoritesScreen
import com.vegamstories.app.presentation.history.HistoryScreen
import com.vegamstories.app.presentation.home.HomeScreen
import com.vegamstories.app.presentation.info.InfoScreen
import com.vegamstories.app.presentation.menu.MenuScreen
import com.vegamstories.app.presentation.reader.ReaderScreen
import com.vegamstories.app.presentation.reader.animationsOn
import com.vegamstories.app.presentation.search.SearchScreen
import com.vegamstories.app.presentation.settings.ApiCallsScreen
import com.vegamstories.app.presentation.settings.SettingsScreen
import com.vegamstories.app.presentation.shortcuts.ShortcutTarget

@Composable
fun VegamNavHost(
    navController: NavHostController,
    shortcutTarget: ShortcutTarget?,
    onShortcutHandled: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Whether a story can be opened now: there's internet, or it's saved on the phone. */
    canOpenStory: suspend (storyId: String) -> Boolean = { true },
    /** A story that can't be opened without internet was asked for. */
    onStoryNeedsInternet: () -> Unit = {},
) {
    // Every way into a story checks it can open first (no internet, not saved: say so instead).
    val scope = rememberCoroutineScope()
    val whenOpenable: (storyId: String, open: () -> Unit) -> Unit = { id, open ->
        scope.launch { if (canOpenStory(id)) open() else onStoryNeedsInternet() }
    }
    // From its card, the card's picture grows into the reader first (StoryOpener).
    val storyOpener = LocalStoryOpener.current
    val animates = animationsOn()
    val openStory: (String) -> Unit = { id ->
        whenOpenable(id) { storyOpener.open(id, animates) { navController.navigate(ReaderRoute(id)) } }
    }

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
            UnderBottomBar {
                HomeScreen(
                    onStoryClick = openStory,
                    onSearchClick = { navController.navigate(SearchRoute) },
                    onShowMessage = onShowMessage,
                )
            }
        }
        composable<FavoritesRoute> {
            UnderBottomBar { FavoritesScreen(onStoryClick = openStory) }
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
                    onBookmarks = { navController.navigate(BookmarksRoute) },
                    onBookshelf = { navController.navigate(BookshelfRoute) },
                    onSettings = { navController.navigate(SettingsRoute) },
                    onInfoPage = { page -> navController.navigate(InfoRoute(page.name)) },
                )
            }
        }
        composable<HistoryRoute> {
            AboveBottomBar { HistoryScreen(onBack = navController::popBackStack, onStoryClick = openStory) }
        }
        composable<BookshelfRoute> {
            AboveBottomBar { BookshelfScreen(onBack = navController::popBackStack, onStoryClick = openStory) }
        }
        composable<BookmarksRoute> {
            AboveBottomBar {
                BookmarksScreen(
                    onBack = navController::popBackStack,
                    onOpenBookmark = { bookmark ->
                        whenOpenable(bookmark.storyId) { navController.navigate(bookmark.toReaderRoute()) }
                    },
                )
            }
        }
        composable<SearchRoute> {
            SearchScreen(onBack = navController::popBackStack, onStoryClick = openStory)
        }
        composable<SettingsRoute> {
            AboveBottomBar {
                SettingsScreen(
                    onBack = navController::popBackStack,
                    onOpenApiCalls = { navController.navigate(ApiCallsRoute) },
                )
            }
        }
        // Settings → Developer → API calls: debug builds only (release builds don't count the calls).
        if (BuildConfig.DEBUG) {
            composable<ApiCallsRoute> {
                AboveBottomBar { ApiCallsScreen(onBack = navController::popBackStack) }
            }
        }
        composable<InfoRoute> { entry ->
            val page = InfoPage.valueOf(entry.toRoute<InfoRoute>().page)
            AboveBottomBar { InfoScreen(page = page, onBack = navController::popBackStack) }
        }
        composable<ReaderRoute> {
            ReaderScreen(
                onBack = navController::popBackStack,
                onOpenNextStory = { nextId ->
                    whenOpenable(nextId) {
                        navController.navigate(ReaderRoute(nextId)) {
                            popUpTo<ReaderRoute> { inclusive = true }
                        }
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
            is ShortcutTarget.Story -> if (canOpenStory(shortcutTarget.storyId)) {
                navController.navigate(ReaderRoute(shortcutTarget.storyId)) {
                    popUpTo(navController.graph.findStartDestination().id)
                }
            } else {
                onStoryNeedsInternet()
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
 * For the screens whose stories scroll under the bar, seen through its glass: the whole screen,
 * with the bar's space handed to their grid (LocalBottomBarSpace) to keep its last row clear.
 */
@Composable
private fun UnderBottomBar(content: @Composable () -> Unit) {
    val space = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + GlassBottomBarHeight
    CompositionLocalProvider(LocalBottomBarSpace provides space) {
        Box(Modifier.fillMaxSize()) { content() }
    }
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
