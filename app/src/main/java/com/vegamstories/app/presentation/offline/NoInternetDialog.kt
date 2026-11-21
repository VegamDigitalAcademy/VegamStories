package com.vegamstories.app.presentation.offline

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vegamstories.app.R
import com.vegamstories.app.core.logging.AppLogger
import com.vegamstories.app.presentation.preview.ComponentPreviews
import com.vegamstories.app.presentation.preview.PreviewTheme

private const val TAG = "NoInternetDialog"

/** Why the "No internet" notice shows. */
enum class NoInternetNotice {
    /** The phone went offline (or the app opened without internet). */
    OFFLINE,

    /** A story was tapped that isn't saved on the phone. */
    STORY_NOT_SAVED,
}

/**
 * Tells the reader there's no internet: [notice] says why. Turn on internet opens the phone's
 * internet settings (a quick panel on Android 10+).
 */
@Composable
fun NoInternetDialog(notice: NoInternetNotice, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { SearchingWifi() },
        title = { Text(stringResource(R.string.no_internet_title)) },
        text = {
            Text(
                stringResource(
                    when (notice) {
                        NoInternetNotice.OFFLINE -> R.string.no_internet_message
                        NoInternetNotice.STORY_NOT_SAVED -> R.string.no_internet_story_message
                    },
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    context.openInternetSettings()
                    onDismiss()
                },
            ) { Text(stringResource(R.string.no_internet_turn_on)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.no_internet_ok)) }
        },
    )
}

/**
 * Wi-Fi looking for a signal: its bars light up one by one from the dot, then fade, and a slash
 * strikes through them; over and over.
 */
@Composable
private fun SearchingWifi() {
    val lit = MaterialTheme.colorScheme.primary
    val gap = MaterialTheme.colorScheme.surfaceContainerHigh // the dialog's own colour
    val looking = rememberInfiniteTransition(label = "wifi")
    val time = looking.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(WIFI_LOOP_MILLIS, easing = LinearEasing)),
        label = "time",
    )
    Canvas(Modifier.size(width = 32.dp, height = 28.dp)) {
        val t = time.value
        val stroke = 3.dp.toPx()
        val base = Offset(size.width / 2f, size.height - 3.dp.toPx())
        // The bars fade back down once all are lit, as the slash comes.
        val fadeOut = 1f - step(t, WIFI_FADE_FROM, WIFI_FADE_FROM + 0.15f)
        fun brightness(bar: Int) = DIM + (1f - DIM) * step(t, 0.08f * bar, 0.08f * bar + 0.1f) * fadeOut
        drawCircle(lit.copy(alpha = brightness(0)), radius = 2.4.dp.toPx(), center = base)
        listOf(7.dp, 14.dp, 21.dp).forEachIndexed { i, radius ->
            val r = radius.toPx()
            drawArc(
                color = lit.copy(alpha = brightness(i + 1)),
                startAngle = 225f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(base.x - r, base.y - r),
                size = Size(r * 2f, r * 2f),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        // The slash, drawn from its top down, with a gap either side so it reads over the bars.
        val drawn = step(t, WIFI_SLASH_FROM, WIFI_SLASH_FROM + 0.12f)
        val gone = 1f - step(t, 0.9f, 1f)
        if (drawn > 0f && gone > 0f) {
            val from = Offset(size.width * 0.18f, size.height * 0.08f)
            val to = Offset(size.width * 0.82f, size.height * 0.98f)
            val end = from + (to - from) * drawn
            drawLine(gap, from, end, strokeWidth = stroke * 2.2f, cap = StrokeCap.Round, alpha = gone)
            drawLine(lit, from, end, strokeWidth = stroke, cap = StrokeCap.Round, alpha = gone)
        }
    }
}

/** 0 before [from], 1 after [to], easing smoothly between. */
private fun step(t: Float, from: Float, to: Float): Float {
    val x = ((t - from) / (to - from)).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/** One go of the Wi-Fi looking for a signal, how bright an unlit bar is, and when it gives up. */
private const val WIFI_LOOP_MILLIS = 2_800
private const val DIM = 0.22f
private const val WIFI_FADE_FROM = 0.5f
private const val WIFI_SLASH_FROM = 0.55f

private fun Context.openInternetSettings() {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
    } else {
        Intent(Settings.ACTION_WIRELESS_SETTINGS)
    }
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        log.w("$TAG: No internet settings screen", e)
    }
}

private val log = AppLogger(TAG)

// ---------------- Previews ----------------

@ComponentPreviews
@Composable
private fun NoInternetDialogPreview() = PreviewTheme {
    NoInternetDialog(NoInternetNotice.STORY_NOT_SAVED, onDismiss = {})
}
