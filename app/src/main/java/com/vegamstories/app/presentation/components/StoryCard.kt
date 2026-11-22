package com.vegamstories.app.presentation.components

import androidx.compose.foundation.background
import com.vegamstories.app.core.util.toCompactCount
import com.vegamstories.app.ui.theme.VegamRed
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.zIndex
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vegamstories.app.R
import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.domain.model.StoryFormat
import com.vegamstories.app.presentation.navigation.CardSpot
import com.vegamstories.app.presentation.navigation.LocalStoryOpener
import com.vegamstories.app.presentation.preview.ComponentPreviews
import com.vegamstories.app.presentation.preview.PreviewData
import com.vegamstories.app.presentation.preview.PreviewTheme
import com.vegamstories.app.ui.theme.ReadGreen

/**
 * Grid card used on Home, Favorites and Search: the cover, with the likes on its bottom left (the
 * heart here only shows them; liking happens in the reader), a green tick in its top right once
 * the story's been read, and the category under it ([showCategory]). The cover carries the title.
 *
 * Tapped, it opens the story. Held, it leans towards the finger and lifts with a shadow, and its
 * cover swings open a little on the story's first page, a peek; let go, it closes (see [CoverPress]).
 */
@Composable
fun StoryCard(
    story: Story,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** The category under the cover. Not on a category's own tab, where every card is in it. */
    showCategory: Boolean = true,
) {
    // Tapped, its picture grows into the reader's cover (StoryOpener): it needs to know where it is.
    val opener = LocalStoryOpener.current
    val pictureBounds = remember { arrayOfNulls<Rect>(1) }
    DisposableEffect(story.id) { onDispose { opener.cardGone(story.id) } }
    val press = rememberCoverPress()
    // Lifted, it's drawn over its neighbours, so its shadow and lean aren't cut off by them.
    val lifted by remember { derivedStateOf { press.lift.value > 0.01f || press.peek.value > 0.01f } }
    Column(
        modifier = modifier
            .zIndex(if (lifted) 1f else 0f)
            .coverPress(press) { width -> width * COVER_ASPECT }
            .combinedClickable(
                onLongClickLabel = stringResource(R.string.cd_peek_story),
                onLongClick = press::open,
                onClick = {
                    opener.tapped(story, pictureBounds[0])
                    onClick()
                },
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / COVER_ASPECT)
                .onGloballyPositioned {
                    pictureBounds[0] = it.boundsInRoot()
                    opener.cardPlaced(story.id, CardSpot(it.boundsInRoot(), story.coverImageUrl))
                }
                .graphicsLayer {
                    // While its picture is growing over the app, or shrinking back into it, it has left the card.
                    val away = opener.opening?.story?.id == story.id || opener.closing?.storyId == story.id
                    alpha = if (away) 0f else 1f
                    leanAndLift(press)
                },
        ) {
            // Under the cover, from the moment it's held: the first page (loading while the cover's shut).
            if (press.touched) FirstPage(story, press)
            // The cover and its badges, swinging open from the left like a book's.
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { coverOpening(press) }
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .coverShade(press),
            ) {
            StoryCover(
                story = story,
                contentDescription = story.title,
                modifier = Modifier.matchParentSize(),
            )
            /*ImageBadge(
                text = stringResource(R.string.episode_short, story.episodeNumber),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            )*/
            // Top right: saved for offline reading (left of the read tick, when there is one).
            if (story.isDownloaded) {
                Icon(
                    painter = painterResource(R.drawable.ic_download_done),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = if (story.isRead) ReadTickSize else 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(2.dp)
                        .size(14.dp),
                )
            }
            // Bottom left: its likes.
            CoverLikes(
                isLiked = story.isLiked,
                likeCount = story.likeCount,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
            )
            FormatBadge(
                formats = story.formats,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
            )
            if (story.isInProgress) {
                LinearProgressIndicator(
                    progress = { story.progress?.fraction ?: 0f },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.5f),
                    drawStopIndicator = {},
                )
            }
            }
            // Read: the green tick right on the card's top right corner, half outside it. Here, not on
            // the cover, which clips what's outside it (and swings open in a peek).
            if (story.isRead) {
                ReadTick(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = ReadTickSize / 5, y = -ReadTickSize / 5),
                )
            }
        }
        // The title's on the cover itself (and read out from it), so it isn't repeated here.
        /*Text(
            text = story.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )*/
        if (showCategory) {
            Text(
                text = story.categoryName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Read: a white tick in a green circle, ringed in white so it shows on any cover. */
@Composable
private fun ReadTick(modifier: Modifier = Modifier) {
    val read = stringResource(R.string.read_badge)
    Box(
        modifier = modifier
            .size(ReadTickSize)
            .shadow(3.dp, CircleShape)
            .background(Color.White, CircleShape)
            .padding(1.5.dp)
            .background(ReadGreen, CircleShape)
            .semantics { contentDescription = read },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(15.dp),
        )
    }
}

/** The read tick's size: its middle sits on the card's corner, so half of it is outside. */
private val ReadTickSize = 24.dp

/** The likes on a cover: the heart (red once liked) and the count, on a dark pill so they show on any picture. */
@Composable
private fun CoverLikes(isLiked: Boolean, likeCount: Int, modifier: Modifier = Modifier) {
    val description = pluralStringResource(R.plurals.like_count, likeCount, likeCount.toCompactCount())
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(likeIcon(isLiked)),
            contentDescription = null,
            tint = if (isLiked) VegamRed else Color.White,
            modifier = Modifier.size(13.dp),
        )
        if (likeCount > 0) {
            Spacer(Modifier.width(3.dp))
            Text(
                text = likeCount.toCompactCount(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}

/**
 * Small icons for the formats a story comes in (comic, text, video). Read out as one label,
 * e.g. "Comic, Text, Video". Hidden for a comic-only story, the common case, to keep covers clean.
 */
@Composable
private fun FormatBadge(formats: Set<StoryFormat>, modifier: Modifier = Modifier) {
    if (formats.isEmpty() || formats == setOf(StoryFormat.COMIC)) return
    val label = formats.map { stringResource(it.labelRes) }.joinToString(", ")
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .semantics(mergeDescendants = true) { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        formats.forEach { format ->
            Icon(
                painter = painterResource(format.iconRes),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/** A cover's height for its width (3 : 4). */
private const val COVER_ASPECT = 4f / 3f

private val StoryFormat.iconRes: Int
    get() = when (this) {
        StoryFormat.COMIC -> R.drawable.ic_format_comic
        StoryFormat.TEXT -> R.drawable.ic_format_text
        StoryFormat.VIDEO -> R.drawable.ic_play
    }

private val StoryFormat.labelRes: Int
    get() = when (this) {
        StoryFormat.COMIC -> R.string.format_comic
        StoryFormat.TEXT -> R.string.format_text
        StoryFormat.VIDEO -> R.string.format_video
    }

/** List row used by History and Downloads: thumbnail, title, meta line, progress bar. */
@Composable
fun StoryListRow(
    story: Story,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showProgress: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StoryCover(
            story = story,
            contentDescription = story.title,
            modifier = Modifier
                .size(width = 56.dp, height = 72.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
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
            val progress = story.progress
            if (showProgress && progress != null) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (progress.isCompleted) ReadGreen else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    drawStopIndicator = {},
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (progress.isCompleted) {
                        stringResource(R.string.completed)
                    } else {
                        stringResource(R.string.pages_progress, progress.lastPageIndex + 1, progress.totalPages)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (progress.isCompleted) ReadGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) trailing()
    }
}

// ---------------- Previews ----------------

@ComponentPreviews
@Composable
private fun StoryCardPreview() = PreviewTheme {
    Row(Modifier.padding(16.dp)) {
        // Read + favorite
        StoryCard(PreviewData.stories[0], onClick = {}, modifier = Modifier.width(160.dp))
        Spacer(Modifier.width(14.dp))
        // In progress
        StoryCard(PreviewData.stories[1], onClick = {}, modifier = Modifier.width(160.dp))
    }
}

@ComponentPreviews
@Composable
private fun StoryListRowPreview() = PreviewTheme {
    Column {
        StoryListRow(PreviewData.historyStories[0], onClick = {})
        StoryListRow(PreviewData.historyStories[1], onClick = {})
    }
}
