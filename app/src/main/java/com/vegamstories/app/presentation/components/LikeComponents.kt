package com.vegamstories.app.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vegamstories.app.R
import com.vegamstories.app.core.util.toCompactCount
import com.vegamstories.app.presentation.preview.ComponentPreviews
import com.vegamstories.app.presentation.preview.PreviewTheme
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Read-only heart + count for story cards. Not clickable: liking happens in the reader. */
@Composable
fun LikeCount(isLiked: Boolean, likeCount: Int, modifier: Modifier = Modifier) {
    val tint = likeTint(isLiked)
    val description = pluralStringResource(R.plurals.like_count, likeCount, likeCount.toCompactCount())
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(likeIcon(isLiked)),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        if (likeCount > 0) {
            Spacer(Modifier.width(3.dp))
            Text(
                text = likeCount.toCompactCount(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = tint,
            )
        }
    }
}

/**
 * Clickable heart + count (reader top bar). On tap the heart pops, its colour fades to the
 * liked colour and the count rolls up (or down when unliking). Liking, a ring and little hearts
 * burst out of it (see [heartBurst]).
 */
@Composable
fun LikeButton(
    isLiked: Boolean,
    likeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 22.dp,
) {
    val scale = remember { Animatable(1f) }
    val burst = rememberHeartBurst(isLiked)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val tint by animateColorAsState(likeTint(isLiked), label = "likeTint")
    // Liked somewhere else on the screen (the big heart at the end of a story): pop here too, so the
    // like is seen landing. Not when first shown, and not twice when this button was the one tapped.
    var wasLiked by remember { mutableStateOf(isLiked) }
    LaunchedEffect(isLiked) {
        if (isLiked && !wasLiked && !scale.isRunning) scale.pop()
        wasLiked = isLiked
    }
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Button) {
                haptics.toggle(on = !isLiked)
                onClick()
                scope.launch { scale.pop() }
            }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(likeIcon(isLiked)),
            contentDescription = stringResource(if (isLiked) R.string.cd_unlike else R.string.cd_like),
            tint = tint,
            modifier = Modifier
                .size(iconSize)
                .heartBurst(burst, MaterialTheme.colorScheme.primary)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
        )
        RollingCount(count = likeCount) { count ->
            if (count > 0) {
                Text(
                    text = count.toCompactCount(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = tint,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

/** Shows [content] for [count]; when the count changes, the new value slides in from below (up) or above (down). */
@Composable
fun RollingCount(count: Int, modifier: Modifier = Modifier, content: @Composable (Int) -> Unit) {
    AnimatedContent(
        targetState = count,
        modifier = modifier,
        transitionSpec = {
            val up = targetState > initialState
            (slideInVertically { h -> if (up) h else -h } + fadeIn())
                .togetherWith(slideOutVertically { h -> if (up) -h else h } + fadeOut())
                .using(SizeTransform(clip = false))
        },
        label = "rollingCount",
    ) { value -> content(value) }
}

/**
 * A heart burst's progress, 0 … 1: plays each time [isLiked] turns on (from a tap here or
 * anywhere else), not when first shown or when unliking.
 */
@Composable
fun rememberHeartBurst(isLiked: Boolean): Animatable<Float, AnimationVector1D> {
    val burst = remember { Animatable(1f) }
    var wasLiked by remember { mutableStateOf(isLiked) }
    LaunchedEffect(isLiked) {
        if (isLiked && !wasLiked) {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(durationMillis = BURST_MILLIS, easing = LinearOutSlowInEasing))
        }
        wasLiked = isLiked
    }
    return burst
}

/**
 * A like bursting out of a heart, behind it: a ring that grows and fades, and little hearts and
 * dots flying out and fading, all in [color]. [burst] is its progress ([rememberHeartBurst]).
 */
fun Modifier.heartBurst(burst: Animatable<Float, AnimationVector1D>, color: Color): Modifier =
    drawBehind {
        val t = burst.value
        if (t >= 1f) return@drawBehind
        val r = size.minDimension / 2f
        val fade = 1f - t
        drawCircle(color.copy(alpha = 0.55f * fade), radius = r * (0.6f + 0.9f * t), style = Stroke(width = r * 0.22f * fade))
        repeat(BURST_PIECES) { i ->
            val angle = (i * 2 * PI / BURST_PIECES - PI / 2).toFloat() + 0.15f * (i % 2)
            val distance = r * (0.9f + 0.9f * t) * (if (i % 2 == 0) 1f else 0.82f)
            val at = center + Offset(cos(angle) * distance, sin(angle) * distance)
            val pieceSize = r * 0.32f * (1f - t * 0.6f)
            val pieceColor = (if (i % 3 == 0) BurstGold else color).copy(alpha = fade)
            if (i % 2 == 0) drawLittleHeart(at, pieceSize, pieceColor) else drawCircle(pieceColor, radius = pieceSize * 0.45f, center = at)
        }
    }

/** A tiny heart, [size] across, centred on [center]. */
private fun DrawScope.drawLittleHeart(center: Offset, size: Float, color: Color) {
    val h = size
    val heart = Path().apply {
        moveTo(center.x, center.y + h * 0.42f)
        cubicTo(center.x - h * 0.9f, center.y - h * 0.1f, center.x - h * 0.35f, center.y - h * 0.75f, center.x, center.y - h * 0.25f)
        cubicTo(center.x + h * 0.35f, center.y - h * 0.75f, center.x + h * 0.9f, center.y - h * 0.1f, center.x, center.y + h * 0.42f)
        close()
    }
    drawPath(heart, color)
}

private const val BURST_MILLIS = 650
private const val BURST_PIECES = 8
private val BurstGold = Color(0xFFFFC94D)

/** Quick grow, then a bouncy settle back to normal size. */
suspend fun Animatable<Float, AnimationVector1D>.pop() {
    animateTo(1.35f, tween(durationMillis = 110))
    animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
}

@Composable
fun likeTint(isLiked: Boolean): Color =
    if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

fun likeIcon(isLiked: Boolean): Int = if (isLiked) R.drawable.ic_favorite else R.drawable.ic_favorite_border

// ---------------- Previews ----------------

@ComponentPreviews
@Composable
private fun LikePreview() = PreviewTheme {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LikeCount(isLiked = false, likeCount = 128)
        Spacer(Modifier.width(16.dp))
        LikeCount(isLiked = true, likeCount = 1_540)
        Spacer(Modifier.width(16.dp))
        LikeButton(isLiked = false, likeCount = 0, onClick = {})
        LikeButton(isLiked = true, likeCount = 42, onClick = {})
    }
}
