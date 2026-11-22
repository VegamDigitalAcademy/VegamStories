package com.vegamstories.app.presentation.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.CircularProgressIndicator
import androidx.activity.compose.BackHandler
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect
import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.presentation.navigation.LocalStoryOpener
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import kotlinx.coroutines.delay
import com.vegamstories.app.domain.model.Bookmark
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vegamstories.app.R
import com.vegamstories.app.presentation.navigation.LocalNewDownloads
import com.vegamstories.app.domain.model.PageBackground
import com.vegamstories.app.domain.model.ReaderScrollMode
import com.vegamstories.app.domain.model.ReaderSettings
import com.vegamstories.app.domain.model.StoryDetail
import com.vegamstories.app.domain.model.ReaderTextSize
import com.vegamstories.app.domain.model.StoryFormat
import com.vegamstories.app.domain.model.StoryText
import com.vegamstories.app.domain.model.Language
import com.vegamstories.app.presentation.components.EmptyState
import com.vegamstories.app.presentation.components.FavoriteButton
import com.vegamstories.app.presentation.components.LikeButton
import com.vegamstories.app.presentation.components.LoadingBox
import com.vegamstories.app.presentation.components.windowIsWideLandscape
import com.vegamstories.app.presentation.components.pageTurn
import com.vegamstories.app.presentation.preview.PreviewData
import com.vegamstories.app.presentation.preview.PreviewTheme
import com.vegamstories.app.presentation.preview.ScreenPreviews
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    // Opened from its card, the card's picture grew into the cover over the app: that can go once
    // this screen is drawn under it (two frames on).
    val storyOpener = LocalStoryOpener.current
    LaunchedEffect(viewModel) {
        withFrameNanos {}
        withFrameNanos {}
        storyOpener.arrived(viewModel.storyId)
    }
    // Back (the arrow, Back to stories, or the phone's Back): the reader shrinks into its card,
    // if that card is on the screen behind (StoryClosingOverlay).
    val readerBounds = remember { arrayOfNulls<Rect>(1) }
    val animates = animationsOn()
    val goBack: () -> Unit = {
        readerBounds[0]?.takeIf { animates }?.let { storyOpener.closing(viewModel.storyId, it) }
        onBack()
    }
    BackHandler(onBack = goBack)

    ReaderScreenContent(
        state = state,
        onBack = goBack,
        onPageChanged = viewModel::onPageChanged,
        onToggleFavorite = viewModel::onToggleFavorite,
        onToggleLike = viewModel::onToggleLike,
        onToggleDownload = viewModel::onToggleDownload,
        onOpenNextStory = onOpenNextStory,
        onScrollModeChange = viewModel::onScrollModeChange,
        onKeepScreenOnChange = viewModel::onKeepScreenOnChange,
        onPageBackgroundChange = viewModel::onPageBackgroundChange,
        onFormatSelected = viewModel::onFormatSelected,
        onLanguageSelected = viewModel::onLanguageSelected,
        onTextProgress = viewModel::onTextProgress,
        onTextSizeChange = viewModel::onTextSizeChange,
        onAddBookmark = viewModel::onAddBookmark,
        onRemoveBookmarks = viewModel::onRemoveBookmarks,
        onTurnHintShown = viewModel::onPageTurnHintShown,
        openingStory = storyOpener.story?.takeIf { it.id == viewModel.storyId },
        openingFormat = viewModel.preferredFormat.collectAsStateWithLifecycle().value,
        downloadProgress = viewModel.downloadProgress.collectAsStateWithLifecycle().value,
        modifier = Modifier.onGloballyPositioned { readerBounds[0] = it.boundsInRoot() },
    )
}

