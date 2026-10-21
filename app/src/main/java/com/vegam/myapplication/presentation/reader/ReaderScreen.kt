package com.vegam.myapplication.presentation.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vegam.myapplication.R
import com.vegam.myapplication.domain.model.StoryDetail
import com.vegam.myapplication.presentation.components.EmptyState
import com.vegam.myapplication.presentation.components.FavoriteButton
import com.vegam.myapplication.presentation.components.LoadingBox
import com.vegam.myapplication.presentation.preview.PreviewData
import com.vegam.myapplication.presentation.preview.PreviewTheme
import com.vegam.myapplication.presentation.preview.ScreenPreviews

@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    onOpenNextStory: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val favAdded = stringResource(R.string.added_to_favorites)
    val favRemoved = stringResource(R.string.removed_from_favorites)
    val dlAdded = stringResource(R.string.download_started)
    val dlRemoved = stringResource(R.string.download_removed)
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is ReaderEvent.FavoriteChanged -> onShowMessage(if (event.isFavorite) favAdded else favRemoved)
                is ReaderEvent.DownloadChanged -> onShowMessage(if (event.isDownloaded) dlAdded else dlRemoved)
            }
        }
    }

    ReaderScreenContent(
        state = state,
        onBack = onBack,
        onPageChanged = viewModel::onPageChanged,
        onToggleFavorite = viewModel::onToggleFavorite,
        onToggleDownload = viewModel::onToggleDownload,
        onOpenNextStory = onOpenNextStory,
    )
}

/** Stateless reader: loading, not-found, or the pager. Used by the screen and by previews. */
@Composable
fun ReaderScreenContent(
    state: ReaderUiState,
    onBack: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleDownload: () -> Unit,
    onOpenNextStory: (String) -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        val detail = state.detail
        when {
            state.isLoading -> LoadingBox()
            detail == null -> EmptyState(
                icon = R.drawable.ic_info,
                title = stringResource(R.string.story_not_found),
                message = "",
                action = { OutlinedButton(onClick = onBack) { Text(stringResource(R.string.back_to_stories)) } },
            )
            else -> ReaderContent(
                state = state,
                detail = detail,
                onBack = onBack,
                onPageChanged = onPageChanged,
                onToggleFavorite = onToggleFavorite,
                onToggleDownload = onToggleDownload,
                onOpenNextStory = onOpenNextStory,
            )
        }
    }
}

@Composable
private fun ReaderContent(
    state: ReaderUiState,
    detail: StoryDetail,
    onBack: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleDownload: () -> Unit,
    onOpenNextStory: (String) -> Unit,
) {
    val story = detail.story
    val pagerState = rememberPagerState(initialPage = state.initialPage) { state.totalPagerPages }
    val uriHandler = LocalUriHandler.current

    // Persist progress whenever the visible page settles.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { onPageChanged(it) }
    }

    Column(Modifier.fillMaxSize()) {
        ReaderTopBar(
            title = story.title,
            isFavorite = story.isFavorite,
            likeCount = story.likeCount,
            isDownloaded = story.isDownloaded,
            onBack = onBack,
            onToggleFavorite = onToggleFavorite,
            onToggleDownload = onToggleDownload,
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            beyondViewportPageCount = 1,
        ) { index ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                if (index < detail.pages.size) {
                    ComicPage(page = detail.pages[index], modifier = Modifier.fillMaxWidth())
                    if (index == detail.pages.lastIndex) {
                        EndOfStory(
                            nextStoryId = state.nextStoryId,
                            onOpenNextStory = onOpenNextStory,
                            onBack = onBack,
                        )
                    }
                } else {
                    story.video?.let { video ->
                        // runCatching: no browser/YouTube app installed must not crash the reader.
                        VideoPage(video = video, onWatch = { runCatching { uriHandler.openUri(video.videoUrl) } })
                    }
                }
            }
        }
        PageIndicator(pagerState = pagerState)
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    isFavorite: Boolean,
    likeCount: Int,
    isDownloaded: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleDownload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.cd_back))
        }
        Text(
            text = stringResource(R.string.brand_short),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onToggleDownload) {
            Icon(
                painter = painterResource(if (isDownloaded) R.drawable.ic_download_done else R.drawable.ic_download),
                contentDescription = stringResource(R.string.cd_download),
                tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        FavoriteButton(isFavorite = isFavorite, likeCount = likeCount, onClick = onToggleFavorite)
    }
}

@Composable
private fun EndOfStory(
    nextStoryId: String?,
    onOpenNextStory: (String) -> Unit,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.the_end),
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        if (nextStoryId != null) {
            Button(
                onClick = { onOpenNextStory(nextStoryId) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) { Text(stringResource(R.string.next_story)) }
        } else {
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.back_to_stories)) }
        }
    }
}

@Composable
private fun PageIndicator(pagerState: PagerState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(pagerState.pageCount) { i ->
                val selected = i == pagerState.currentPage
                Box(
                    Modifier
                        .size(width = if (selected) 18.dp else 6.dp, height = 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        ),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.page_of, pagerState.currentPage + 1, pagerState.pageCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------- Previews ----------------

private val previewDetail = PreviewData.storyDetail

@ScreenPreviews
@Composable
private fun ReaderFirstPagePreview() = PreviewTheme {
    ReaderScreenContent(
        state = ReaderUiState(isLoading = false, detail = previewDetail, nextStoryId = "moral-2"),
        onBack = {}, onPageChanged = {}, onToggleFavorite = {}, onToggleDownload = {}, onOpenNextStory = {},
    )
}

@ScreenPreviews
@Composable
private fun ReaderLastPagePreview() = PreviewTheme {
    ReaderScreenContent(
        state = ReaderUiState(
            isLoading = false,
            detail = previewDetail,
            nextStoryId = "moral-2",
            initialPage = previewDetail.pages.lastIndex,
        ),
        onBack = {}, onPageChanged = {}, onToggleFavorite = {}, onToggleDownload = {}, onOpenNextStory = {},
    )
}

@ScreenPreviews
@Composable
private fun ReaderVideoPagePreview() = PreviewTheme {
    ReaderScreenContent(
        state = ReaderUiState(
            isLoading = false,
            detail = previewDetail,
            initialPage = previewDetail.pages.size, // the page after the comic = video
        ),
        onBack = {}, onPageChanged = {}, onToggleFavorite = {}, onToggleDownload = {}, onOpenNextStory = {},
    )
}

@ScreenPreviews
@Composable
private fun ReaderLoadingPreview() = PreviewTheme {
    ReaderScreenContent(
        state = ReaderUiState(isLoading = true),
        onBack = {}, onPageChanged = {}, onToggleFavorite = {}, onToggleDownload = {}, onOpenNextStory = {},
    )
}

@ScreenPreviews
@Composable
private fun ReaderNotFoundPreview() = PreviewTheme {
    ReaderScreenContent(
        state = ReaderUiState(isLoading = false, detail = null),
        onBack = {}, onPageChanged = {}, onToggleFavorite = {}, onToggleDownload = {}, onOpenNextStory = {},
    )
}
