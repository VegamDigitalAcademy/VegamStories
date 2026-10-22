package com.vegamstories.app.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.vegamstories.app.presentation.preview.ComponentPreviews
import com.vegamstories.app.presentation.preview.PreviewTheme

/**
 * A box with a light band sweeping across it — the "content is loading" shimmer.
 * Only the draw phase reads the animation, so it doesn't recompose every frame.
 */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape = RectangleShape) {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = if (isSystemInDarkThemeForApp()) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.White.copy(alpha = 0.65f)
    }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )
    Box(
        modifier
            .clip(shape)
            .drawBehind {
                drawRect(base)
                val band = size.width * 0.6f
                // Sweep from fully off the left edge to fully off the right edge.
                val x = -band + progress * (size.width + band * 2)
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, highlight, Color.Transparent),
                        start = Offset(x - band, 0f),
                        end = Offset(x + band, size.height),
                    ),
                )
            },
    )
}

/** True when the app's colour scheme is the dark one (respects the in-app theme setting). */
@Composable
private fun isSystemInDarkThemeForApp(): Boolean =
    MaterialTheme.colorScheme.background.luminanceIsDark()

private fun Color.luminanceIsDark(): Boolean = (0.299f * red + 0.587f * green + 0.114f * blue) < 0.5f

/** Placeholder for one StoryCard while the story list is first loading. */
@Composable
fun StoryCardSkeleton(modifier: Modifier = Modifier) {
    Column(modifier) {
        ShimmerBox(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f),
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(8.dp))
        ShimmerBox(
            Modifier
                .fillMaxWidth(0.8f)
                .height(14.dp),
            shape = RoundedCornerShape(4.dp),
        )
        Spacer(Modifier.height(6.dp))
        ShimmerBox(
            Modifier
                .fillMaxWidth(0.5f)
                .height(10.dp),
            shape = RoundedCornerShape(4.dp),
        )
    }
}

/** Two-column grid of skeleton cards, shown instead of a spinner on first load. */
@Composable
fun StoryGridSkeleton(modifier: Modifier = Modifier, rows: Int = 3) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ShimmerBox(
            Modifier
                .fillMaxWidth(0.4f)
                .height(14.dp),
            shape = RoundedCornerShape(4.dp),
        )
        repeat(rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StoryCardSkeleton(Modifier.weight(1f))
                StoryCardSkeleton(Modifier.weight(1f))
            }
        }
    }
}

// ---------------- Previews ----------------

@ComponentPreviews
@Composable
private fun StoryGridSkeletonPreview() = PreviewTheme {
    StoryGridSkeleton(rows = 2)
}