/** Stateless reader: loading, not-found, or the pages. Used by the screen and by previews. */
@Composable
fun ReaderScreenContent(
    state: ReaderUiState,
    onBack: () -> Unit,
    onPageChanged: (Int) -> Unit,
    /** The star: saves the story in Favorites, or takes it out. */
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
    onAddBookmark: (format: StoryFormat, position: Int, offset: Int) -> Unit = { _, _, _ -> },
    onRemoveBookmarks: (List<Bookmark>) -> Unit = {},
    onTurnHintShown: () -> Unit = {},
    /** The heart: likes the story (counted on the server), or unlikes it. Separate from Favorites. */
    onToggleLike: () -> Unit = {},
    /** Page turns, tears and glue (Settings → Reading → Page turn sound). */
    sounds: ReaderSounds = rememberReaderSounds(enabled = state.settings.pageTurnSound),
    /** A story opened at its start opens on its cover and turns it; false goes straight to its first page. */
    openOnCover: Boolean = true,
    /** The story as its card showed it, until the reader is ready for it (see [ReaderOpening]). */
    openingStory: Story? = null,
    /** The tab the reader will open on, as far as is known before the story has loaded. */
    openingFormat: StoryFormat? = null,
    /** The story's download while it saves, 0 … 1 (the download button's jar fills); else null. */
    downloadProgress: Float? = null,
    modifier: Modifier = Modifier,
) {
    ReaderPageTheme(background = state.settings.pageBackground) {
        // Read to the end for the first time: a little celebration (StoryFinished), once.
        val story = state.detail?.story
        var finishedBefore by rememberSaveable { mutableStateOf<Boolean?>(null) }
        LaunchedEffect(story != null) {
            if (story != null && finishedBefore == null) finishedBefore = story.isRead
        }
        var celebrating by rememberSaveable { mutableStateOf(false) }
        val likeCover = remember { LikeCoverSpot() }
        // Download tapped: the story's book drops into the download button (DownloadDrop), and the
        // Downloads tab hops once the bottom bar is back.
        val downloadSpot = remember { DownloadSpot() }
        var dropping by remember { mutableStateOf(false) }
        val newDownloads = LocalNewDownloads.current
        val animated = animationsOn()
        val currentOnToggleDownload by rememberUpdatedState(onToggleDownload)
        val currentStory by rememberUpdatedState(story)
        // Remembered, like onReachedEnd: a new one each time would recompose the whole reader.
        val toggleDownload: () -> Unit = remember {
            {
                val saving = currentStory
                if (saving != null && !saving.isDownloaded) {
                    newDownloads.added()
                    if (animated) dropping = true
                }
                currentOnToggleDownload()
            }
        }
        var celebrated by rememberSaveable { mutableStateOf(false) }
        // Remembered: a new one each time would recompose the whole reader (a static local).
        val onReachedEnd: () -> Unit = remember {
            {
                if (finishedBefore == false && !celebrated) {
                    celebrated = true
                    celebrating = true
                }
            }
        }
        CompositionLocalProvider(
            LocalReaderSounds provides sounds,
            LocalTornPages provides state.settings.tornPages,
            LocalStoryLanguage provides state.language,
            LocalOnReachedEnd provides onReachedEnd,
            LocalLikeCover provides likeCover,
            LocalDownloadSpot provides downloadSpot,
        ) {
        Box(
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            val detail = state.detail
            // A story opened from its card: until it has loaded, the bars and the cover, as the card's
            // picture left them (ReaderOpening).
            var arrived by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(state.isLoading) { if (!state.isLoading) arrived = true }
            when {
                openingStory != null && !arrived -> ReaderOpening(
                    story = openingStory,
                    format = if (state.isLoading) openingFormat else state.format,
                    onBack = onBack,
                )
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
                    onToggleLike = onToggleLike,
                    onToggleDownload = toggleDownload,
                    onOpenNextStory = onOpenNextStory,
                    onScrollModeChange = onScrollModeChange,
                    onKeepScreenOnChange = onKeepScreenOnChange,
                    onPageBackgroundChange = onPageBackgroundChange,
                    onFormatSelected = onFormatSelected,
                    onLanguageSelected = onLanguageSelected,
                    onTextProgress = onTextProgress,
                    onTextSizeChange = onTextSizeChange,
                    onAddBookmark = onAddBookmark,
                    onRemoveBookmarks = onRemoveBookmarks,
                    onTurnHintShown = onTurnHintShown,
                    openOnCover = openOnCover,
                    downloadProgress = downloadProgress,
                )
            }
            if (celebrating && story != null) StoryFinished(story, onDone = { celebrating = false }, likeCover = likeCover)
            if (dropping && story != null) DownloadDrop(story, downloadSpot, onDone = { dropping = false })
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
    onToggleLike: () -> Unit,
    onToggleDownload: () -> Unit,
    onOpenNextStory: (String) -> Unit,
    onScrollModeChange: (ReaderScrollMode) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onPageBackgroundChange: (PageBackground) -> Unit,
    onFormatSelected: (StoryFormat) -> Unit,
    onLanguageSelected: (languageCode: String) -> Unit,
    onTextProgress: (paragraphIndex: Int, paragraphCount: Int) -> Unit,
    onTextSizeChange: (ReaderTextSize) -> Unit,
    onAddBookmark: (format: StoryFormat, position: Int, offset: Int) -> Unit,
    onRemoveBookmarks: (List<Bookmark>) -> Unit,
    onTurnHintShown: () -> Unit,
    openOnCover: Boolean,
    downloadProgress: Float?,
) {
    val story = detail.story
    // Lives above both readers, so switching paged ↔ vertical stays on the same page.
    var currentPage by rememberSaveable { mutableIntStateOf(state.initialPage) }
    // Page by page, the story's cover comes before its first page. Opened at its start, the story
    // opens on the cover and turns it by itself, once; picked up where the reader left off, it
    // opens there.
    val animated = animationsOn()
    var coverToTurn by rememberSaveable {
        mutableStateOf(openOnCover && animated && state.initialPage == 0 && state.initialTextFraction == 0f && state.textBookmarkStart == null)
    }
    val cover: @Composable () -> Unit = { ReaderCover(story) }
    var showOptions by rememberSaveable { mutableStateOf(false) }
    val onPageSettled: (Int) -> Unit = { page ->
        currentPage = page
        onPageChanged(page) // Persist progress whenever the visible page settles.
    }

    KeepScreenOn(enabled = state.settings.keepScreenOn)
    // The auto-turn countdown: run by the reader below, shown filling the page's bar in PageIndicator.
    val pageTimer = remember { PageTimer() }

    Column(Modifier.fillMaxSize()) Column@{
        ReaderTopBar(
            title = story.title,
            isFavorite = story.isFavorite,
            isLiked = story.isLiked,
            likeCount = story.likeCount,
            isDownloaded = story.isDownloaded,
            downloadProgress = downloadProgress,
            onBack = onBack,
            onToggleFavorite = onToggleFavorite,
            onToggleLike = onToggleLike,
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
        val readerModifier = Modifier
            .weight(1f)
            .fillMaxWidth()
        // "Did you like this story?" + next episode, at the end of the text and under the video.
        val likeSection: @Composable () -> Unit = {
            LikeStoryPage(
                story = story,
                nextStory = state.nextStory,
                onToggleLike = onToggleLike,
                onOpenNextStory = onOpenNextStory,
                onBack = onBack,
                moral = state.moral,
            )
        }
        val text = state.text
        when (state.format) {
            StoryFormat.TEXT if text != null -> {
                TextPagesReader(
                    cover = cover,
                    openOnCover = coverToTurn,
                    onCoverTurned = { coverToTurn = false },
                    title = story.title,
                    text = text,
                    textSize = state.settings.textSize,
                    startFraction = state.initialTextFraction,
                    startAt = state.textBookmarkStart,
                    // A page of text takes longer to read than a comic page.
                    autoTurnMillis = state.settings.autoTurnMillis?.let { TEXT_AUTO_TURN_MILLIS },
                    timer = pageTimer,
                    bookmarks = state.bookmarks,
                    onAddBookmark = onAddBookmark,
                    onRemoveBookmarks = onRemoveBookmarks,
                    showTurnHint = !state.settings.pageTurnHintShown,
                    onTurnHintShown = onTurnHintShown,
                    onProgress = onTextProgress,
                    likePage = likeSection,
                    modifier = readerModifier,
                )
                return@Column
            }
            StoryFormat.VIDEO if story.video != null -> {
                WatchTab(story = story, endContent = likeSection, modifier = readerModifier)
                return@Column
            }

            else -> {}
        }
        // A wide screen on its side shows comic pages two at a time, like an open book.
        val spreads = ComicSpreads(detail.pages.size).takeIf {
            state.settings.scrollMode == ReaderScrollMode.PAGED && detail.pages.size > 1 && windowIsWideLandscape()
        }
        val page: @Composable (index: Int, onContinue: () -> Unit) -> Unit = { index, onContinue ->
            ReaderPage(
                index = index,
                state = state,
                detail = detail,
                // Paged mode shows comic pages edge to edge (see PagedReader); the strip keeps its frame.
                edgeToEdge = state.settings.scrollMode == ReaderScrollMode.PAGED,
                onContinue = onContinue,
                onToggleLike = onToggleLike,
                onOpenNextStory = onOpenNextStory,
                onBack = onBack,
            )
        }
        // Double-tapping a comic page likes the story (never unlikes it): see doubleTapToLike.
        val currentStory by rememberUpdatedState(story)
        val currentOnToggleLike by rememberUpdatedState(onToggleLike)
        val likeOnDoubleTap: () -> Unit = remember { { if (!currentStory.isLiked) currentOnToggleLike() } }
        CompositionLocalProvider(LocalLikeOnDoubleTap provides likeOnDoubleTap) {
            when (state.settings.scrollMode) {
                ReaderScrollMode.PAGED if spreads != null -> BoxWithConstraints(readerModifier) {
                    // A page's room: the reader's height less the pager's margins above and below.
                    val pageRoom = maxHeight - SpreadPageMargin * 2
                    PagedReader(
                        startPage = spreads.spreadOf(currentPage),
                        autoTurnMillis = state.settings.autoTurnMillis,
                        timer = pageTimer,
                        pageCount = spreads.pagerPageCount,
                        comicPageCount = spreads.spreadCount,
                        likePageIndex = spreads.likeSpread,
                        onPageSettled = { spread -> onPageSettled(spreads.readTo(spread)) },
                        page = { spread, onContinue ->
                            if (spread < spreads.spreadCount) {
                                ComicSpread(
                                    pages = spreads.pagesOn(spread),
                                    state = state,
                                    detail = detail,
                                    maxPageHeight = pageRoom,
                                    onContinue = onContinue,
                                    onAddBookmark = onAddBookmark,
                                    onRemoveBookmarks = onRemoveBookmarks,
                                )
                            } else {
                                page(state.likePageIndex, onContinue)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        pageMargin = SpreadPageMargin,
                        showTurnHint = !state.settings.pageTurnHintShown,
                        onTurnHintShown = onTurnHintShown,
                    )
                }
                ReaderScrollMode.PAGED -> PagedReader(
                    cover = cover,
                    openOnCover = coverToTurn,
                    onCoverTurned = { coverToTurn = false },
                    startPage = currentPage,
                    autoTurnMillis = state.settings.autoTurnMillis,
                    timer = pageTimer,
                    pageCount = state.totalPagerPages,
                    comicPageCount = detail.pages.size,
                    likePageIndex = state.likePageIndex,
                    onPageSettled = onPageSettled,
                    page = page,
                    modifier = readerModifier,
                    showTurnHint = !state.settings.pageTurnHintShown,
                    onTurnHintShown = onTurnHintShown,
                    pageOverlay = { index ->
                        if (index < detail.pages.size) {
                            ComicBookmarkRibbon(
                                index = index,
                                state = state,
                                onAddBookmark = onAddBookmark,
                                onRemoveBookmarks = onRemoveBookmarks,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(end = 12.dp),
                            )
                        }
                    },
                )
                ReaderScrollMode.VERTICAL -> VerticalReader(
                    startPage = currentPage,
                    autoTurnMillis = state.settings.autoTurnMillis,
                    timer = pageTimer,
                    pageCount = state.totalPagerPages,
                    likePageIndex = state.likePageIndex,
                    onPageSettled = onPageSettled,
                    page = page,
                    modifier = readerModifier,
                )
            }
        }
        // Two pages side by side fill one bar between them.
        PageIndicator(
            currentPage = spreads?.spreadOf(currentPage) ?: currentPage,
            pageCount = spreads?.pagerPageCount ?: state.totalPagerPages,
            timer = pageTimer,
            autoTurn = state.settings.autoTurnMillis != null,
            lastAutoPage = spreads?.likeSpread ?: state.likePageIndex,
        )
    }

    if (showOptions) {
        ReaderOptionsSheet(
            settings = state.settings,
            onScrollModeChange = onScrollModeChange,
            onKeepScreenOnChange = onKeepScreenOnChange,
            onPageBackgroundChange = onPageBackgroundChange,
            format = state.format,
            onTextSizeChange = onTextSizeChange,
            languages = state.languages,
            selectedLanguage = state.language,
            onLanguageSelected = onLanguageSelected,
            onDismiss = { showOptions = false },
        )
    }
}

/**
 * One page at a time, turned like a book page (see [bookPageTurn]). Comic pages run edge to edge
 * (no side padding) and sit in the middle of the screen when they are shorter than it, as on a
 * tall phone; taller pages scroll vertically within their page. The like page keeps its padding.
 */
@Composable
private fun PagedReader(
    startPage: Int,
    /** Auto-turn time per comic page; null = off. */
    autoTurnMillis: Long?,
    timer: PageTimer,
    pageCount: Int,
    comicPageCount: Int,
    likePageIndex: Int,
    onPageSettled: (Int) -> Unit,
    page: @Composable (index: Int, onContinue: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    /** The pages' colour, on their backs as they curl. */
    paper: Color = MaterialTheme.colorScheme.background,
    /** Under each page's content; transparent shows the reader's background around a smaller page. */
    pageBackground: Color = paper,
    /** Room above and below each page. */
    pageMargin: Dp = DefaultPageMargin,
    /** Lift the open page's corner once and let it fall back, to show pages turn; then [onTurnHintShown]. */
    showTurnHint: Boolean = false,
    onTurnHintShown: () -> Unit = {},
    /** Drawn over page [index], turning with it: the bookmark ribbon. */
    pageOverlay: @Composable BoxScope.(index: Int) -> Unit = {},
    /**
     * The story's cover, a page of its own before the first one (see [ReaderCover]). Page numbers
     * in and out of here leave it out: page 0 is still the story's first page.
     */
    cover: (@Composable () -> Unit)? = null,
    /** Open on the cover, and turn it by itself (unless the reader touches it first); then [onCoverTurned]. */
    openOnCover: Boolean = false,
    onCoverTurned: () -> Unit = {},
) {
    // The pager's own pages: the cover (if any), then the story's pages.
    val first = if (cover != null) 1 else 0
    val pagerState = rememberPagerState(initialPage = if (cover != null && openOnCover) 0 else startPage + first) {
        pageCount + first
    }
    val scope = rememberCoroutineScope()
    val currentOnPageSettled by rememberUpdatedState(onPageSettled)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { if (it >= first) currentOnPageSettled(it - first) }
    }
    val onReachedEnd by rememberUpdatedState(LocalOnReachedEnd.current)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { if (it == likePageIndex + first) onReachedEnd() }
    }

    // A story opened at its start: its cover shows once the card has grown into the reader, then
    // turns by itself to the first page. Not if the reader touches the page first.
    // Decided once, as it opens: [onCoverTurned] changes openOnCover, and that mustn't stop the
    // turn half-way.
    val turnCover = remember { cover != null && openOnCover }
    var touchedCover by remember { mutableStateOf(false) }
    if (turnCover) {
        val currentOnCoverTurned by rememberUpdatedState(onCoverTurned)
        LaunchedEffect(pagerState) {
            delay(COVER_TURN_DELAY_MILLIS)
            if (!touchedCover && pagerState.currentPage == 0 && !pagerState.isScrollInProgress) {
                pagerState.animateScrollToPage(1, animationSpec = pageTurnSpec)
            }
        }
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }.first { it >= 1 }
            currentOnCoverTurned()
        }
    }

    // A light tick when the reader's own swipe lands on another page. Not for the auto-turn or the
    // turn hint (no finger), and not for a swipe that springs back to the same page.
    val haptics = LocalHapticFeedback.current
    var swiped by remember { mutableStateOf(false) }
    // The reader is turning the pages themselves (swipes and taps), not the auto-turn or a jump.
    var turnedByReader by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState) {
        pagerState.interactionSource.interactions.collect {
            if (it is DragInteraction.Start) {
                swiped = true
                turnedByReader = true
            }
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect {
            if (swiped) haptics.pageTurn()
            swiped = false
        }
    }

    // The page-turn sound (Settings → Reading), as a page passes the middle: for a swipe, a tap and
    // the auto-turn alike. Not for the corner-lift hint or a swipe that springs back, which never
    // get that far. Fast turns get quicker sounds (see pageTurnPlayback).
    // Torn pages (Settings → Reading): the reader turning too many pages too fast tears the page
    // they reach. It stays there, and nothing turns it, until it's glued back (TearRepair).
    val sounds by rememberUpdatedState(LocalReaderSounds.current)
    val tornPages by rememberUpdatedState(LocalTornPages.current)
    val tear = remember { PageTear() }
    // Pages turning from a tap right now (a quick next tap takes over the one before).
    var tapTurns by remember { mutableIntStateOf(0) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.drop(1).collect { page ->
            sounds.pageTurn()
            if (!tornPages || !turnedByReader || page < first || page >= likePageIndex + first || tear.isTorn) {
                tear.fastTurns.reset()
                return@collect
            }
            if (tear.fastTurns.turned(withFrameMillis { it })) {
                tear.tear(page)
                // Turns still queued up stop here.
                scope.launch { pagerState.animateScrollToPage(page, animationSpec = quickPageTurnSpec) }
            }
        }
    }
    // Once the page has come to rest: the rip, and the loose piece falls.
    LaunchedEffect(tear.page) {
        if (!tear.isTorn) return@LaunchedEffect
        tear.resetMotion()
        snapshotFlow { pagerState.isScrollInProgress }.first { !it }
        sounds.tear()
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        tear.drop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow))
    }
    // Glued back (TearRepair): the squish, and a firm tick.
    val onTearFixed: () -> Unit = {
        sounds.glued()
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }

    // Auto-advance (Settings → Reading): each comic page turns to the next after autoTurnMillis.
    // Swiping restarts the wait; it stops at the like page.
    AutoAdvance(
        delayMillis = autoTurnMillis,
        timer = timer,
        page = pagerState.settledPage - first,
        userIsScrolling = pagerState.isScrollInProgress,
        lastAutoPage = likePageIndex,
        paused = tear.isTorn,
    ) {
        swiped = false // a swipe that sprang back before this turn
        turnedByReader = false
        pagerState.animateScrollToPage(pagerState.settledPage + 1, animationSpec = pageTurnSpec)
    }

    // Which way a turning page's fold leans: after the finger, else the app's own lean.
    val lean = remember { PageLean() }
    LaunchedEffect(pagerState, lean) { lean.follow(pagerState) }

    // The first story ever opened: the corner lifts a little and falls back. Decided once, so saving
    // that it was shown doesn't stop it halfway.
    // Not with the cover turning by itself: that shows pages turn already.
    val turnHint = remember { showTurnHint && !turnCover }
    if (turnHint) {
        val currentOnShown by rememberUpdatedState(onTurnHintShown)
        LaunchedEffect(pagerState) {
            delay(TURN_HINT_DELAY_MILLIS)
            // Only with a page to turn to, and not while the reader is turning one already.
            if (pagerState.isScrollInProgress || pagerState.currentPage >= pageCount + first - 1) return@LaunchedEffect
            currentOnShown()
            val lift = pagerState.layoutInfo.pageSize * TURN_HINT_LIFT
            pagerState.animateScrollBy(lift, tween(durationMillis = 650, easing = EaseOut))
            delay(150)
            pagerState.animateScrollBy(-lift, tween(durationMillis = 550, easing = EaseInOut))
        }
    }

    val hearts = rememberTapHearts()
    HorizontalPager(
        state = pagerState,
        // The curl's shadows stay inside the reader area.
        modifier = modifier
            .clipToBounds()
            // Double-tapping the middle of a comic page likes the story; its sides turn pages.
            .doubleTapToLike(hearts) { at, size ->
                pagerState.currentPage - first in 0 until comicPageCount &&
                    at.x > size.width / 3f && at.x < size.width * 2f / 3f
            }
            .leanWithFinger(lean, pagerState)
            .holdToPauseAutoTurn(timer)
            // Tapping the left or right third of a page turns back or on, like an e-reader.
            .then(
                if (turnCover) {
                    Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            touchedCover = true
                        }
                    }
                } else {
                    Modifier
                },
            )
            .tapToTurn(pagerState, pageCount + first) { target, quick ->
                if (tear.isTorn) return@tapToTurn
                turnedByReader = true
                scope.launch {
                    tapTurns++
                    try {
                        pagerState.animateScrollToPage(target, animationSpec = if (quick) quickPageTurnSpec else pageTurnSpec)
                    } finally {
                        tapTurns--
                    }
                }
            },
        beyondViewportPageCount = 1,
        flingBehavior = PagerDefaults.flingBehavior(state = pagerState, snapAnimationSpec = pageSettleSpec),
        // Not while a tapped page turns: a finger landing for the next tap would catch the page and
        // stop it there. Taps keep turning; a swipe works again once the page is down.
        userScrollEnabled = !tear.isTorn && tapTurns == 0,
    ) { index ->
        val isCover = index < first
        val isComic = index - first in 0 until comicPageCount
        // Which page lies on top changes only a few times per turn: recompose just then.
        val layer by remember(pagerState, index) {
            derivedStateOf { turnLayer(turnPosition(index, pagerState.currentPage, pagerState.currentPageOffsetFraction)) }
        }
        val tornLayer = rememberGraphicsLayer()
        BoxWithConstraints(
            Modifier
                .zIndex(layer)
                .fillMaxSize()
                .bookPageTurn(pagerState, index, lean, paper),
        ) {
            val viewportHeight = maxHeight
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (tear.page == index) Modifier.tornPage(tear, tornLayer, paper) else Modifier)
                    // Opaque, so the page underneath shows only where the turning page has lifted.
                    .background(pageBackground),
            ) {
                if (isCover) {
                    CoverSlot(pageMargin) { cover?.invoke() }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            // At least one screen tall, so a shorter page can be centred in it.
                            .heightIn(min = viewportHeight)
                            .padding(horizontal = if (isComic) 0.dp else 16.dp, vertical = pageMargin),
                        verticalArrangement = if (isComic) Arrangement.Center else Arrangement.Top,
                    ) {
                        page(index - first) {
                            turnedByReader = false // a jump, not turning
                            scope.launch { pagerState.animateScrollToPage(likePageIndex + first, animationSpec = pageTurnSpec) }
                        }
                    }
                }
                if (!isCover) pageOverlay(index - first)
            }
            // Over the torn page, not part of it: it stays put while the loose piece hangs.
            if (tear.page == index) TearRepair(tear, onFixed = onTearFixed, onDone = tear::finish)
        }
    }
}

