package com.vegamstories.app.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vegamstories.app.R
import com.vegamstories.app.presentation.preview.ComponentPreviews
import com.vegamstories.app.presentation.preview.PreviewTheme

/** Sharing the logs: idle, packing them into a zip, or the last try failed. */
enum class ShareLogsState { Idle, Preparing, Failed }

/**
 * Settings → Support → Share logs: the user describes the problem ("stories are not coming"),
 * then the logs go, as a zip with the note, through the app they pick (WhatsApp, Gmail…).
 */
@Composable
fun ShareLogsDialog(
    state: ShareLogsState,
    onShare: (note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var note by rememberSaveable { mutableStateOf("") }
    val preparing = state == ShareLogsState.Preparing
    AlertDialog(
        onDismissRequest = { if (!preparing) onDismiss() },
        title = { Text(stringResource(R.string.share_logs_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.share_logs_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text(stringResource(R.string.share_logs_hint)) },
                    enabled = !preparing,
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state == ShareLogsState.Failed) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.share_logs_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onShare(note) }, enabled = !preparing) {
                if (preparing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.share_logs_action))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !preparing) { Text(stringResource(R.string.cancel)) }
        },
    )
}

// ---------------- Previews ----------------

@ComponentPreviews
@Composable
private fun ShareLogsDialogPreview() = PreviewTheme {
    ShareLogsDialog(state = ShareLogsState.Idle, onShare = {}, onDismiss = {})
}

@ComponentPreviews
@Composable
private fun ShareLogsDialogFailedPreview() = PreviewTheme {
    ShareLogsDialog(state = ShareLogsState.Failed, onShare = {}, onDismiss = {})
}
