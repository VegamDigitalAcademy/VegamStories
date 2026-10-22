package com.vegamstories.app.presentation.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vegamstories.app.R
import com.vegamstories.app.domain.model.PageBackground
import com.vegamstories.app.domain.model.ReaderScrollMode
import com.vegamstories.app.domain.model.ReaderSettings
import com.vegamstories.app.domain.model.StoryDetail
import com.vegamstories.app.domain.model.ReaderTextSize
import com.vegamstories.app.domain.model.StoryFormat
import com.vegamstories.app.domain.model.StoryText
import com.vegamstories.app.domain.model.Language
import com.vegamstories.app.presentation.components.EmptyState
import com.vegamstories.app.presentation.components.LikeButton
import com.vegamstories.app.presentation.components.LoadingBox
import com.vegamstories.app.presentation.preview.PreviewData
import com.vegamstories.app.presentation.preview.PreviewTheme
import com.vegamstories.app.presentation.preview.ScreenPreviews
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

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
        onScrollModeChange = viewModel::onScrollModeChange,
        onKeepScreenOnChange = viewModel::onKeepScreenOnChange,
        onPageBackgroundChange = viewModel::onPageBackgroundChange,
        onFormatSelected = viewModel::onFormatSelected,
        onLanguageSelected = viewModel::onLanguageSelected,
        onTextProgress = viewModel::onTextProgress,
        onTextSizeChange = viewModel::onTextSizeChange,
    )
}