/**
 * Where the cover sits: as big as the pages after it ([pageMargin] above and below, the book
 * pages' margin at the sides), the same in the pager and while the story loads. Opaque, so the
 * first page doesn't show through around it.
 */
@Composable
private fun CoverSlot(pageMargin: Dp, cover: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = BookPageMargin, vertical = pageMargin),
    ) {
        cover()
    }
}

/**
 * The reader as it first shows a story opened from its card, laid out as it will be once loaded:
 * the bar, the tabs (on [format]), and, opened at its start, the cover where its page will be. The
 * page bars keep their room, unseen. Drawn by the reader while the story loads, and over the app
 * (StoryOpeningOverlay) for the card's picture to grow into: [onCoverPlaced] says where the cover
 * is, and [coverAlpha] hides it until the picture has landed.
 */
@Composable
internal fun ReaderOpening(
    story: Story,
    format: StoryFormat?,
    onBack: () -> Unit,
    coverAlpha: () -> Float = { 1f },
    onCoverPlaced: ((Rect) -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize()) {
        ReaderTopBar(
            title = story.title,
            isFavorite = story.isFavorite,
            isLiked = story.isLiked,
            likeCount = story.likeCount,
            isDownloaded = story.isDownloaded,
            onBack = onBack,
            onToggleFavorite = {},
            onToggleLike = {},
            onToggleDownload = {},
            onOpenOptions = {},
        )
        if (story.formats.size > 1) {
            FormatTabs(
                formats = story.formats,
                selected = format?.takeIf { it in story.formats } ?: story.formats.first(),
                onSelect = {},
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        // As the reader decides once it has the story: opened at its start (and the phone animates),
        // on the cover; else on the page it was left on, here, where the card's picture goes instead.
        val onCover = story.opensOnCover && animationsOn()
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(
                    if (!onCover && onCoverPlaced != null) {
                        Modifier.onGloballyPositioned { onCoverPlaced(it.boundsInRoot()) }
                    } else {
                        Modifier
                    },
                ),
        ) {
            if (onCover) {
                // The page margin the reader will use for this tab (PagedReader's, or the book's).
                val margin = if (format == StoryFormat.TEXT) BookPageMargin else DefaultPageMargin
                CoverSlot(margin) { ReaderCover(story, pictureAlpha = coverAlpha, onPlaced = onCoverPlaced) }
            }
        }
        Box(Modifier.alpha(0f)) {
            PageIndicator(currentPage = 0, pageCount = 1, timer = remember { PageTimer() }, autoTurn = false, lastAutoPage = 0)
        }
    }
}

/**
 * A story opened from its card starts on its cover: never read, or read to the end, or left on
 * its first page. Otherwise it opens where it was left.
 */
internal val Story.opensOnCover: Boolean
    get() = progress.let { it == null || it.isCompleted || it.lastPageIndex == 0 }

/** Room above and below each page in the page-by-page reader. */
private val DefaultPageMargin = 8.dp

/** How long the cover stays, once the card has grown into the reader, before it turns by itself. */
private const val COVER_TURN_DELAY_MILLIS = 1_000L

/** The corner-lift hint: how long after the reader opens, and how far (a share of the page's width). */
private const val TURN_HINT_DELAY_MILLIS = 1_200L
private const val TURN_HINT_LIFT = 0.18f

/** How long each page of text waits before auto-turn turns it (comic pages: Settings → Reading). */
private const val TEXT_AUTO_TURN_MILLIS = 60_000L

/**
 * The Read tab as a book: the text split into pages that fit the screen (see [rememberTextBook]),
 * turned like the comic pages (curl, auto-turn, the bars below), then the like page. Reports the
 * last paragraph on the page as progress; the like page finishes the story.
 */
@Composable
private fun TextPagesReader(
    /** The story's cover, before the first page; opened on it, turned by itself (see PagedReader). */
    cover: @Composable () -> Unit,
    openOnCover: Boolean,
    onCoverTurned: () -> Unit,
    title: String,
    text: StoryText,
    textSize: ReaderTextSize,
    startFraction: Float,
    /** Opened from a bookmark in the text: start on its page, when the text is in its language. */
    startAt: Bookmark?,
    autoTurnMillis: Long?,
    timer: PageTimer,
    /** This story's bookmarks: the text ones in this language show as ribbons on their pages. */
    bookmarks: List<Bookmark>,
    onAddBookmark: (format: StoryFormat, position: Int, offset: Int) -> Unit,
    onRemoveBookmarks: (List<Bookmark>) -> Unit,
    showTurnHint: Boolean,
    onTurnHintShown: () -> Unit,
    onProgress: (paragraphIndex: Int, paragraphCount: Int) -> Unit,
    likePage: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paragraphs = text.paragraphs
    val start = startAt?.takeIf { it.language == text.languageCode }
    // Where the reader is in the text (paragraph, character), or at the end: survives a new layout
    // (text size, rotation), unlike a page number.
    var paragraph by rememberSaveable(text.languageCode) {
        mutableIntStateOf(start?.position ?: (startFraction * paragraphs.size).toInt().coerceIn(0, paragraphs.lastIndex.coerceAtLeast(0)))
    }
    var offset by rememberSaveable(text.languageCode) { mutableIntStateOf(start?.offset ?: 0) }
    var atEnd by rememberSaveable(text.languageCode) { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(1) }
    val colors = bookColors()
    val marks = bookmarks.filter { it.format == StoryFormat.TEXT && it.language == text.languageCode }

    Column(modifier) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // The book page keeps a little room from the screen's edges all round.
            val book = rememberTextBook(title, paragraphs, textSize, width = maxWidth, height = maxHeight - BookPageMargin * 2)
            val likeIndex = book.pages.size
            SideEffect { pageCount = likeIndex + 1 }
            key(text.languageCode, book.pages) {
                PagedReader(
                    cover = cover,
                    openOnCover = openOnCover,
                    onCoverTurned = onCoverTurned,
                    startPage = if (atEnd) likeIndex else book.pageAt(paragraph, offset),
                    autoTurnMillis = autoTurnMillis,
                    timer = timer,
                    pageCount = likeIndex + 1,
                    comicPageCount = likeIndex, // book pages run edge to edge, like comic pages
                    likePageIndex = likeIndex,
                    onPageSettled = { page ->
                        currentPage = page
                        atEnd = page >= likeIndex
                        if (page < likeIndex) {
                            book.pages[page].firstOrNull()?.let {
                                paragraph = it.paragraph
                                offset = it.start
                            }
                            onProgress(book.lastParagraphOn(page), paragraphs.size)
                        } else if (paragraphs.isNotEmpty()) {
                            onProgress(paragraphs.lastIndex, paragraphs.size)
                        }
                    },
                    page = { index, _ -> if (index < likeIndex) BookTextPage(book, index) else likePage() },
                    paper = colors.paper,
                    pageBackground = Color.Transparent,
                    pageMargin = BookPageMargin,
                    showTurnHint = showTurnHint,
                    onTurnHintShown = onTurnHintShown,
                    pageOverlay = { index ->
                        if (index < likeIndex) {
                            // A text bookmark is on the page its place in the text falls on.
                            val onPage = marks.filter { book.pageAt(it.position, it.offset) == index }
                            BookmarkRibbon(
                                marked = onPage.isNotEmpty(),
                                onToggle = {
                                    if (onPage.isNotEmpty()) {
                                        onRemoveBookmarks(onPage)
                                    } else {
                                        val first = book.pages[index].firstOrNull()
                                        onAddBookmark(StoryFormat.TEXT, first?.paragraph ?: 0, first?.start ?: 0)
                                    }
                                },
                                idleColor = colors.rule,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = BookPageMargin, end = BookRibbonEndPadding),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        PageIndicator(
            currentPage = currentPage,
            pageCount = pageCount,
            timer = timer,
            autoTurn = autoTurnMillis != null,
            lastAutoPage = pageCount - 1,
        )
    }
}

/**
 * A page turn started by the app (auto-turn, Continue), unhurried so the curl can be watched: the
 * corner lifts gently, the page sweeps across, then settles.
 */
private val pageTurnSpec = tween<Float>(durationMillis = 1100, easing = EaseInOut)

/** A tap while a page is still turning: the next one follows quickly, carrying on the movement. */
private val quickPageTurnSpec = tween<Float>(durationMillis = 450, easing = EaseOut)

/**
 * A page let go of mid-turn finishes turning (or falls back) at the same pace, slowing to a stop,
 * however fast the swipe was.
 */
private val pageSettleSpec = tween<Float>(durationMillis = 900, easing = EaseOut)

/**
 * Turns pages like a book instead of sliding them. Pages stay in one spot, stacked; turning forward
 * curls the open page away from its right edge (see [pageCurl]), following the finger, uncovering
 * the next page underneath. Turning back, the previous page uncurls back down over the open one.
 * Everything is read while drawing, so a turn redraws the pages without recomposing them.
 */
private fun Modifier.bookPageTurn(pagerState: PagerState, page: Int, lean: PageLean, paper: Color): Modifier {
    val position = { turnPosition(page, pagerState.currentPage, pagerState.currentPageOffsetFraction) }
    return this
        .graphicsLayer {
            translationX = -position() * size.width // cancel the pager's slide
            alpha = if (turnVisible(position())) 1f else 0f
        }
        .pageCurl(progress = { -position() }, lean = { lean.current.value }, paper = paper)
}

/**
 * Where [page] is in a turn: 0 = lying open on screen, toward -1 = being turned away (forward),
 * toward +1 = the next page, still underneath. Continuous through the whole turn.
 */
internal fun turnPosition(page: Int, currentPage: Int, offsetFraction: Float): Float =
    page - currentPage - offsetFraction

/** A page is seen from lying open until fully turned away; pages further away are hidden. */
internal fun turnVisible(position: Float): Boolean = position > -1f && position < 1f

/**
 * Drawing order: the page being turned lies over the one it uncovers; pages out of view go to the
 * bottom, so an invisible page never catches a tap.
 */
internal fun turnLayer(position: Float): Float = when {
    !turnVisible(position) -> -1f
    position <= 0f -> 1f
    else -> 0f
}

/** Every page in one continuous strip (webtoon style), followed by the like and video pages. */
@Composable
private fun VerticalReader(
    startPage: Int,
    /** Auto-turn time per comic page; null = off. */
    autoTurnMillis: Long?,
    timer: PageTimer,
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

    val pageOnScreen by remember(listState) { derivedStateOf { listState.pageAtCenter() } }
    val onReachedEnd by rememberUpdatedState(LocalOnReachedEnd.current)
    LaunchedEffect(listState) {
        snapshotFlow { pageOnScreen }.collect { if (it == likePageIndex) onReachedEnd() }
    }
    AutoAdvance(
        delayMillis = autoTurnMillis,
        timer = timer,
        page = pageOnScreen,
        userIsScrolling = listState.isScrollInProgress,
        lastAutoPage = likePageIndex,
    ) { listState.animateScrollToItem(pageOnScreen + 1) }

    val hearts = rememberTapHearts()
    LazyColumn(
        state = listState,
        modifier = modifier
            // Double-tapping a comic page likes the story.
            .doubleTapToLike(hearts) { at, _ ->
                listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { at.y >= it.offset && at.y < it.offset + it.size }
                    ?.let { it.index != likePageIndex } == true
            }
            .holdToPauseAutoTurn(timer),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(count = pageCount) { index ->
            // On a wide screen the strip stays a readable width, in the middle.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = MAX_STRIP_WIDTH).fillMaxWidth()) {
                    page(index) { scope.launch { listState.animateScrollToItem(likePageIndex) } }
                }
            }
        }
    }
}

/**
 * The auto-turn countdown of one page. [progress] runs 0 → 1 while [page] waits to turn; the
 * page indicator draws it as that page's bar filling up (like status updates in WhatsApp).
 */
@Stable
private class PageTimer {
    val progress = Animatable(0f)
    var page by mutableIntStateOf(-1)

    /** A finger is held on the page: the countdown stops where it is until it lifts. */
    var held by mutableStateOf(false)
}

/**
 * Runs the auto-turn countdown at 1×, whatever the phone's animation speed (Developer options, or
 * Remove animations in Accessibility), which would otherwise shorten a page's time or skip it.
 */
private object RealTime : MotionDurationScale {
    override val scaleFactor = 1f
}

/**
 * Turns the page by itself: comic [page] (any page before [lastAutoPage], the like page) waits
 * [delayMillis] while [timer] fills, then [advance] is called. Swiping the pages freezes the timer;
 * letting go, or a new page, starts it again from empty. A long press on a page, and the app not
 * being on screen, pause it: it carries on from where it was. [delayMillis] null = auto-turn off.
 */
@Composable
private fun AutoAdvance(
    delayMillis: Long?,
    timer: PageTimer,
    page: Int,
    userIsScrolling: Boolean,
    lastAutoPage: Int,
    /** The page can't turn now (torn): the countdown waits where it is. */
    paused: Boolean = false,
    advance: suspend () -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentAdvance by rememberUpdatedState(advance)
    val isPaused by rememberUpdatedState(paused)
    val scope = rememberCoroutineScope()
    LaunchedEffect(page, userIsScrolling, lastAutoPage, delayMillis) {
        // While pages move (a swipe, or the auto-turn itself) the bar stays as it is.
        if (userIsScrolling) return@LaunchedEffect
        timer.page = page
        timer.progress.snapTo(0f)
        if (delayMillis == null || page >= lastAutoPage) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow { timer.held || isPaused }.collectLatest { held ->
                if (held || timer.progress.value >= 1f) return@collectLatest // paused, or already turning
                // Back from a pause: only the time that was left.
                val remaining = ((1f - timer.progress.value) * delayMillis).toInt()
                withContext(RealTime) {
                    timer.progress.animateTo(1f, tween(durationMillis = remaining, easing = LinearEasing))
                }
                // Turn the page outside this effect: the turn itself makes userIsScrolling true,
                // which restarts this effect, and would cancel the animation after its first frame.
                // A swipe still interrupts it (the pager gives user input priority).
                scope.launch { currentAdvance() }
            }
        }
    }
}

/**
 * Tapping the left or right third of the pager turns a page back or on: [turn] gets the page to go
 * to, and quick = true for a tap while a page is still turning, so tapping fast flicks through,
 * like a book. Buttons on a page (Continue, the ribbon, the like page's) keep their own taps. A long
 * press is not a tap (it pauses the auto-turn instead).
 *
 * Watched from the outside in, since the pager stops a turning page the moment a finger touches
 * it: such a tap carries on from the page it was turning to, instead of being lost.
 */
private fun Modifier.tapToTurn(pagerState: PagerState, pageCount: Int, turn: (target: Int, quick: Boolean) -> Unit): Modifier =
    pointerInput(pagerState, pageCount) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val turning = pagerState.isScrollInProgress
            val headingTo = pagerState.targetPage
            val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitTapUp(down) }
                ?: return@awaitEachGesture
            // A button on a still page took it. On a turning page the pager itself takes every touch.
            if (up.isConsumed && !turning) return@awaitEachGesture
            val third = size.width / 3f
            val target = when {
                up.position.x < third -> headingTo - 1
                up.position.x > size.width - third -> headingTo + 1
                else -> return@awaitEachGesture
            }
            if (target in 0 until pageCount) turn(target, turning)
        }
    }

