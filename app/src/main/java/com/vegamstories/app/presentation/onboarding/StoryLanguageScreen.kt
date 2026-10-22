package com.vegamstories.app.presentation.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vegamstories.app.R
import com.vegamstories.app.domain.model.Language
import com.vegamstories.app.presentation.components.VegamLogoMark
import com.vegamstories.app.presentation.preview.PreviewTheme
import com.vegamstories.app.presentation.preview.ScreenPreviews
import com.vegamstories.app.presentation.preview.TeluguPreview
import com.vegamstories.app.ui.theme.AppBackground

/**
 * Shown once, before Home, until a story language has been chosen. This is the language of the
 * stories (Home's language button), not the app's own language in Settings.
 */
@Composable
fun StoryLanguageScreen(viewModel: StoryLanguageViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StoryLanguageContent(
        state = state,
        onLanguageChosen = viewModel::onLanguageChosen,
        onRetry = viewModel::load,
    )
}

@Composable
fun StoryLanguageContent(
    state: StoryLanguageUiState,
    onLanguageChosen: (String) -> Unit,
    onRetry: () -> Unit,
) {
    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            VegamLogoMark(size = 72)
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.story_language_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.story_language_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            when {
                state.languages.isNotEmpty() -> LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.languages, key = { it.code }) { language ->
                        LanguageCard(
                            language = language,
                            suggested = language.code == state.suggested,
                            onClick = { onLanguageChosen(language.code) },
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
                state.loadFailed -> Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.story_language_load_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                    Spacer(Modifier.height(8.dp))
                    // Never stuck offline: continue in the phone's language (or Telugu), change it later on Home.
                    OutlinedButton(onClick = { state.suggested?.let(onLanguageChosen) }) {
                        Text(stringResource(R.string.continue_action))
                    }
                }
                else -> Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

/** A language in its own script (big), with its English name below. */
@Composable
private fun LanguageCard(language: Language, suggested: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick, role = Role.Button),
        shape = RoundedCornerShape(16.dp),
        color = if (suggested) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (suggested) 2.dp else 1.dp,
            color = if (suggested) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = language.nativeName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (language.name != language.nativeName) {
                    Text(
                        text = language.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

// ---------------- Previews ----------------

private val previewLanguages = listOf(
    Language("te", "Telugu", "తెలుగు"),
    Language("en", "English", "English"),
    Language("or", "Odia", "ଓଡ଼ିଆ"),
)

@ScreenPreviews
@TeluguPreview
@Composable
private fun StoryLanguagePreview() = PreviewTheme {
    StoryLanguageContent(
        state = StoryLanguageUiState(languages = previewLanguages, suggested = "te", isLoading = false),
        onLanguageChosen = {}, onRetry = {},
    )
}

@ScreenPreviews
@Composable
private fun StoryLanguageOfflinePreview() = PreviewTheme {
    StoryLanguageContent(
        state = StoryLanguageUiState(isLoading = false, loadFailed = true, suggested = "te"),
        onLanguageChosen = {}, onRetry = {},
    )
}
