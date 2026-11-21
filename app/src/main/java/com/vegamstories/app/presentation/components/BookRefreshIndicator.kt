package com.vegamstories.app.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pull to refresh, as a little open book: pulling turns its page over, and while the stories
 * load its pages keep flipping.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.BookRefreshIndicator(state: PullToRefreshState, isRefreshing: Boolean) {
    // How far over the turning page is: 0 flat on the right … 1 flat on the left. Pulled all the
    // way, it's still on its way over, so it reads as a page turning.
    val turned: () -> Float = if (isRefreshing) {
        val pages = rememberInfiniteTransition(label = "refreshing")
        val turn = pages.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(FLIP_MILLIS, easing = FastOutSlowInEasing), RepeatMode.Restart),
            label = "page",
        );
        { turn.value }
    } else {
        { state.distanceFraction.coerceIn(0f, 1f) * PULLED_TURN }
    }
    PullToRefreshDefaults.IndicatorBox(
        state = state,
        isRefreshing = isRefreshing,
        modifier = Modifier.align(Alignment.TopCenter),
        // The logo's colours: navy round it, an orange book with cream pages; clear on light and dark.
        containerColor = NAVY,
    ) {
        Canvas(Modifier.size(BOOK_WIDTH, BOOK_HEIGHT)) { drawOpenBook(turned(), ORANGE, LINES_COLOR) }
    }
}

/** An open book, its spine down the middle, with one page [turned] part of the way over. */
private fun DrawScope.drawOpenBook(turned: Float, cover: Color, lines: Color) {
    val spine = size.width / 2f
    val pageWidth = size.width / 2f - 1.5.dp.toPx()
    val top = 1.5.dp.toPx()
    val bottom = size.height - 2.5.dp.toPx()
    // The cover, a little bigger than the pages, behind them.
    drawRoundRect(cover, cornerRadius = CornerRadius(2.dp.toPx()))
    // The two pages lying open.
    drawPage(left = spine - pageWidth, right = spine, top = top, bottom = bottom, lines = lines)
    drawPage(left = spine, right = spine + pageWidth, top = top, bottom = bottom, lines = lines)
    drawRect(cover.copy(alpha = 0.25f), Offset(spine - 0.5.dp.toPx(), top), Size(1.dp.toPx(), bottom - top))

    // The turning page: seen edge on halfway over, its outer edge nearer and so a bit taller.
    if (turned <= 0f || turned >= 1f) return
    val angle = turned * PI.toFloat()
    val reach = pageWidth * cos(angle) // + to the right of the spine, - to the left
    val lift = (bottom - top) * 0.12f * sin(angle)
    val page = Path().apply {
        moveTo(spine, top)
        lineTo(spine + reach, top - lift)
        lineTo(spine + reach, bottom + lift)
        lineTo(spine, bottom)
        close()
    }
    // Darker as it stands up away from the light.
    drawPath(page, lerp(PAPER, PAPER_SHADE, 1f - abs(cos(angle))))
    drawPath(page, lines.copy(alpha = lines.alpha * 0.6f), style = Stroke(0.6.dp.toPx()))
}

/** A page with a few lines of writing on it. */
private fun DrawScope.drawPage(left: Float, right: Float, top: Float, bottom: Float, lines: Color) {
    drawRect(PAPER, Offset(left, top), Size(right - left, bottom - top))
    val margin = 2.dp.toPx()
    val gap = (bottom - top - margin * 2f) / LINES
    repeat(LINES) { i ->
        val y = top + margin + gap * (i + 0.5f)
        val end = if (i == LINES - 1) left + (right - left) * 0.55f else right - margin
        drawLine(lines, Offset(left + margin, y), Offset(end, y), strokeWidth = 0.8.dp.toPx())
    }
}

private val BOOK_WIDTH = 28.dp
private val BOOK_HEIGHT = 20.dp
private const val PULLED_TURN = 0.8f
private const val LINES = 4
private const val FLIP_MILLIS = 650

private val NAVY = Color(0xFF25245A)
private val ORANGE = Color(0xFFF59E0B)
private val LINES_COLOR = Color(0xFFB0AAB0)
private val PAPER = Color(0xFFFFF7E6)
private val PAPER_SHADE = Color(0xFFD9D2C3)