/** The finger [down] lifting without moving away: the tap's up, or null for a swipe. */
private suspend fun AwaitPointerEventScope.awaitTapUp(down: PointerInputChange): PointerInputChange? {
    while (true) {
        val change = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull { it.id == down.id } ?: return null
        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) return null
        if (!change.pressed) return change
    }
}

/**
 * A long press on a page pauses its auto-turn ([PageTimer.held]) until the finger lifts. Watches
 * the finger, without taking any of its moves or taps from the page.
 */
private fun Modifier.holdToPauseAutoTurn(timer: PageTimer): Modifier =
    pointerInput(timer) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val letGoOrMoved = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) break
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                }
            }
            if (letGoOrMoved != null) return@awaitEachGesture
            timer.held = true
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                } while (event.changes.any { it.id == down.id && it.pressed })
            } finally {
                timer.held = false
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

/** The widest the vertical strip's pages get, so a tablet's pages aren't taller than its screen many times over. */
private val MAX_STRIP_WIDTH = 640.dp

/** Above and below two pages side by side. */
private val SpreadPageMargin = 8.dp

/** Room kept under the last spread for "The End" and its Continue button. */
private val EndOfStoryHeight = 80.dp

/**
 * Comic pages side by side, meeting in the middle like an open book, each as tall as fits and
 * with its own bookmark ribbon. Under the last ones, "The End" leads on to the like page.
 */
@Composable
private fun ComicSpread(
    pages: IntRange,
    state: ReaderUiState,
    detail: StoryDetail,
    maxPageHeight: Dp,
    onContinue: () -> Unit,
    onAddBookmark: (format: StoryFormat, position: Int, offset: Int) -> Unit,
    onRemoveBookmarks: (List<Bookmark>) -> Unit,
) {
    val endsStory = detail.pages.lastIndex in pages
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (index in pages) {
                Box(Modifier.weight(1f, fill = false)) {
                    ComicPage(
                        page = detail.pages[index],
                        framed = false,
                        maxHeight = if (endsStory) maxPageHeight - EndOfStoryHeight else maxPageHeight,
                    )
                    ComicBookmarkRibbon(
                        index = index,
                        state = state,
                        onAddBookmark = onAddBookmark,
                        onRemoveBookmarks = onRemoveBookmarks,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 12.dp),
                    )
                }
            }
        }
        if (endsStory) EndOfStory(onContinue = onContinue, modifier = Modifier.padding(horizontal = 16.dp))
    }
}

