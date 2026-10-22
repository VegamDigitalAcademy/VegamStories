package com.vegamstories.app.presentation.downloads

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vegamstories.app.R
import com.vegamstories.app.presentation.common.StoryListUiState
import com.vegamstories.app.presentation.components.BackTopBar
import com.vegamstories.app.presentation.components.EmptyState
import com.vegamstories.app.presentation.components.LoadingBox
import com.vegamstories.app.presentation.components.StoryListRow
import com.vegamstories.app.presentation.preview.PreviewData
import com.vegamstories.app.presentation.preview.PreviewTheme
import com.vegamstories.app.presentation.preview.ScreenPreviews

@Composable
fun DownloadsScreen(
    onStoryClick: (String) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DownloadsContent(state = state, onStoryClick = onStoryClick, onRemoveDownload = viewModel::onRemoveDownload)
}

@Composable
fun DownloadsContent(
    state: StoryListUiState,
    onStoryClick: (String) -> Unit,
    onRemoveDownload: (String) -> Unit,
) {
    Scaffold(
        topBar = { BackTopBar(title = stringResource(R.string.nav_downloads), onBack = null) },
        containerColor = Color.Transparent, // AppBackground shows through
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        val modifier = Modifier
            .padding(padding)
            .fillMaxSize()
        when {
            state.isLoading -> LoadingBox(modifier)
            state.stories.isEmpty() -> EmptyState(
                icon = R.drawable.ic_download,
                title = stringResource(R.string.downloads_empty_title),
                message = stringResource(R.string.downloads_empty_message),
                modifier = modifier,
            )
            else -> LazyColumn(modifier) {
                items(state.stories, key = { it.id }) { story ->
                    StoryListRow(
                        story = story,
                        onClick = { onStoryClick(story.id) },
                        showProgress = false,
                        trailing = {
                            IconButton(onClick = { onRemoveDownload(story.id) }) {
                                Icon(
                                    painterResource(R.drawable.ic_delete),
                                    contentDescription = stringResource(R.string.remove_download),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                    HorizontalDivider(Modifier.padding(start = 86.dp), color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

// ---------------- Previews ----------------

@ScreenPreviews
@Composable
private fun DownloadsPreview() = PreviewTheme {
    DownloadsContent(
        state = StoryListUiState(isLoading = false, stories = PreviewData.stories.filter { it.isDownloaded }),
        onStoryClick = {},
        onRemoveDownload = {},
    )
}

@ScreenPreviews
@Composable
private fun DownloadsEmptyPreview() = PreviewTheme {
    DownloadsContent(state = StoryListUiState(isLoading = false), onStoryClick = {}, onRemoveDownload = {})
}
