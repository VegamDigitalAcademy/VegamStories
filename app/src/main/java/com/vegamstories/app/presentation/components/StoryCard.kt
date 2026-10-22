package com.vegamstories.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vegamstories.app.R
import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.presentation.preview.ComponentPreviews
import com.vegamstories.app.presentation.preview.PreviewData
import com.vegamstories.app.presentation.preview.PreviewTheme
import com.vegamstories.app.ui.theme.ReadGreen

/**
 * Grid card used on Home, Favorites and Search: cover, episode badge, title, category, and the
 * like count. The heart here only shows likes; liking happens in the reader.
 */
@Composable
fun StoryCard(
    story: Story,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            StoryImage(
                model = story.coverImageUrl,
                contentDescription = story.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            ImageBadge(
                text = stringResource(R.string.episode_short, story.episodeNumber),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            )
            if (story.isRead) {
                ImageBadge(
                    text = stringResource(R.string.read_badge),
                    icon = R.drawable.ic_check_circle,
                    background = ReadGreen.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                )
            }
            if (story.isDownloaded) {
                Icon(
                    painter = painterResource(R.drawable.ic_download_done),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(2.dp)
                        .size(14.dp),
                )
            }
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
        Row(verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 6.dp),
            ) {
                Text(
                    text = story.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = story.categoryName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LikeCount(
                isLiked = story.isFavorite,
                likeCount = story.likeCount,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp),
            )
        }
    }
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
        StoryImage(
            model = story.coverImageUrl,
            contentDescription = story.title,
            contentScale = ContentScale.Crop,
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
