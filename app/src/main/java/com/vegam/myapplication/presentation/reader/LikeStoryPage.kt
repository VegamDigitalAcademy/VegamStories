package com.vegam.myapplication.presentation.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.vegam.myapplication.R
import com.vegam.myapplication.core.util.toCompactCount
import com.vegam.myapplication.domain.model.Story
import com.vegam.myapplication.presentation.components.RollingCount
import com.vegam.myapplication.presentation.components.StoryImage
import com.vegam.myapplication.presentation.components.likeIcon
import com.vegam.myapplication.presentation.components.pop
import com.vegam.myapplication.presentation.preview.ComponentPreviews
import com.vegam.myapplication.presentation.preview.PreviewData
import com.vegam.myapplication.presentation.preview.PreviewTheme
import kotlinx.coroutines.launch

/**
 * The page after the last comic page: "Did you like this story?" with a big heart and the like
 * count. Tapping the heart pops it, fills it with colour and rolls the count up. If the story is
 * already liked it opens in the liked state ("You liked this story!"); tapping again unlikes.
 */
@Composable
fun LikeStoryPage(story: Story, onToggleLike: () -> Unit, modifier: Modifier = Modifier) {
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
        StoryImage(
            model = story.coverImageUrl,
            contentDescription = story.title,
            contentScale = ContentScale.Crop,
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
    }
}

// ---------------- Previews ----------------

@ComponentPreviews
@Composable
private fun LikeStoryPagePreview() = PreviewTheme {
    LikeStoryPage(story = PreviewData.story(index = 2), onToggleLike = {})
}

@ComponentPreviews
@Composable
private fun LikeStoryPageLikedPreview() = PreviewTheme {
    LikeStoryPage(story = PreviewData.story(index = 3, isFavorite = true), onToggleLike = {})
}