/** Stateless reader: loading, not-found, or the pages. Used by the screen and by previews. */
@Composable
fun ReaderScreenContent(
    state: ReaderUiState,
    onBack: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleDownload: () -> Unit,
    onOpenNextStory: (String) -> Unit,
    onScrollModeChange: (ReaderScrollMode) -> Unit = {},
    onKeepScreenOnChange: (Boolean) -> Unit = {},
    onPageBackgroundChange: (PageBackground) -> Unit = {},
    onFormatSelected: (StoryFormat) -> Unit = {},
    onLanguageSelected: (languageCode: String) -> Unit = {},
    onTextProgress: (paragraphIndex: Int, paragraphCount: Int) -> Unit = { _, _ -> },
    onTextSizeChange: (ReaderTextSize) -> Unit = {},
) {
    ReaderPageTheme(background = state.settings.pageBackground) {
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
                    onScrollModeChange = onScrollModeChange,
                    onKeepScreenOnChange = onKeepScreenOnChange,
                    onPageBackgroundChange = onPageBackgroundChange,
                    onFormatSelected = onFormatSelected,
                    onLanguageSelected = onLanguageSelected,
                    onTextProgress = onTextProgress,
                    onTextSizeChange = onTextSizeChange,
                )
            }
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
    onScrollModeChange: (ReaderScrollMode) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onPageBackgroundChange: (PageBackground) -> Unit,
    onFormatSelected: (StoryFormat) -> Unit,
    onLanguageSelected: (languageCode: String) -> Unit,
    onTextProgress: (paragraphIndex: Int, paragraphCount: Int) -> Unit,
    onTextSizeChange: (ReaderTextSize) -> Unit,
) {
    val story = detail.story
    // Lives above both readers, so switching paged ↔ vertical stays on the same page.
    var currentPage by rememberSaveable { mutableIntStateOf(state.initialPage) }
    var showOptions by rememberSaveable { mutableStateOf(false) }
    val onPageSettled: (Int) -> Unit = { page ->
        currentPage = page
        onPageChanged(page) // Persist progress whenever the visible page settles.
    }

    KeepScreenOn(enabled = state.settings.keepScreenOn)

    Column(Modifier.fillMaxSize()) Column@{
        ReaderTopBar(
            title = story.title,
            isFavorite = story.isFavorite,
            likeCount = story.likeCount,
            isDownloaded = story.isDownloaded,
            onBack = onBack,
            onToggleFavorite = onToggleFavorite,
            onToggleDownload = onToggleDownload,
            onOpenOptions = { showOptions = true },
        )
        if (state.formats.size > 1) {
            FormatTabs(
                formats = state.formats,
                selected = state.format,
                onSelect = onFormatSelected,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (state.languages.size > 1) {
            LanguageChips(
                languages = state.languages,
                selectedCode = state.language,
                onSelect = onLanguageSelected,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
        val readerModifier = Modifier
            .weight(1f)
            .fillMaxWidth()
        // "Did you like this story?" + next episode, at the end of the text and under the video.
        val likeSection: @Composable () -> Unit = {
            LikeStoryPage(
                story = story,
                nextStory = state.nextStory,
                onToggleLike = onToggleFavorite,
                onOpenNextStory = onOpenNextStory,
                onBack = onBack,
            )
        }
        val text = state.text
        when {
            state.format == StoryFormat.TEXT && text != null -> {
                TextReader(
                    story = story,
                    text = text,
                    textSize = state.settings.textSize,
                    startFraction = state.initialTextFraction,
                    onProgress = onTextProgress,
                    endContent = likeSection,
                    modifier = readerModifier,
                )
                return@Column
            }
            state.format == StoryFormat.VIDEO && story.video != null -> {
                WatchTab(story = story, endContent = likeSection, modifier = readerModifier)
                return@Column
            }
        }
        val page: @Composable (index: Int, onContinue: () -> Unit) -> Unit = { index, onContinue ->
            ReaderPage(
                index = index,
                state = state,
                detail = detail,
                // Paged mode shows comic pages edge to edge (see PagedReader); the strip keeps its frame.
                edgeToEdge = state.settings.scrollMode == ReaderScrollMode.PAGED,
                onContinue = onContinue,
                onToggleFavorite = onToggleFavorite,
                onOpenNextStory = onOpenNextStory,
                onBack = onBack,
            )
        }
        when (state.settings.scrollMode) {
            ReaderScrollMode.PAGED -> PagedReader(
                startPage = currentPage,
                pageCount = state.totalPagerPages,
                comicPageCount = detail.pages.size,
                likePageIndex = state.likePageIndex,
                onPageSettled = onPageSettled,
                page = page,
                modifier = readerModifier,
            )
            ReaderScrollMode.VERTICAL -> VerticalReader(
                startPage = currentPage,
                pageCount = state.totalPagerPages,
                likePageIndex = state.likePageIndex,
                onPageSettled = onPageSettled,
                page = page,
                modifier = readerModifier,
            )
        }
        PageIndicator(currentPage = currentPage, pageCount = state.totalPagerPages)
    }

    if (showOptions) {
        ReaderOptionsSheet(
            settings = state.settings,
            onScrollModeChange = onScrollModeChange,
            onKeepScreenOnChange = onKeepScreenOnChange,
            onPageBackgroundChange = onPageBackgroundChange,
            format = state.format,
            onTextSizeChange = onTextSizeChange,
            onDismiss = { showOptions = false },
        )
    }
}

/**
 * One page at a time, flipped sideways. Comic pages run edge to edge (no side padding) and sit
 * in the middle of the screen when they are shorter than it, as on a tall phone; taller pages
 * scroll vertically within their page. The like and video pages keep their normal padding.
 */
@Composable
private fun PagedReader(
    startPage: Int,
    pageCount: Int,
    comicPageCount: Int,
    likePageIndex: Int,
    onPageSettled: (Int) -> Unit,
    page: @Composable (index: Int, onContinue: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(initialPage = startPage) { pageCount }
    val scope = rememberCoroutineScope()
    val currentOnPageSettled by rememberUpdatedState(onPageSettled)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { currentOnPageSettled(it) }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        beyondViewportPageCount = 1,
    ) { index ->
        val isComic = index < comicPageCount
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewportHeight = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    // At least one screen tall, so a shorter page can be centred in it.
                    .heightIn(min = viewportHeight)
                    .padding(horizontal = if (isComic) 0.dp else 16.dp, vertical = 8.dp),
                verticalArrangement = if (isComic) Arrangement.Center else Arrangement.Top,
            ) {
                page(index) { scope.launch { pagerState.animateScrollToPage(likePageIndex) } }
            }
        }
    }
}

/** Every page in one continuous strip (webtoon style), followed by the like and video pages. */
@Composable
private fun VerticalReader(
    startPage: Int,
    pageCount: Int,
    likePageIndex: Int,
    onPageSettled: (Int) -> Unit,
    page: @Composable (index: Int, onContinue: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startPage)
    val scope = rememberCoroutineScope()
    val currentOnPageSettled by rememberUpdatedState(onPageSettled)

    LaunchedEffect(listState) {
        snapshotFlow { listState.pageAtCenter() }
            .distinctUntilChanged()
            .collect { currentOnPageSettled(it) }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(count = pageCount) { index ->
            Column(Modifier.fillMaxWidth()) {
                page(index) { scope.launch { listState.animateScrollToItem(likePageIndex) } }
            }
        }
    }
}

/**
 * The page the reader is looking at: the one under the middle of the screen, or the last
 * one once the strip can't scroll any further (a short final page never reaches the middle).
 */
private fun LazyListState.pageAtCenter(): Int {
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) return firstVisibleItemIndex
    if (!canScrollForward) return visible.last().index
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return visible.firstOrNull { it.offset <= center && it.offset + it.size > center }?.index
        ?: firstVisibleItemIndex
}

/** What goes on page [index]: a comic page or the like page. */
@Composable
private fun ReaderPage(
    index: Int,
    state: ReaderUiState,
    detail: StoryDetail,
    edgeToEdge: Boolean,
    onContinue: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenNextStory: (String) -> Unit,
    onBack: () -> Unit,
) {
    when {
        index < detail.pages.size -> {
            ComicPage(page = detail.pages[index], modifier = Modifier.fillMaxWidth(), framed = !edgeToEdge)
            if (index == detail.pages.lastIndex) {
                // The like page comes next; it also offers the next episode.
                EndOfStory(
                    onContinue = onContinue,
                    modifier = Modifier.padding(horizontal = if (edgeToEdge) 16.dp else 0.dp),
                )
            }
        }
        index == state.likePageIndex -> LikeStoryPage(
            story = detail.story,
            nextStory = state.nextStory,
            onToggleLike = onToggleFavorite,
            onOpenNextStory = onOpenNextStory,
            onBack = onBack,
        )
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
    onOpenOptions: () -> Unit,
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
        /*Text(
            text = stringResource(R.string.brand_short),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )*/
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onOpenOptions) {
            Icon(
                painter = painterResource(R.drawable.ic_tune),
                contentDescription = stringResource(R.string.cd_reader_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        IconButton(onClick = onToggleDownload) {
            Icon(
                painter = painterResource(if (isDownloaded) R.drawable.ic_download_done else R.drawable.ic_download),
                contentDescription = stringResource(R.string.cd_download),
                tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        LikeButton(isLiked = isFavorite, likeCount = likeCount, onClick = onToggleFavorite)
    }
}

@Composable
private fun EndOfStory(onContinue: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
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
        Button(
            onClick = onContinue,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) { Text(stringResource(R.string.continue_action)) }
    }
}

@Composable
private fun PageIndicator(currentPage: Int, pageCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(pageCount) { i ->
                val selected = i == currentPage
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
            text = stringResource(R.string.page_of, currentPage + 1, pageCount),
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
        state = ReaderUiState(isLoading = false, detail = previewDetail, nextStory = PreviewData.story(index = 1)),
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
            nextStory = PreviewData.story(index = 1),
            initialPage = previewDetail.pages.lastIndex,
        ),
        onBack = {}, onPageChanged = {}, onToggleFavorite = {}, onToggleDownload = {}, onOpenNextStory = {},
    )
}

@ScreenPreviews
@Composable
private fun ReaderLikePagePreview() = PreviewTheme {
    ReaderScreenContent(
        state = ReaderUiState(
            isLoading = false,
            detail = previewDetail,
            nextStory = PreviewData.story(index = 1),
            initialPage = previewDetail.pages.size, // the page after the comic = "Did you like it?"
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
            selectedFormat = StoryFormat.VIDEO, // the Watch tab
        ),
        onBack = {}, onPageChanged = {}, onToggleFavorite = {}, onToggleDownload = {}, onOpenNextStory = {},
    )
}

@ScreenPreviews
@Composable
private fun ReaderTextPreview() = PreviewTheme {
    ReaderScreenContent(
        state = ReaderUiState(
            isLoading = false,
            detail = previewDetail.copy(
                story = previewDetail.story.copy(hasText = true),
                text = StoryText("te", "ఒకప్పుడు ఒక చిన్న ఊరిలో ఒక నిజాయితీ గల కట్టెలు కొట్టేవాడు ఉండేవాడు.\n\nనీతి: నిజాయితీయే గొప్ప బహుమతి."),
                languages = listOf(Language("te", "Telugu", "తెలుగు"), Language("en", "English", "English")),
            ),
            selectedFormat = StoryFormat.TEXT,
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

@ScreenPreviews
@Composable
private fun ReaderVerticalSepiaPreview() = PreviewTheme {
    ReaderScreenContent(
        state = ReaderUiState(
            isLoading = false,
            detail = previewDetail,
            settings = ReaderSettings(scrollMode = ReaderScrollMode.VERTICAL, pageBackground = PageBackground.SEPIA),
        ),
        onBack = {}, onPageChanged = {}, onToggleFavorite = {}, onToggleDownload = {}, onOpenNextStory = {},
    )
}

@ScreenPreviews
@Composable
private fun ReaderDarkPagePreview() = PreviewTheme {
    ReaderScreenContent(
        state = ReaderUiState(
            isLoading = false,
            detail = previewDetail,
            nextStory = PreviewData.story(index = 1),
            initialPage = previewDetail.pages.size,
            settings = ReaderSettings(pageBackground = PageBackground.DARK),
        ),
        onBack = {}, onPageChanged = {}, onToggleFavorite = {}, onToggleDownload = {}, onOpenNextStory = {},
    )
}

