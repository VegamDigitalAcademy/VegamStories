package com.vegam.myapplication.presentation.history

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vegam.myapplication.R
import com.vegam.myapplication.presentation.common.StoryListUiState
import com.vegam.myapplication.presentation.components.BackTopBar
import com.vegam.myapplication.presentation.components.EmptyState
import com.vegam.myapplication.presentation.components.LoadingBox
import com.vegam.myapplication.presentation.components.StoryListRow
import com.vegam.myapplication.presentation.preview.PreviewData
import com.vegam.myapplication.presentation.preview.PreviewTheme
import com.vegam.myapplication.presentation.preview.ScreenPreviews

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onStoryClick: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryContent(
        state = state,
        onBack = onBack,
        onStoryClick = onStoryClick,
        onClearHistory = viewModel::onClearHistory,
    )
}

@Composable
fun HistoryContent(
    state: StoryListUiState,
    onBack: () -> Unit,
    onStoryClick: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    Scaffold(
        topBar = {
            BackTopBar(
                title = stringResource(R.string.history_title),
                onBack = onBack,
                actions = {
                    if (state.stories.isNotEmpty()) {
                        IconButton(onClick = onClearHistory) {
                            Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.clear_history))
                        }
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        val modifier = Modifier
            .padding(padding)
            .fillMaxSize()
        when {
            state.isLoading -> LoadingBox(modifier)
            state.stories.isEmpty() -> EmptyState(
                icon = R.drawable.ic_history,
                title = stringResource(R.string.history_empty_title),
                message = stringResource(R.string.history_empty_message),
                modifier = modifier,
            )
            else -> LazyColumn(modifier) {
                items(state.stories, key = { it.id }) { story ->
                    StoryListRow(story = story, onClick = { onStoryClick(story.id) })
                }
            }
        }
    }
}

// ---------------- Previews ----------------

@ScreenPreviews
@Composable
private fun HistoryPreview() = PreviewTheme {
    HistoryContent(
        state = StoryListUiState(isLoading = false, stories = PreviewData.historyStories),
        onBack = {}, onStoryClick = {}, onClearHistory = {},
    )
}

@ScreenPreviews
@Composable
private fun HistoryEmptyPreview() = PreviewTheme {
    HistoryContent(state = StoryListUiState(isLoading = false), onBack = {}, onStoryClick = {}, onClearHistory = {})
}
