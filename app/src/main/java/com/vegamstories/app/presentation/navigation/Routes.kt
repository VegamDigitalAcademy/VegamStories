package com.vegamstories.app.presentation.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.vegamstories.app.R
import com.vegamstories.app.domain.model.Bookmark
import com.vegamstories.app.domain.model.StoryFormat
import kotlinx.serialization.Serializable

/** Type-safe navigation destinations (Navigation Compose + kotlinx.serialization). */

/** [categoryId] pre-selects a category tab (used by app-icon shortcuts); null = ALL. */
@Serializable data class HomeRoute(val categoryId: String? = null)
@Serializable data object FavoritesRoute
@Serializable data object DownloadsRoute
@Serializable data object MenuRoute
@Serializable data object HistoryRoute
@Serializable data object BookmarksRoute
@Serializable data object SearchRoute
@Serializable data object SettingsRoute

/**
 * A story in the reader. Opened from a bookmark it carries the bookmark's place ([bookmarkFormat]
 * set), and opens on that page; otherwise where the reader left off.
 */
@Serializable data class ReaderRoute(
    val storyId: String,
    val bookmarkFormat: String? = null,
    val bookmarkLanguage: String? = null,
    val bookmarkPosition: Int = 0,
    val bookmarkOffset: Int = 0,
) {
    /** The bookmark the reader opens at, if any. */
    val bookmark: Bookmark?
        get() {
            val format = StoryFormat.entries.firstOrNull { it.name == bookmarkFormat } ?: return null
            val language = bookmarkLanguage ?: return null
            return Bookmark(storyId, language, format, bookmarkPosition, bookmarkOffset)
        }
}

/** Opens the story on [Bookmark]'s page. */
fun Bookmark.toReaderRoute() = ReaderRoute(storyId, format.name, language, position, offset)

@Serializable data class InfoRoute(val page: String)

/** Static pages reachable from the menu. Passed to [InfoRoute] by name. */
enum class InfoPage(@StringRes val titleRes: Int, @StringRes val bodyRes: Int) {
    PROFILE(R.string.menu_profile, R.string.info_profile_body),
    ABOUT(R.string.menu_about, R.string.info_about_body),
    PRIVACY(R.string.menu_privacy, R.string.info_privacy_body),
    TERMS(R.string.menu_terms, R.string.info_terms_body),
}

/** The four tabs of the bottom navigation bar. */
enum class TopLevelDestination(
    val route: Any,
    @DrawableRes val selectedIcon: Int,
    @DrawableRes val unselectedIcon: Int,
    @StringRes val labelRes: Int,
) {
    HOME(HomeRoute(), R.drawable.ic_home, R.drawable.ic_home, R.string.nav_home),
    FAVORITES(FavoritesRoute, R.drawable.ic_favorite, R.drawable.ic_favorite_border, R.string.nav_favorites),
    DOWNLOADS(DownloadsRoute, R.drawable.ic_download, R.drawable.ic_download, R.string.nav_downloads),
    MENU(MenuRoute, R.drawable.ic_menu, R.drawable.ic_menu, R.string.nav_menu),
}
