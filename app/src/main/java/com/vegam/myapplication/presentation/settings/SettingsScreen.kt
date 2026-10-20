package com.vegam.myapplication.presentation.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vegam.myapplication.R
import com.vegam.myapplication.domain.model.AppLanguage
import com.vegam.myapplication.domain.model.ThemeMode
import com.vegam.myapplication.presentation.components.BackTopBar
import com.vegam.myapplication.presentation.preview.PreviewTheme
import com.vegam.myapplication.presentation.preview.ScreenPreviews
import com.vegam.myapplication.presentation.preview.TeluguPreview

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    SettingsContent(
        themeMode = themeMode,
        language = language,
        onThemeModeSelected = viewModel::onThemeModeSelected,
        onLanguageSelected = viewModel::onLanguageSelected,
        onBack = onBack,
    )
}

@Composable
fun SettingsContent(
    themeMode: ThemeMode,
    language: AppLanguage,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = { BackTopBar(title = stringResource(R.string.menu_settings), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // ---- Language ----
            SectionLabel(stringResource(R.string.settings_language_section))
            SettingTitle(stringResource(R.string.settings_language))
            Column(Modifier.selectableGroup()) {
                LanguageOption.entries.forEach { option ->
                    RadioOptionRow(
                        title = stringResource(option.titleRes),
                        subtitle = stringResource(option.subtitleRes),
                        selected = option.language == language,
                        onClick = { onLanguageSelected(option.language) },
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline)

            // ---- Appearance ----
            SectionLabel(stringResource(R.string.settings_appearance))
            SettingTitle(stringResource(R.string.settings_theme))
            Column(Modifier.selectableGroup()) {
                ThemeOption.entries.forEach { option ->
                    RadioOptionRow(
                        title = stringResource(option.titleRes),
                        subtitle = stringResource(option.subtitleRes),
                        selected = option.mode == themeMode,
                        onClick = { onThemeModeSelected(option.mode) },
                    )
                }
            }
        }
    }
}

private enum class LanguageOption(val language: AppLanguage, @StringRes val titleRes: Int, @StringRes val subtitleRes: Int) {
    SYSTEM(AppLanguage.SYSTEM, R.string.language_system, R.string.language_system_desc),
    // Title in the language's own script ("తెలుగు"), subtitle in the current UI language ("Telugu").
    ENGLISH(AppLanguage.ENGLISH, R.string.language_english, R.string.language_english_desc),
    TELUGU(AppLanguage.TELUGU, R.string.language_telugu, R.string.language_telugu_desc),
}

private enum class ThemeOption(val mode: ThemeMode, @StringRes val titleRes: Int, @StringRes val subtitleRes: Int) {
    SYSTEM(ThemeMode.SYSTEM, R.string.theme_system, R.string.theme_system_desc),
    LIGHT(ThemeMode.LIGHT, R.string.theme_light, R.string.theme_light_desc),
    DARK(ThemeMode.DARK, R.string.theme_dark, R.string.theme_dark_desc),
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

@Composable
private fun RadioOptionRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The whole row is one radio button for TalkBack and touch.
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.padding(8.dp),
        )
        Spacer(Modifier.width(4.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------- Previews ----------------

@ScreenPreviews
@TeluguPreview
@Composable
private fun SettingsSystemPreview() = PreviewTheme {
    SettingsContent(
        themeMode = ThemeMode.SYSTEM,
        language = AppLanguage.SYSTEM,
        onThemeModeSelected = {},
        onLanguageSelected = {},
        onBack = {},
    )
}

@ScreenPreviews
@Composable
private fun SettingsTeluguDarkPreview() = PreviewTheme {
    SettingsContent(
        themeMode = ThemeMode.DARK,
        language = AppLanguage.TELUGU,
        onThemeModeSelected = {},
        onLanguageSelected = {},
        onBack = {},
    )
}
