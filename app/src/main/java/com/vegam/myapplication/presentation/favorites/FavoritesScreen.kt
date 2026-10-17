package com.vegam.myapplication.presentation.favorites

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vegam.myapplication.R
import com.vegam.myapplication.presentation.common.StoryListUiState
import com.vegam.myapplication.presentation.components.BackTopBar
import com.vegam.myapplication.presentation.components.EmptyState
import com.vegam.myapplication.presentation.components.LoadingBox
import com.vegam.myapplication.presentation.components.StoryGrid
import com.vegam.myapplication.presentation.preview.PreviewData
import com.vegam.myapplication.presentation.preview.PreviewTheme
import com.vegam.myapplication.presentation.preview.ScreenPreviews

@Composable
fun FavoritesScreen(
    onStoryClick: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val removedMsg = stringResource(R.string.removed_from_favorites)

    FavoritesContent(
        state = state,
        onStoryClick = onStoryClick,
        onToggleFavorite = { storyId ->
            viewModel.onToggleFavorite(storyId)
            onShowMessage(removedMsg)
        },
    )
}

@Composable
fun FavoritesContent(
    state: StoryListUiState,
    onStoryClick: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
) {
    Scaffold(
        topBar = { BackTopBar(title = stringResource(R.string.nav_favorites), onBack = null) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                state.isLoading -> LoadingBox()
                state.stories.isEmpty() -> EmptyState(
                    icon = R.drawable.ic_favorite_border,
                    title = stringResource(R.string.favorites_empty_title),
                    message = stringResource(R.string.favorites_empty_message),
                )
                else -> StoryGrid(
                    stories = state.stories,
                    onStoryClick = { onStoryClick(it.id) },
                    onToggleFavorite = { onToggleFavorite(it.id) },
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
        onToggleFavorite = {},
    )
}

@ScreenPreviews
@Composable
private fun FavoritesEmptyPreview() = PreviewTheme {
    FavoritesContent(state = StoryListUiState(isLoading = false), onStoryClick = {}, onToggleFavorite = {})
}
