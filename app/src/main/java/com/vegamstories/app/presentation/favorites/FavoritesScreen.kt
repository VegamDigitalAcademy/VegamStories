package com.vegamstories.app.presentation.favorites

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vegamstories.app.R
import com.vegamstories.app.presentation.common.StoryListUiState
import com.vegamstories.app.presentation.components.BackTopBar
import com.vegamstories.app.presentation.components.EmptyState
import com.vegamstories.app.presentation.components.StoryGrid
import com.vegamstories.app.presentation.components.StoryGridSkeleton
import com.vegamstories.app.presentation.preview.PreviewData
import com.vegamstories.app.presentation.preview.PreviewTheme
import com.vegamstories.app.presentation.preview.ScreenPreviews

@Composable
fun FavoritesScreen(
    onStoryClick: (String) -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FavoritesContent(state = state, onStoryClick = onStoryClick)
}

@Composable
fun FavoritesContent(
    state: StoryListUiState,
    onStoryClick: (String) -> Unit,
) {
    Scaffold(
        topBar = { BackTopBar(title = stringResource(R.string.nav_favorites), onBack = null) },
        containerColor = Color.Transparent, // AppBackground shows through
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                state.isLoading -> StoryGridSkeleton()
                state.stories.isEmpty() -> EmptyState(
                    icon = R.drawable.ic_favorite_border,
                    title = stringResource(R.string.favorites_empty_title),
                    message = stringResource(R.string.favorites_empty_message),
                )
                else -> StoryGrid(
                    stories = state.stories,
                    onStoryClick = { onStoryClick(it.id) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

// ---------------- Previews ----------------

@ScreenPreviews
@Composable
private fun FavoritesPreview() = PreviewTheme {
    FavoritesContent(
        state = StoryListUiState(isLoading = false, stories = PreviewData.stories.filter { it.isFavorite }),
        onStoryClick = {},
    )
}

@ScreenPreviews
@Composable
private fun FavoritesEmptyPreview() = PreviewTheme {
    FavoritesContent(state = StoryListUiState(isLoading = false), onStoryClick = {})
}