/** The ribbon on comic page [index]: marks the page in the language shown, or takes its marks off. */
@Composable
private fun ComicBookmarkRibbon(
    index: Int,
    state: ReaderUiState,
    onAddBookmark: (format: StoryFormat, position: Int, offset: Int) -> Unit,
    onRemoveBookmarks: (List<Bookmark>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onPage = state.bookmarks.filter {
        it.format == StoryFormat.COMIC && it.language == state.language && it.position == index
    }
    BookmarkRibbon(
        marked = onPage.isNotEmpty(),
        onToggle = { if (onPage.isNotEmpty()) onRemoveBookmarks(onPage) else onAddBookmark(StoryFormat.COMIC, index, 0) },
        modifier = modifier,
    )
}

/** What goes on page [index]: a comic page or the like page. */
@Composable
private fun ReaderPage(
    index: Int,
    state: ReaderUiState,
    detail: StoryDetail,
    edgeToEdge: Boolean,
    onContinue: () -> Unit,
    onToggleLike: () -> Unit,
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
            onToggleLike = onToggleLike,
            onOpenNextStory = onOpenNextStory,
            onBack = onBack,
            moral = state.moral,
        )
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    /** The star: in Favorites. */
    isFavorite: Boolean,
    /** The heart: liked; [likeCount] is everyone's likes. */
    isLiked: Boolean,
    likeCount: Int,
    isDownloaded: Boolean,
    /** Saving the story for offline reading: how far, 0 … 1; null when not. */
    downloadProgress: Float? = null,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleLike: () -> Unit,
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
            DownloadIcon(isDownloaded = isDownloaded, progress = downloadProgress)
        }
        FavoriteButton(isFavorite = isFavorite, onClick = onToggleFavorite)
        LikeButton(isLiked = isLiked, likeCount = likeCount, onClick = onToggleLike)
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

