package com.vegamstories.app.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vegamstories.app.R
import com.vegamstories.app.core.logging.ApiCallCount
import com.vegamstories.app.presentation.components.BackTopBar
import com.vegamstories.app.presentation.components.EmptyState
import com.vegamstories.app.presentation.preview.PreviewTheme
import com.vegamstories.app.presentation.preview.ScreenPreviews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ApiCallsScreen(
    onBack: () -> Unit,
    viewModel: ApiCallsViewModel = hiltViewModel(),
) {
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    ApiCallsContent(counts = counts, onBack = onBack, onReset = viewModel::onReset)
}

/**
 * Debug builds only: every API the app has called, with its last call and how many times, the
 * latest first. The total of all calls stays at the bottom.
 */
@Composable
fun ApiCallsContent(
    counts: List<ApiCallCount>,
    onBack: () -> Unit,
    onReset: () -> Unit,
) {
    Scaffold(
        topBar = {
            BackTopBar(
                title = stringResource(R.string.settings_api_calls),
                onBack = onBack,
                actions = {
                    if (counts.isNotEmpty()) {
                        TextButton(onClick = onReset) { Text(stringResource(R.string.api_calls_reset)) }
                    }
                },
            )
        },
        containerColor = Color.Transparent, // AppBackground shows through
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        val modifier = Modifier
            .padding(padding)
            .fillMaxSize()
        if (counts.isEmpty()) {
            EmptyState(
                icon = R.drawable.ic_info,
                title = stringResource(R.string.api_calls_empty_title),
                message = stringResource(R.string.api_calls_empty_message),
                modifier = modifier,
            )
            return@Scaffold
        }
        val bold = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
        Column(modifier) {
            ApiCallRow(
                time = stringResource(R.string.api_calls_last_call),
                api = stringResource(R.string.api_calls_api),
                count = stringResource(R.string.api_calls_count),
                style = bold,
                color = MaterialTheme.colorScheme.primary,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            LazyColumn(Modifier.weight(1f)) {
                items(counts, key = { it.name }) { call ->
                    ApiCallRow(time = apiCallTime(call.lastCalledAt), api = call.name, count = call.count.toString())
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            ApiCallRow(
                time = stringResource(R.string.api_calls_total),
                api = "",
                count = counts.sumOf { it.count }.toString(),
                style = bold,
            )
        }
    }
}

/** One line of the table: when, which API, how many times. */
@Composable
private fun ApiCallRow(
    time: String,
    api: String,
    count: String,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(time, style = style, color = color, modifier = Modifier.width(TimeColumnWidth))
        Text(api, style = style, color = color, modifier = Modifier.weight(1f))
        Text(count, style = style, color = color, textAlign = TextAlign.End, modifier = Modifier.width(CountColumnWidth))
    }
}

/** When an API was last called, in the phone's time zone: "19 Oct 2026 19:15:20". */
internal fun apiCallTime(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.ENGLISH).format(Date(millis))

private val TimeColumnWidth = 150.dp
private val CountColumnWidth = 56.dp

// ---------------- Previews ----------------

@ScreenPreviews
@Composable
private fun ApiCallsPreview() = PreviewTheme {
    val now = System.currentTimeMillis()
    ApiCallsContent(
        counts = listOf(
            ApiCallCount("getLikeCounts", 6, now),
            ApiCallCount("getStories", 3, now - 60_000),
            ApiCallCount("getPointer", 2, now - 120_000),
            ApiCallCount("getCategories", 1, now - 600_000),
        ),
        onBack = {},
        onReset = {},
    )
}

@ScreenPreviews
@Composable
private fun ApiCallsEmptyPreview() = PreviewTheme {
    ApiCallsContent(counts = emptyList(), onBack = {}, onReset = {})
}
