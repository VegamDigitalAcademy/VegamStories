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
import com.vegam.myapplication.domain.model.ThemeMode
import com.vegam.myapplication.presentation.components.BackTopBar
import com.vegam.myapplication.presentation.preview.PreviewTheme
import com.vegam.myapplication.presentation.preview.ScreenPreviews

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    SettingsContent(
        themeMode = themeMode,
        onThemeModeSelected = viewModel::onThemeModeSelected,
        onBack = onBack,
    )
}

@Composable
fun SettingsContent(
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
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
            Text(
                text = stringResource(R.string.settings_appearance),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
            )
            Text(
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Column(Modifier.selectableGroup()) {
                ThemeOption.entries.forEach { option ->
                    ThemeOptionRow(
                        option = option,
                        selected = option.mode == themeMode,
                        onClick = { onThemeModeSelected(option.mode) },
                    )
                }
            }
        }
    }
}

private enum class ThemeOption(val mode: ThemeMode, @StringRes val titleRes: Int, @StringRes val subtitleRes: Int) {
    SYSTEM(ThemeMode.SYSTEM, R.string.theme_system, R.string.theme_system_desc),
    LIGHT(ThemeMode.LIGHT, R.string.theme_light, R.string.theme_light_desc),
    DARK(ThemeMode.DARK, R.string.theme_dark, R.string.theme_dark_desc),
}

@Composable
private fun ThemeOptionRow(option: ThemeOption, selected: Boolean, onClick: () -> Unit) {
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
            Text(stringResource(option.titleRes), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(option.subtitleRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------- Previews ----------------

@ScreenPreviews
@Composable
private fun SettingsSystemPreview() = PreviewTheme {
    SettingsContent(themeMode = ThemeMode.SYSTEM, onThemeModeSelected = {}, onBack = {})
}

@ScreenPreviews
@Composable
private fun SettingsDarkSelectedPreview() = PreviewTheme {
    SettingsContent(themeMode = ThemeMode.DARK, onThemeModeSelected = {}, onBack = {})
}
