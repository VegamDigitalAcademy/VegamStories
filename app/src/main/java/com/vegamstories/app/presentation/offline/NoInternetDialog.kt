package com.vegamstories.app.presentation.offline

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_wifi_off),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
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
