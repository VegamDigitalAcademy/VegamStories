package com.vegamstories.app.presentation.bookshelf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vegamstories.app.R
import com.vegamstories.app.domain.model.SeriesShelf
import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.presentation.components.BackTopBar
import com.vegamstories.app.presentation.components.EmptyState
import com.vegamstories.app.presentation.components.LoadingBox
import com.vegamstories.app.presentation.preview.PreviewData
import com.vegamstories.app.presentation.preview.PreviewTheme
import com.vegamstories.app.presentation.preview.ScreenPreviews

/** Wood of the shelves: the plank the books stand on, and the darker back of the case behind them. */
private val PlankLight = Color(0xFFA0703F)
private val PlankDark = Color(0xFF6E4524)
private val BackTop = Color(0xFF3B2716)
private val BackBottom = Color(0xFF55391F)

/**
 * Books newly finished: welcomed onto the shelf once each while the app runs, if finished within
 * [NEW_BOOK_MILLIS].
 */
private object NewOnShelf {
    private val welcomed = mutableSetOf<String>()

    /** True the first time it's asked about [storyId]. */
    fun welcome(storyId: String): Boolean = welcomed.add(storyId)
}

private const val NEW_BOOK_MILLIS = 24 * 60 * 60 * 1000L
private const val NEW_BOOK_DELAY_MILLIS = 350L
private val NEW_BOOK_DROP = 90.dp
private val NEW_BOOK_GLOW = 12.dp
private const val GLOW_RINGS = 5

/** Gold of the spines' bands and of the "series complete" sticker. */
private val Gold = Color(0xFFE2B865)

/** The books' blue cloth (the reader's book cover, book_cover), and the title printed on it. */
private val BookCloth = Color(0xFF25245A)
private val SpineText = Color(0xFFF3E9D2)

/**
 * The reader's bookshelf: every story read to the end, standing as a book on a wooden shelf, one
 * shelf for each series. A series read in full earns a sticker; the others show how far along
 * they are. Tapping a book opens the story.
 */
@Composable
fun BookshelfScreen(
    onBack: () -> Unit,
    onStoryClick: (String) -> Unit,
    viewModel: BookshelfViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BookshelfContent(state = state, onBack = onBack, onStoryClick = onStoryClick)
}

@Composable
fun BookshelfContent(state: BookshelfUiState, onBack: () -> Unit, onStoryClick: (String) -> Unit) {
    Scaffold(
        topBar = { BackTopBar(title = stringResource(R.string.bookshelf_title), onBack = onBack) },
        containerColor = Color.Transparent, // AppBackground shows through
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        val modifier = Modifier
            .padding(padding)
            .fillMaxSize()
        when {
            state.isLoading -> LoadingBox(modifier)
            state.shelves.isEmpty() -> EmptyState(
                icon = R.drawable.ic_bookshelf,
                title = stringResource(R.string.bookshelf_empty_title),
                message = stringResource(R.string.bookshelf_empty_message),
                modifier = modifier,
            )
            else -> {
                // The book most lately finished, if that was today and it hasn't been welcomed yet.
                val newest = remember(state.shelves) {
                    state.shelves.flatMap { it.finished }
                        .maxByOrNull { it.progress?.updatedAt ?: 0L }
                        ?.takeIf { story ->
                            val finishedAt = story.progress?.updatedAt ?: 0L
                            System.currentTimeMillis() - finishedAt < NEW_BOOK_MILLIS && NewOnShelf.welcome(story.id)
                        }
                        ?.id
                }
                LazyColumn(
                modifier = modifier,
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                item(key = "summary") {
                    Text(
                        text = stringResource(R.string.bookshelf_summary, state.finishedCount, state.completeSeries),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
                items(state.shelves, key = { it.categoryId }) { shelf ->
                    Shelf(shelf = shelf, newestId = newest, onStoryClick = onStoryClick)
                }
            }
            }
        }
    }
}

/** One series: its name and how far along it is, then its finished books standing on a plank. */
@Composable
private fun Shelf(shelf: SeriesShelf, newestId: String?, onStoryClick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = shelf.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            if (shelf.isComplete) {
                CompleteSticker()
            } else {
                Text(
                    text = stringResource(R.string.bookshelf_progress, shelf.readCount, shelf.total),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                .background(Brush.verticalGradient(listOf(BackTop, BackBottom))),
        ) {
            LazyRow(
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                items(shelf.finished, key = { it.id }) { story ->
                    BookSpine(story = story, isNew = story.id == newestId, onClick = { onStoryClick(story.id) })
                }
            }
        }
        Plank()
    }
}

/** The shelf board: lit along its front edge, casting a shadow below. */
@Composable
private fun Plank() {
    Box(
        Modifier
            .padding(horizontal = 6.dp)
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Brush.verticalGradient(listOf(PlankLight, PlankDark)))
            .drawBehind {
                drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, 1.dp.toPx()), Offset(size.width, 1.dp.toPx()), 1.dp.toPx())
            },
    )
    Box(
        Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .height(6.dp)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent))),
    )
}

