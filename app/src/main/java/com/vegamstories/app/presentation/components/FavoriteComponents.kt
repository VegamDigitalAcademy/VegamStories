package com.vegamstories.app.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vegamstories.app.R
import kotlinx.coroutines.launch

/**
 * The star (reader top bar): saves the story in Favorites, the user's own list, or takes it out.
 * Separate from the heart, which is a like counted on the server. Saved, the star fills gold,
 * spins one point round and pops; taken out, it just gives a small bounce.
 */
@Composable
fun FavoriteButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 22.dp,
) {
    val pop = remember { Animatable(1f) }
    val spin = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val tint by animateColorAsState(
        if (isFavorite) FavoriteGold else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "favoriteTint",
    )
    IconButton(
        onClick = {
            val saving = !isFavorite
            haptics.toggle(on = saving)
            onClick()
            scope.launch {
                if (saving) {
                    // One point round: a star looks the same after 72°, so it ends where it began.
                    launch {
                        spin.snapTo(0f)
                        spin.animateTo(STAR_POINT_DEGREES, tween(durationMillis = 450, easing = FastOutSlowInEasing))
                        spin.snapTo(0f)
                    }
                    pop.snapTo(0.55f)
                } else {
                    pop.snapTo(0.8f)
                }
                pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
            }
        },
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(if (isFavorite) R.drawable.ic_star else R.drawable.ic_star_border),
            contentDescription = stringResource(if (isFavorite) R.string.cd_remove_favorite else R.string.cd_add_favorite),
            tint = tint,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                    rotationZ = spin.value
                },
        )
    }
}

/** A favorite's star: warm gold, apart from the heart's red. */
val FavoriteGold = Color(0xFFF5A623)

private const val STAR_POINT_DEGREES = 72f
