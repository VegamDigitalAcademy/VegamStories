package com.vegamstories.app.presentation.settings

import com.vegamstories.app.core.logging.SharedLogs
import com.vegamstories.app.core.logging.AppLogger
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Icon
import android.content.ClipData
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vegamstories.app.R
import com.vegamstories.app.domain.model.AUTO_TURN_SECONDS_OPTIONS
import com.vegamstories.app.domain.model.AppLanguage
import com.vegamstories.app.domain.model.ThemeMode
import com.vegamstories.app.presentation.components.BackTopBar
import com.vegamstories.app.presentation.components.VegamSwitch
import com.vegamstories.app.presentation.preview.PreviewTheme
import com.vegamstories.app.presentation.preview.ScreenPreviews
import com.vegamstories.app.presentation.preview.TeluguPreview

private const val TAG = "SettingsScreen"

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val newStoryAlerts by viewModel.newStoryAlerts.collectAsStateWithLifecycle()
    val readerSettings by viewModel.readerSettings.collectAsStateWithLifecycle()
    val shareLogs by viewModel.shareLogs.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Support → Share logs: the note dialog, then the share sheet with the zip.
    var showShareLogs by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.logsToShare.collect { logs ->
            showShareLogs = false
            context.shareLogs(logs)
        }
    }
    if (showShareLogs) {
        ShareLogsDialog(
            state = shareLogs,
            onShare = viewModel::onShareLogs,
            onDismiss = {
                showShareLogs = false
                viewModel.onShareLogsDismissed()
            },
        )
    }

    // Whether the system lets the app post notifications at all. Re-checked on every resume,
    // since the user may have just changed it in the phone's settings.
    var notificationsAllowed by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose { }
    }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        permissionDenied = !granted
        if (granted) viewModel.onNewStoryAlertsChange(true)
    }

    SettingsContent(
        themeMode = themeMode,
        language = language,
        newStoryAlerts = newStoryAlerts,
        notificationsBlocked = !notificationsAllowed && (newStoryAlerts || permissionDenied),
        onThemeModeSelected = viewModel::onThemeModeSelected,
        onLanguageSelected = viewModel::onLanguageSelected,
        onNewStoryAlertsChange = { enabled ->
            val needsPermission = enabled &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                // Asked only now, when the user wants alerts; never on first launch.
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.onNewStoryAlertsChange(enabled)
                if (!enabled) permissionDenied = false
            }
        },
        onOpenNotificationSettings = { context.openAppNotificationSettings() },
        autoTurnPages = readerSettings.autoTurnPages,
        autoTurnSeconds = readerSettings.autoTurnSeconds,
        onAutoTurnChange = viewModel::onAutoTurnChange,
        onAutoTurnSecondsChange = viewModel::onAutoTurnSecondsChange,
        onShareLogs = { showShareLogs = true },
        onBack = onBack,
    )
}

/**
 * The share sheet with the logs zip: WhatsApp, Gmail (addressed to support, with a subject) or any
 * app that takes a file. The note and the app/phone line go in the message too.
 */
private fun Context.shareLogs(logs: SharedLogs) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, logs.uri)
        putExtra(Intent.EXTRA_TEXT, logs.text)
        putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_logs_subject))
        putExtra(Intent.EXTRA_EMAIL, arrayOf(getString(R.string.support_email)))
        // Lets the chosen app read the zip (through the FileProvider).
        clipData = ClipData.newRawUri(null, logs.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        startActivity(Intent.createChooser(send, getString(R.string.share_logs_title)))
        log.i("$TAG: Share sheet opened for the logs")
    } catch (e: ActivityNotFoundException) {
        log.w("$TAG: No app can share the logs", e)
    }
}

private val log = AppLogger(TAG)

/** The app's notification page in the phone's settings (Android 8+), else its app-info page. */
private fun Context.openAppNotificationSettings() {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    }
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        log.w("$TAG: No settings screen for notifications", e)
    }
}

@Composable
fun SettingsContent(
    themeMode: ThemeMode,
    language: AppLanguage,
    newStoryAlerts: Boolean,
    /** Alerts are wanted but the phone blocks the app's notifications: show how to fix it. */
    notificationsBlocked: Boolean,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit,
    onNewStoryAlertsChange: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onBack: () -> Unit,
    autoTurnPages: Boolean = true,
    autoTurnSeconds: Int = 10,
    onAutoTurnChange: (Boolean) -> Unit = {},
    onAutoTurnSecondsChange: (Int) -> Unit = {},
    onShareLogs: () -> Unit = {},
) {
    Scaffold(
        topBar = { BackTopBar(title = stringResource(R.string.menu_settings), onBack = onBack) },
        containerColor = Color.Transparent, // AppBackground shows through
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

            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline)

            // ---- Reading ----
            SectionLabel(stringResource(R.string.settings_reading_section))
            SwitchRow(
                title = stringResource(R.string.settings_auto_turn),
                subtitle = stringResource(R.string.settings_auto_turn_desc),
                checked = autoTurnPages,
                onCheckedChange = onAutoTurnChange,
            )
            if (autoTurnPages) {
                SingleChoiceSegmentedButtonRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                ) {
                    AUTO_TURN_SECONDS_OPTIONS.forEachIndexed { i, seconds ->
                        SegmentedButton(
                            selected = seconds == autoTurnSeconds,
                            onClick = { onAutoTurnSecondsChange(seconds) },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = AUTO_TURN_SECONDS_OPTIONS.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        ) { Text(stringResource(R.string.settings_auto_turn_seconds, seconds)) }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline)

            // ---- Notifications ----
            SectionLabel(stringResource(R.string.settings_notifications_section))
            SwitchRow(
                title = stringResource(R.string.settings_new_story_alerts),
                subtitle = stringResource(R.string.settings_new_story_alerts_desc),
                checked = newStoryAlerts,
                onCheckedChange = onNewStoryAlertsChange,
            )
            if (notificationsBlocked) {
                Text(
                    text = stringResource(R.string.settings_notifications_blocked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenNotificationSettings)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline)

            // ---- Support ----
            SectionLabel(stringResource(R.string.settings_support_section))
            ActionRow(
                title = stringResource(R.string.settings_share_logs),
                subtitle = stringResource(R.string.settings_share_logs_desc),
                onClick = onShareLogs,
            )
            Spacer(Modifier.height(16.dp))
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

/** A row that does something when tapped (opens a dialog), with a chevron. */
@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The whole row is one switch for TalkBack and touch.
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        VegamSwitch(checked = checked, onCheckedChange = null)
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
        newStoryAlerts = false,
        notificationsBlocked = false,
        onThemeModeSelected = {},
        onLanguageSelected = {},
        onNewStoryAlertsChange = {},
        onOpenNotificationSettings = {},
        onBack = {},
    )
}

@ScreenPreviews
@Composable
private fun SettingsTeluguDarkPreview() = PreviewTheme {
    SettingsContent(
        themeMode = ThemeMode.DARK,
        language = AppLanguage.TELUGU,
        // Alerts on, but notifications blocked in the phone's settings → the hint shows.
        newStoryAlerts = true,
        notificationsBlocked = true,
        onThemeModeSelected = {},
        onLanguageSelected = {},
        onNewStoryAlertsChange = {},
        onOpenNotificationSettings = {},
        onBack = {},
    )
}
