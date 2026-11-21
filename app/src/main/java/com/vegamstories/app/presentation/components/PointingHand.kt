package com.vegamstories.app.presentation.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

/**
 * A cartoon hand pointing with its index finger, to show what to do (drag this, rub here): the
 * fingertip is at [tip], the hand below it, in the navy sleeve and orange cuff of the pull-to-refresh
 * hand. [unit] sizes it (about 85 units tall), [press] (0 … 1) pushes the finger down onto the screen,
 * [tilt] leans it (degrees), and [alpha] fades it as a whole.
 */
internal fun DrawScope.drawPointingHand(tip: Offset, unit: Float, press: Float = 0f, tilt: Float = -18f, alpha: Float = 1f) {
    if (alpha <= 0.01f) return
    withAlpha(alpha) {
        rotate(degrees = tilt, pivot = tip) {
            // Pressed, the hand comes down a little towards the screen.
            scale(scale = 1f - 0.08f * press, pivot = tip) {
                translate(tip.x, tip.y) { hand(unit) }
            }
        }
    }
}

/** The hand with its fingertip at (0, 0), finger up. */
private fun DrawScope.hand(u: Float) {
    val skin = Path().apply {
        // The index finger, pointing up.
        addRoundRect(RoundRect(Rect(-5.5f * u, 0f, 5.5f * u, 38f * u), CornerRadius(5.5f * u)))
        // The palm, and the three folded fingers as bumps along its top.
        addRoundRect(RoundRect(Rect(-7f * u, 24f * u, 26f * u, 60f * u), CornerRadius(10f * u)))
        addOval(Rect(Offset(11.5f * u, 28f * u), 6.2f * u))
        addOval(Rect(Offset(18.5f * u, 30f * u), 5.8f * u))
        addOval(Rect(Offset(24f * u, 34f * u), 5f * u))
    }
    val thumb = Path().apply {
        addRoundRect(RoundRect(Rect(-5f * u, -20f * u, 5f * u, 0f), CornerRadius(5f * u)))
    }
    // The thumb: from the bottom of the palm, up along its left side.
    val thumbBase = Offset(-3f * u, 52f * u)
    val thumbDegrees = -32f

    // A soft shadow under the hand, so it shows on any page (one shadow, however its shapes overlap).
    withAlpha(SHADOW_ALPHA) {
        translate(2.5f * u, 3.5f * u) {
            drawPath(skin, Color.Black)
            rotate(thumbDegrees, pivot = thumbBase) { translate(thumbBase.x, thumbBase.y) { drawPath(thumb, Color.Black) } }
            drawRoundRect(Color.Black, topLeft = Offset(-10f * u, 56f * u), size = Size(39f * u, 29f * u), cornerRadius = CornerRadius(4f * u))
        }
    }

    // Outline first, then the skin over it: the shapes merge into one hand with one outline.
    val outline = Stroke(width = 2.6f * u)
    drawPath(skin, Outline, style = outline)
    rotate(thumbDegrees, pivot = thumbBase) { translate(thumbBase.x, thumbBase.y) { drawPath(thumb, Outline, style = outline) } }
    drawPath(skin, Skin)
    rotate(thumbDegrees, pivot = thumbBase) { translate(thumbBase.x, thumbBase.y) { drawPath(thumb, Skin) } }

    // Shading and creases: the palm's side, between the folded fingers, the finger's knuckle.
    drawRoundRect(SkinShade, topLeft = Offset(19f * u, 40f * u), size = Size(5f * u, 15f * u), cornerRadius = CornerRadius(2.5f * u))
    val crease = 1.3f * u
    drawLine(Outline.copy(alpha = 0.7f), Offset(15f * u, 27f * u), Offset(15.5f * u, 33f * u), strokeWidth = crease, cap = StrokeCap.Round)
    drawLine(Outline.copy(alpha = 0.7f), Offset(21.5f * u, 30f * u), Offset(21.5f * u, 35f * u), strokeWidth = crease, cap = StrokeCap.Round)
    drawLine(Outline.copy(alpha = 0.55f), Offset(-2.5f * u, 20f * u), Offset(2.5f * u, 20f * u), strokeWidth = crease, cap = StrokeCap.Round)
    drawLine(Outline.copy(alpha = 0.55f), Offset(-2f * u, 22.5f * u), Offset(2f * u, 22.5f * u), strokeWidth = crease, cap = StrokeCap.Round)
    // The fingernail.
    drawRoundRect(Nail, topLeft = Offset(-3.4f * u, 2f * u), size = Size(6.8f * u, 8f * u), cornerRadius = CornerRadius(3f * u))
    drawRoundRect(Outline.copy(alpha = 0.35f), topLeft = Offset(-3.4f * u, 2f * u), size = Size(6.8f * u, 8f * u), cornerRadius = CornerRadius(3f * u), style = Stroke(0.9f * u))

    // The sleeve, and its orange cuff over the wrist.
    drawRoundRect(
        brush = Brush.horizontalGradient(listOf(NavyDark, NavyLight, NavyDark), startX = -8f * u, endX = 27f * u),
        topLeft = Offset(-8f * u, 63f * u),
        size = Size(35f * u, 22f * u),
        cornerRadius = CornerRadius(3f * u),
    )
    drawRoundRect(Orange, topLeft = Offset(-10f * u, 56f * u), size = Size(39f * u, 9f * u), cornerRadius = CornerRadius(3f * u))
    drawRoundRect(OrangeDark, topLeft = Offset(-10f * u, 56f * u), size = Size(39f * u, 9f * u), cornerRadius = CornerRadius(3f * u), style = Stroke(1.2f * u))
}

/** Draws [block] faded as a whole, so its overlapping shapes don't show through one another. */
internal inline fun DrawScope.withAlpha(alpha: Float, block: DrawScope.() -> Unit) {
    if (alpha >= 0.999f) {
        block()
        return
    }
    drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { this.alpha = alpha })
    block()
    drawContext.canvas.restore()
}

private val Skin = Color(0xFFF7C9A9)
private val SkinShade = Color(0xFFE8AE8E)
private val Nail = Color(0xFFFCE3D4)
private val Outline = Color(0xFF8A4F3A)
private const val SHADOW_ALPHA = 0.25f
private val NavyDark = Color(0xFF1B1A45)
private val NavyLight = Color(0xFF34337A)
private val Orange = Color(0xFFF59E0B)
private val OrangeDark = Color(0xFFB8740A)
