package com.vegamstories.app.presentation.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.vegamstories.app.R
import com.vegamstories.app.core.util.toCompactCount
import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.presentation.components.RollingCount
import com.vegamstories.app.presentation.components.StoryCover
import com.vegamstories.app.presentation.components.StoryImage
import com.vegamstories.app.presentation.components.likeIcon
import com.vegamstories.app.presentation.components.pop
import com.vegamstories.app.presentation.preview.ComponentPreviews
import com.vegamstories.app.presentation.preview.PreviewData
import com.vegamstories.app.presentation.preview.PreviewTheme
import kotlinx.coroutines.launch

/**
 * The page after the last comic page.
 *  - Not liked yet: "Did you like this story?" with a big heart and the like count. Tapping the
 *    heart pops it, fills it with colour and rolls the count up. A small "skip" link moves on.
 *  - Liked (just now or before): "You liked this story!", and the next episode slides in below,
 *    or "Back to stories" after the last episode. Tapping the heart again unlikes.
 */
@Composable
fun LikeStoryPage(
    story: Story,
    nextStory: Story?,
    onToggleLike: () -> Unit,
    onOpenNextStory: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLiked = story.isFavorite
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val circleColor by animateColorAsState(
        if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        label = "likeCircle",
    )
    val heartColor by animateColorAsState(
        if (isLiked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "likeHeart",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StoryCover(
            story = story,
            contentDescription = story.title,
            modifier = Modifier
                .size(width = 120.dp, height = 160.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.height(24.dp))
        AnimatedContent(
            targetState = isLiked,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "likeTitle",
        ) { liked ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(if (liked) R.string.liked_title else R.string.like_prompt_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(if (liked) R.string.liked_hint else R.string.like_prompt_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Box(
            modifier = Modifier
                .size(112.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .clip(CircleShape)
                .background(circleColor)
                .clickable(role = Role.Button) {
                    onToggleLike()
                    scope.launch { scale.pop() }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(likeIcon(isLiked)),
                contentDescription = stringResource(if (isLiked) R.string.cd_unlike else R.string.cd_like),
                tint = heartColor,
                modifier = Modifier.size(52.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        RollingCount(count = story.likeCount) { count ->
            Text(
                text = if (count > 0) {
                    pluralStringResource(R.plurals.like_count, count, count.toCompactCount())
                } else {
                    stringResource(R.string.like_count_zero)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // After liking: the way forward. Before: only a quiet skip, so the heart stays the focus.
        AnimatedVisibility(visible = isLiked, enter = fadeIn() + expandVertically(), exit = fadeOut()) {
            Column(
                modifier = Modifier.padding(top = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (nextStory != null) {
                    NextEpisodeCard(story = nextStory, onClick = { onOpenNextStory(nextStory.id) })
                } else {
                    Text(
                        text = stringResource(R.string.all_episodes_read),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onBack) { Text(stringResource(R.string.back_to_stories)) }
                }
            }
        }
        if (!isLiked) {
            Spacer(Modifier.height(20.dp))
            TextButton(onClick = { if (nextStory != null) onOpenNextStory(nextStory.id) else onBack() }) {
                Text(
                    text = stringResource(if (nextStory != null) R.string.skip_to_next_story else R.string.back_to_stories),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "NEXT EPISODE" card: cover, episode + title, and a Read button. The whole card opens it. */
@Composable
private fun NextEpisodeCard(story: Story, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            StoryCover(
                story = story,
                contentDescription = null,
                modifier = Modifier
                    .size(width = 60.dp, height = 80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.next_episode_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = story.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.episode_category, story.episodeNumber, story.categoryName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onClick) { Text(stringResource(R.string.read_action)) }
        }
    }
}

// ---------------- Previews ----------------

@ComponentPreviews
@Composable
private fun LikeStoryPagePreview() = PreviewTheme {
    LikeStoryPage(
        story = PreviewData.story(index = 2),
        nextStory = PreviewData.story(index = 3),
        onToggleLike = {}, onOpenNextStory = {}, onBack = {},
    )
}

@ComponentPreviews
@Composable
private fun LikeStoryPageLikedPreview() = PreviewTheme {
    LikeStoryPage(
        story = PreviewData.story(index = 3, isFavorite = true),
        nextStory = PreviewData.story(index = 4),
        onToggleLike = {}, onOpenNextStory = {}, onBack = {},
    )
}

@ComponentPreviews
@Composable
private fun LikeStoryPageLastEpisodePreview() = PreviewTheme {
    LikeStoryPage(
        story = PreviewData.story(index = 3, isFavorite = true),
        nextStory = null,
        onToggleLike = {}, onOpenNextStory = {}, onBack = {},
    )
}