/**
 * A finished story as a book on the shelf: bound in the same blue cloth as the reader's cover,
 * rounded by light and shade, gold bands top and bottom, the title written up the spine and the
 * episode number at its foot. Books differ a little in shade and height, as on a real shelf.
 */
@Composable
private fun BookSpine(story: Story, isNew: Boolean, onClick: () -> Unit) {
    val base = lerp(BookCloth, Color.Black, (story.episodeNumber % 3) * 0.08f)
    val height = (128 + (story.id.hashCode() and Int.MAX_VALUE) % 4 * 8).dp
    // Newly finished: it drops onto the shelf from above, leaning, lands upright with a little
    // bounce, then glows gold twice. (Dropped, not slid, so it never passes behind its neighbours.)
    val arrived = remember { Animatable(if (isNew) 0f else 1f) }
    val glow = remember { Animatable(0f) }
    LaunchedEffect(isNew) {
        if (!isNew) return@LaunchedEffect
        delay(NEW_BOOK_DELAY_MILLIS)
        arrived.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
        repeat(2) {
            glow.animateTo(1f, tween(durationMillis = 380))
            glow.animateTo(0f, tween(durationMillis = 520))
        }
    }
    Box(
        modifier = Modifier
            .graphicsLayer {
                translationY = -(1f - arrived.value) * NEW_BOOK_DROP.toPx()
                rotationZ = (1f - arrived.value) * -12f
                transformOrigin = TransformOrigin(0.5f, 1f)
                alpha = arrived.value.coerceIn(0f, 1f)
            }
            // Over its neighbours while it glows, so the glow spills onto them.
            .zIndex(if (isNew) 1f else 0f)
            .drawBehind {
                if (glow.value > 0f) {
                    // A soft halo: rings ever wider and fainter, so it fades out from the book.
                    val reach = NEW_BOOK_GLOW.toPx() * glow.value
                    for (ring in 1..GLOW_RINGS) {
                        val spread = reach * ring / GLOW_RINGS
                        drawRoundRect(
                            color = Gold.copy(alpha = 0.3f * glow.value * (1f - (ring - 1f) / GLOW_RINGS)),
                            topLeft = Offset(-spread, -spread),
                            size = Size(size.width + spread * 2, size.height + spread * 2),
                            cornerRadius = CornerRadius(3.dp.toPx() + spread),
                        )
                    }
                }
            }
            .size(width = 40.dp, height = height)
            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
            .background(
                Brush.horizontalGradient(
                    0f to lerp(base, Color.Black, 0.35f),
                    0.35f to lerp(base, Color.White, 0.12f),
                    1f to lerp(base, Color.Black, 0.25f),
                ),
            )
            .drawBehind {
                val band = 1.5.dp.toPx()
                for (y in listOf(10.dp.toPx(), 14.dp.toPx(), size.height - 26.dp.toPx(), size.height - 30.dp.toPx())) {
                    drawLine(Gold.copy(alpha = 0.85f), Offset(0f, y), Offset(size.width, y), band)
                }
            }
            .clickable(role = Role.Button, onClickLabel = story.title, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = story.title,
            color = SpineText,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            textAlign = TextAlign.Center,
            maxLines = 2, // a long title runs on to a second line up the spine
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 18.dp, bottom = 34.dp)
                .sideways(),
        )
        Text(
            text = story.episodeNumber.toString(),
            color = Gold,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
        )
    }
}

/** The gold sticker of a series read to the end, stuck on a little askew. */
@Composable
private fun CompleteSticker() {
    Row(
        modifier = Modifier
            .rotate(-4f)
            .clip(RoundedCornerShape(50))
            .background(Gold)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(PlankDark),
            contentAlignment = Alignment.Center,
        ) {
            Text("★", color = Gold, fontSize = 10.sp, lineHeight = 10.sp)
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.bookshelf_complete),
            color = PlankDark,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Lays the text out along the spine, reading from bottom to top, as on most books' spines. */
private fun Modifier.sideways(): Modifier = this
    .rotate(-90f)
    .layout { measurable, constraints ->
        val placeable = measurable.measure(
            constraints.copy(
                minWidth = 0,
                maxWidth = constraints.maxHeight,
                minHeight = 0,
                maxHeight = constraints.maxWidth,
            ),
        )
        layout(placeable.height, placeable.width) {
            placeable.place(
                x = -(placeable.width - placeable.height) / 2,
                y = -(placeable.height - placeable.width) / 2,
            )
        }
    }

// ---------------- Previews ----------------

@ScreenPreviews
@Composable
private fun BookshelfPreview() = PreviewTheme {
    val read = (0 until 6).map { PreviewData.story(index = it) }
    BookshelfContent(
        state = BookshelfUiState(
            isLoading = false,
            shelves = read.groupBy { it.categoryId }.map { (id, stories) ->
                SeriesShelf(id, stories.first().categoryName, stories, total = if (id == read.first().categoryId) stories.size else 8)
            },
        ),
        onBack = {}, onStoryClick = {},
    )
}

@ScreenPreviews
@Composable
private fun BookshelfEmptyPreview() = PreviewTheme {
    BookshelfContent(state = BookshelfUiState(isLoading = false), onBack = {}, onStoryClick = {})
}