/**
 * One bar per page across the screen, with the count beside it ("3 / 10"), like WhatsApp status:
 * earlier pages are full, the current page's bar fills as its auto-turn timer runs, later ones are
 * empty. With auto-turn off (Settings), or on the like page, the current bar is simply full.
 *
 * The current page's bar is wider and a little taller than the others, so it's easy to see; on a
 * page turn it slides over to the new page with a small springy pop (see [pageBarLayout]).
 */
@Composable
private fun PageIndicator(
    currentPage: Int,
    pageCount: Int,
    timer: PageTimer,
    autoTurn: Boolean,
    lastAutoPage: Int,
) {
    val fillColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outline
    // Where the big bar is: on the current page, springing over to the next one as the page turns.
    val focus = animateFloatAsState(
        targetValue = currentPage.toFloat(),
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "pageBarFocus",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(
            Modifier
                .weight(1f)
                .height(PageBarTallHeight),
        ) {
            if (pageCount <= 0) return@Canvas
            val gap = (if (pageCount > 30) 1.dp else 3.dp).toPx()
            val low = PageBarHeight.toPx()
            val tall = PageBarTallHeight.toPx()
            // The timer and the big bar's place are read here, while drawing: only the bars redraw.
            val timerFill = pageBarFill(currentPage, currentPage, timer.page, timer.progress.value, autoTurn, lastAutoPage)
            val bars = pageBarLayout(pageCount, focus.value, size.width, gap)
            for (i in 0 until pageCount) {
                val (left, width, near) = bars[i]
                val height = low + (tall - low) * near
                val top = (size.height - height) / 2f
                val corner = CornerRadius(height / 2f)
                drawRoundRect(trackColor, topLeft = Offset(left, top), size = Size(width, height), cornerRadius = corner)
                val fill = if (i == currentPage) timerFill else pageBarFill(i, currentPage, timer.page, 0f, autoTurn, lastAutoPage)
                if (fill > 0f) {
                    drawRoundRect(fillColor, topLeft = Offset(left, top), size = Size(width * fill, height), cornerRadius = corner)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.page_of, currentPage + 1, pageCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One page's bar: where it starts and how wide it is (px), and how near the big bar is to it (0 … 1). */
internal data class PageBar(val left: Float, val width: Float, val near: Float)

/**
 * The page bars across [totalWidth] px, [gap] px apart. The bar at [focus] (the current page; in
 * between two while the big bar slides over) is [PAGE_BAR_FOCUS_EXTRA] wider than the rest; between
 * two pages the extra is shared by both, so the bars always fill the width exactly.
 */
internal fun pageBarLayout(pageCount: Int, focus: Float, totalWidth: Float, gap: Float): List<PageBar> {
    if (pageCount <= 0) return emptyList()
    val near = List(pageCount) { i -> (1f - kotlin.math.abs(i - focus)).coerceIn(0f, 1f) }
    val weights = near.map { 1f + PAGE_BAR_FOCUS_EXTRA * it }
    val unit = (totalWidth - gap * (pageCount - 1)).coerceAtLeast(0f) / weights.sum()
    var left = 0f
    return List(pageCount) { i ->
        val width = (unit * weights[i]).coerceAtLeast(1f)
        PageBar(left, width, near[i]).also { left += width + gap }
    }
}

/** The current page's bar is this much wider than the others (1.4 = 2.4 times as wide). */
internal const val PAGE_BAR_FOCUS_EXTRA = 1.4f

/** The page bars' height, and the current page's (taller). */
private val PageBarHeight = 4.dp
private val PageBarTallHeight = 6.dp

/**
 * How full page [index]'s bar is, 0..1: earlier pages full, later ones empty; the current page
 * follows its auto-turn timer, but only once the timer belongs to it ([timerPage]); mid-turn it
 * still belongs to the page being left, so the new page's bar starts empty instead of flashing full.
 */
internal fun pageBarFill(
    index: Int,
    currentPage: Int,
    timerPage: Int,
    timerProgress: Float,
    autoTurn: Boolean,
    lastAutoPage: Int,
): Float = when {
    index < currentPage -> 1f
    index > currentPage -> 0f
    !autoTurn || currentPage >= lastAutoPage -> 1f
    timerPage == currentPage -> timerProgress.coerceIn(0f, 1f)
    else -> 0f
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

