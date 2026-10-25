package com.vegamstories.app.presentation.reader

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vegamstories.app.R

/** A book's satin ribbon marker: deep red, darker toward its edges. */
private val RibbonRed = Color(0xFFB3261E)
private val RibbonRedDark = Color(0xFF7F1C16)

/**
 * The ribbon at the top of a page, as in a book: tap it to mark the page (it drops down, red), tap
 * again to take the mark off. Unmarked, only its short tip shows, outlined in [idleColor].
 */
@Composable
fun BookmarkRibbon(
    marked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    idleColor: Color = Color.White.copy(alpha = 0.9f),
) {
    val length by animateDpAsState(
        targetValue = if (marked) 44.dp else 24.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "ribbon",
    )
    val label = stringResource(R.string.bookmark_page)
    Box(
        modifier
            .size(width = 48.dp, height = 56.dp) // room for a thumb, more than the ribbon
            .toggleable(
                value = marked,
                interactionSource = null,
                indication = null, // the ribbon dropping is the feedback
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            .semantics { contentDescription = label }
            .drawBehind { drawRibbon(length.toPx(), marked, idleColor) },
    )
}

private fun DrawScope.drawRibbon(length: Float, marked: Boolean, idleColor: Color) {
    val width = 16.dp.toPx()
    val left = (size.width - width) / 2
    // A strip hanging from the top edge, its end cut into a V.
    val ribbon = Path().apply {
        moveTo(left, 0f)
        lineTo(left + width, 0f)
        lineTo(left + width, length)
        lineTo(left + width / 2, length - 6.dp.toPx())
        lineTo(left, length)
        close()
    }
    if (marked) {
        translate(left = 1.dp.toPx(), top = 1.5.dp.toPx()) { drawPath(ribbon, Color.Black.copy(alpha = 0.25f)) }
        drawPath(
            ribbon,
            Brush.horizontalGradient(
                0f to RibbonRedDark,
                0.5f to RibbonRed,
                1f to RibbonRedDark,
                startX = left,
                endX = left + width,
            ),
        )
    } else {
        // A faint fill so the outline shows on light pages and dark pictures alike.
        drawPath(ribbon, Color.Black.copy(alpha = 0.18f))
        drawPath(ribbon, idleColor, style = Stroke(1.2.dp.toPx()))
    }
}
