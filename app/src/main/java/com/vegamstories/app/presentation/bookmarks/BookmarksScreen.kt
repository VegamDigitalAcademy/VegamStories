package com.vegamstories.app.presentation.bookmarks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vegamstories.app.R
import com.vegamstories.app.domain.model.Bookmark
import com.vegamstories.app.domain.model.BookmarkItem
import com.vegamstories.app.domain.model.StoryFormat
import com.vegamstories.app.presentation.components.BackTopBar
import com.vegamstories.app.presentation.components.EmptyState
import com.vegamstories.app.presentation.components.LoadingBox
import com.vegamstories.app.presentation.components.StoryCover
import com.vegamstories.app.presentation.components.StoryImage
import com.vegamstories.app.presentation.preview.PreviewData
import com.vegamstories.app.presentation.preview.PreviewTheme
import com.vegamstories.app.presentation.preview.ScreenPreviews

/** Pages marked with the ribbon in the reader, newest first. Tapping one opens the story there. */
@Composable
fun BookmarksScreen(
    onBack: () -> Unit,
    onOpenBookmark: (Bookmark) -> Unit,
    viewModel: BookmarksViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BookmarksContent(
        state = state,
        onBack = onBack,
        onOpenBookmark = onOpenBookmark,
        onRemove = viewModel::onRemove,
    )
}

@Composable
fun BookmarksContent(
    state: BookmarksUiState,
    onBack: () -> Unit,
    onOpenBookmark: (Bookmark) -> Unit,
    onRemove: (Bookmark) -> Unit,
) {
    Scaffold(
        topBar = { BackTopBar(title = stringResource(R.string.bookmarks_title), onBack = onBack) },
        containerColor = Color.Transparent, // AppBackground shows through
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        val modifier = Modifier
            .padding(padding)
            .fillMaxSize()
        when {
            state.isLoading -> LoadingBox(modifier)
            state.items.isEmpty() -> EmptyState(
                icon = R.drawable.ic_bookmark,
                title = stringResource(R.string.bookmarks_empty_title),
                message = stringResource(R.string.bookmarks_empty_message),
                modifier = modifier,
            )
            else -> LazyColumn(modifier) {
                items(state.items, key = { it.bookmark.key }) { item ->
                    BookmarkRow(
                        item = item,
                        onClick = { onOpenBookmark(item.bookmark) },
                        onRemove = { onRemove(item.bookmark) },
                    )
                }
            }
        }
    }
}

/** One per page: a story can have several. */
private val Bookmark.key: String get() = "$storyId|$language|$format|$position|$offset"

/** The story's picture (the marked page itself, for a comic), its title, and where the mark is. */
@Composable
private fun BookmarkRow(item: BookmarkItem, onClick: () -> Unit, onRemove: () -> Unit) {
    val story = item.story
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val thumbnail = Modifier
            .size(width = 56.dp, height = 72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
        if (item.pageImageUrl != null) {
            StoryImage(
                model = item.pageImageUrl,
                contentDescription = null,
                modifier = thumbnail,
                fallback = { StoryCover(story = story, contentDescription = null, modifier = Modifier.matchParentSize()) },
            )
        } else {
            StoryCover(story = story, contentDescription = null, modifier = thumbnail)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = story.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.episode_category, story.episodeNumber, story.categoryName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    painter = painterResource(R.drawable.ic_bookmark),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = when (item.bookmark.format) {
                        StoryFormat.TEXT -> item.snippet?.trim()?.let { stringResource(R.string.bookmark_text_place, it) }
                            ?: stringResource(R.string.format_text)
                        else -> stringResource(R.string.bookmark_comic_page, item.bookmark.position + 1)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.bookmark_remove))
        }
    }
}

// ---------------- Previews ----------------

@ScreenPreviews
@Composable
private fun BookmarksPreview() = PreviewTheme {
    val comic = PreviewData.story(0)
    val text = PreviewData.story(1)
    BookmarksContent(
        state = BookmarksUiState(
            isLoading = false,
            items = listOf(
                BookmarkItem(Bookmark(comic.id, "te", StoryFormat.COMIC, position = 2), comic),
                BookmarkItem(
                    Bookmark(text.id, "te", StoryFormat.TEXT, position = 3, offset = 40),
                    text,
                    snippet = "ఒక రోజు అతని గొడ్డలి నదిలో పడిపోయింది. అతను నది ఒడ్డున కూర్చుని ఏడవడం మొదలుపెట్టాడు…",
                ),
            ),
        ),
        onBack = {}, onOpenBookmark = {}, onRemove = {},
    )
}

@ScreenPreviews
@Composable
private fun BookmarksEmptyPreview() = PreviewTheme {
    BookmarksContent(state = BookmarksUiState(isLoading = false), onBack = {}, onOpenBookmark = {}, onRemove = {})
}
