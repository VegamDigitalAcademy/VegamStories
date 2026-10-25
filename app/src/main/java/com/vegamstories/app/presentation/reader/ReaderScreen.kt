package com.vegamstories.app.presentation.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
        onAddBookmark = viewModel::onAddBookmark,
        onRemoveBookmarks = viewModel::onRemoveBookmarks,
        onTurnHintShown = viewModel::onPageTurnHintShown,
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
    onAddBookmark: (format: StoryFormat, position: Int, offset: Int) -> Unit = { _, _, _ -> },
    onRemoveBookmarks: (List<Bookmark>) -> Unit = {},
    onTurnHintShown: () -> Unit = {},
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
                    onAddBookmark = onAddBookmark,
                    onRemoveBookmarks = onRemoveBookmarks,
                    onTurnHintShown = onTurnHintShown,
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
    onAddBookmark: (format: StoryFormat, position: Int, offset: Int) -> Unit,
    onRemoveBookmarks: (List<Bookmark>) -> Unit,
    onTurnHintShown: () -> Unit,
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
    // The auto-turn countdown: run by the reader below, shown filling the page's bar in PageIndicator.
    val pageTimer = remember { PageTimer() }

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
                moral = state.moral,
            )
        }
        val text = state.text
        when (state.format) {
            StoryFormat.TEXT if text != null -> {
                TextPagesReader(
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
                        val onPage = state.bookmarks.filter {
                            it.format == StoryFormat.COMIC && it.language == state.language && it.position == index
                        }
                        BookmarkRibbon(
                            marked = onPage.isNotEmpty(),
                            onToggle = {
                                if (onPage.isNotEmpty()) onRemoveBookmarks(onPage) else onAddBookmark(StoryFormat.COMIC, index, 0)
                            },
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
        PageIndicator(
            currentPage = currentPage,
            pageCount = state.totalPagerPages,
            timer = pageTimer,
            autoTurn = state.settings.autoTurnMillis != null,
            lastAutoPage = state.likePageIndex,
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
    pageMargin: Dp = 8.dp,
    /** Lift the open page's corner once and let it fall back, to show pages turn; then [onTurnHintShown]. */
    showTurnHint: Boolean = false,
    onTurnHintShown: () -> Unit = {},
    /** Drawn over page [index], turning with it: the bookmark ribbon. */
    pageOverlay: @Composable BoxScope.(index: Int) -> Unit = {},
) {
    val pagerState = rememberPagerState(initialPage = startPage) { pageCount }
    val scope = rememberCoroutineScope()
    val currentOnPageSettled by rememberUpdatedState(onPageSettled)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { currentOnPageSettled(it) }
    }

    // Auto-advance (Settings → Reading): each comic page turns to the next after autoTurnMillis.
    // Swiping restarts the wait; it stops at the like page.
    AutoAdvance(
        delayMillis = autoTurnMillis,
        timer = timer,
        page = pagerState.settledPage,
        userIsScrolling = pagerState.isScrollInProgress,
        lastAutoPage = likePageIndex,
    ) { pagerState.animateScrollToPage(pagerState.settledPage + 1, animationSpec = pageTurnSpec) }

    // Which way a turning page's fold leans: after the finger, else the app's own lean.
    val lean = remember { PageLean() }
    LaunchedEffect(pagerState, lean) { lean.follow(pagerState) }

    // The first story ever opened: the corner lifts a little and falls back. Decided once, so saving
    // that it was shown doesn't stop it halfway.
    val turnHint = remember { showTurnHint }
    if (turnHint) {
        val currentOnShown by rememberUpdatedState(onTurnHintShown)
        LaunchedEffect(pagerState) {
            delay(TURN_HINT_DELAY_MILLIS)
            // Only with a page to turn to, and not while the reader is turning one already.
            if (pagerState.isScrollInProgress || pagerState.currentPage >= pageCount - 1) return@LaunchedEffect
            currentOnShown()
            val lift = pagerState.layoutInfo.pageSize * TURN_HINT_LIFT
            pagerState.animateScrollBy(lift, tween(durationMillis = 650, easing = EaseOut))
            delay(150)
            pagerState.animateScrollBy(-lift, tween(durationMillis = 550, easing = EaseInOut))
        }
    }

    HorizontalPager(
        state = pagerState,
        // The curl's shadows stay inside the reader area.
        modifier = modifier
            .clipToBounds()
            .leanWithFinger(lean, pagerState)
            // Tapping the left or right third of a page turns back or on, like an e-reader. Buttons on
            // a page (Continue, the ribbon, the like page's) take their own taps first.
            .pointerInput(pagerState, pageCount) {
                detectTapGestures { tap ->
                    val third = size.width / 3f
                    val target = when {
                        tap.x < third -> pagerState.targetPage - 1
                        tap.x > size.width - third -> pagerState.targetPage + 1
                        else -> return@detectTapGestures
                    }
                    if (target in 0 until pageCount) {
                        scope.launch { pagerState.animateScrollToPage(target, animationSpec = pageTurnSpec) }
                    }
                }
            },
        beyondViewportPageCount = 1,
        flingBehavior = PagerDefaults.flingBehavior(state = pagerState, snapAnimationSpec = pageSettleSpec),
    ) { index ->
        val isComic = index < comicPageCount
        // Which page lies on top changes only a few times per turn: recompose just then.
        val layer by remember(pagerState, index) {
            derivedStateOf { turnLayer(turnPosition(index, pagerState.currentPage, pagerState.currentPageOffsetFraction)) }
        }
        BoxWithConstraints(
            Modifier
                .zIndex(layer)
                .fillMaxSize()
                .bookPageTurn(pagerState, index, lean, paper)
                // Opaque, so the page underneath shows only where the turning page has lifted.
                .background(pageBackground),
        ) {
            val viewportHeight = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    // At least one screen tall, so a shorter page can be centred in it.
                    .heightIn(min = viewportHeight)
                    .padding(horizontal = if (isComic) 0.dp else 16.dp, vertical = pageMargin),
                verticalArrangement = if (isComic) Arrangement.Center else Arrangement.Top,
            ) {
                page(index) { scope.launch { pagerState.animateScrollToPage(likePageIndex, animationSpec = pageTurnSpec) } }
            }
            pageOverlay(index)
        }
    }
}

/** The corner-lift hint: how long after the reader opens, and how far (a share of the page's width). */
private const val TURN_HINT_DELAY_MILLIS = 1_200L
private const val TURN_HINT_LIFT = 0.18f

/** How long each page of text waits before auto-turn turns it (comic pages: Settings → Reading). */
private const val TEXT_AUTO_TURN_MILLIS = 45_000L

/**
 * The Read tab as a book: the text split into pages that fit the screen (see [rememberTextBook]),
 * turned like the comic pages (curl, auto-turn, the bars below), then the like page. Reports the
 * last paragraph on the page as progress; the like page finishes the story.
 */
@Composable
private fun TextPagesReader(
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
    AutoAdvance(
        delayMillis = autoTurnMillis,
        timer = timer,
        page = pageOnScreen,
        userIsScrolling = listState.isScrollInProgress,
        lastAutoPage = likePageIndex,
    ) { listState.animateScrollToItem(pageOnScreen + 1) }

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
 * The auto-turn countdown of one page. [progress] runs 0 → 1 while [page] waits to turn; the
 * page indicator draws it as that page's bar filling up (like status updates in WhatsApp).
 */
@Stable
private class PageTimer {
    val progress = Animatable(0f)
    var page by mutableIntStateOf(-1)
}

/**
 * Turns the page by itself: comic [page] (any page before [lastAutoPage], the like page) waits
 * [delayMillis] while [timer] fills, then [advance] is called. Holding or swiping the pages freezes
 * the timer; letting go, or a new page, starts it again from empty. While the app isn't on screen
 * it pauses, and carries on from where it was on return. [delayMillis] null = auto-turn off.
 */
@Composable
private fun AutoAdvance(
    delayMillis: Long?,
    timer: PageTimer,
    page: Int,
    userIsScrolling: Boolean,
    lastAutoPage: Int,
    advance: suspend () -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentAdvance by rememberUpdatedState(advance)
    val scope = rememberCoroutineScope()
    LaunchedEffect(page, userIsScrolling, lastAutoPage, delayMillis) {
        // While pages move (a swipe, or the auto-turn itself) the bar stays as it is.
        if (userIsScrolling) return@LaunchedEffect
        timer.page = page
        timer.progress.snapTo(0f)
        if (delayMillis == null || page >= lastAutoPage) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (timer.progress.value >= 1f) return@repeatOnLifecycle // already turning
            // Back from the background: only the time that was left.
            val remaining = ((1f - timer.progress.value) * delayMillis).toInt()
            timer.progress.animateTo(1f, tween(durationMillis = remaining, easing = LinearEasing))
            // Turn the page outside this effect: the turn itself makes userIsScrolling true,
            // which restarts this effect, and would cancel the animation after its first frame.
            // A swipe still interrupts it (the pager gives user input priority).
            scope.launch { currentAdvance() }
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
            moral = state.moral,
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

/**
 * One bar per page across the screen, with the count beside it ("3 / 10"), like WhatsApp status:
 * earlier pages are full, the current page's bar fills as its auto-turn timer runs, later ones are
 * empty. With auto-turn off (Settings), or on the like page, the current bar is simply full.
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(
            Modifier
                .weight(1f)
                .height(4.dp),
        ) {
            if (pageCount <= 0) return@Canvas
            val gap = (if (pageCount > 30) 1.dp else 3.dp).toPx()
            val barWidth = ((size.width - gap * (pageCount - 1)) / pageCount).coerceAtLeast(1f)
            val corner = CornerRadius(size.height / 2)
            // The timer is read here, while drawing: only this bar redraws each frame.
            val timerFill = pageBarFill(currentPage, currentPage, timer.page, timer.progress.value, autoTurn, lastAutoPage)
            for (i in 0 until pageCount) {
                val left = i * (barWidth + gap)
                drawRoundRect(trackColor, topLeft = Offset(left, 0f), size = Size(barWidth, size.height), cornerRadius = corner)
                val fill = if (i == currentPage) timerFill else pageBarFill(i, currentPage, timer.page, 0f, autoTurn, lastAutoPage)
                if (fill > 0f) {
                    drawRoundRect(fillColor, topLeft = Offset(left, 0f), size = Size(barWidth * fill, size.height), cornerRadius = corner)
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

