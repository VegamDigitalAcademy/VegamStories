package com.vegamstories.app.presentation.menu

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vegamstories.app.BuildConfig
import com.vegamstories.app.R
import com.vegamstories.app.presentation.components.BackTopBar
import com.vegamstories.app.presentation.navigation.InfoPage
import com.vegamstories.app.presentation.preview.PreviewTheme
import com.vegamstories.app.presentation.preview.ScreenPreviews
import com.vegamstories.app.presentation.preview.TeluguPreview

/** Static menu: no ViewModel needed, it only routes to other screens. */
@Composable
fun MenuScreen(
    onBack: () -> Unit,
    onFavorites: () -> Unit,
    onDownloads: () -> Unit,
    onHistory: () -> Unit,
    onBookmarks: () -> Unit,
    onBookshelf: () -> Unit,
    onSettings: () -> Unit,
    onInfoPage: (InfoPage) -> Unit,
) {
    Scaffold(
        topBar = { BackTopBar(title = stringResource(R.string.menu_title), onBack = onBack) },
        containerColor = Color.Transparent, // AppBackground shows through
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // Profile (InfoPage.PROFILE) is hidden until sign-in exists.
            MenuItem(R.drawable.ic_favorite_border, stringResource(R.string.menu_favorites), showChevron = true, onClick = onFavorites)
            MenuItem(R.drawable.ic_download, stringResource(R.string.menu_downloads), showChevron = true, onClick = onDownloads)
            MenuItem(R.drawable.ic_history, stringResource(R.string.menu_history), showChevron = true, onClick = onHistory)
            MenuItem(R.drawable.ic_bookshelf, stringResource(R.string.menu_bookshelf), showChevron = true, onClick = onBookshelf)
            MenuItem(R.drawable.ic_bookmark, stringResource(R.string.menu_bookmarks), showChevron = true, onClick = onBookmarks)
            MenuItem(R.drawable.ic_settings, stringResource(R.string.menu_settings), showChevron = true, onClick = onSettings)
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline)
            MenuItem(R.drawable.ic_info, stringResource(R.string.menu_about), small = true) { onInfoPage(InfoPage.ABOUT) }
            MenuItem(R.drawable.ic_lock, stringResource(R.string.menu_privacy), small = true) { onInfoPage(InfoPage.PRIVACY) }
            MenuItem(R.drawable.ic_description, stringResource(R.string.menu_terms), small = true) { onInfoPage(InfoPage.TERMS) }
            Spacer(Modifier.size(24.dp))
            Text(
                text = stringResource(R.string.app_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

@Composable
private fun MenuItem(
    @DrawableRes icon: Int,
    label: String,
    showChevron: Boolean = false,
    small: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = if (small) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(if (small) 18.dp else 22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = if (small) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
            color = if (small) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (showChevron) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

// ---------------- Previews ----------------

@ScreenPreviews
@TeluguPreview
@Composable
private fun MenuPreview() = PreviewTheme {
    MenuScreen(onBack = {}, onFavorites = {}, onDownloads = {}, onHistory = {}, onBookmarks = {}, onBookshelf = {}, onSettings = {}, onInfoPage = {})
}
