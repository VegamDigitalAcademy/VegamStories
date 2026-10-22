package com.vegamstories.app.presentation.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import com.vegamstories.app.domain.model.ReaderTextSize
import com.vegamstories.app.domain.model.StoryFormat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.vegamstories.app.R
import com.vegamstories.app.domain.model.PageBackground
import com.vegamstories.app.domain.model.ReaderScrollMode
import com.vegamstories.app.domain.model.ReaderSettings
import com.vegamstories.app.presentation.components.VegamSwitch
import com.vegamstories.app.presentation.preview.ComponentPreviews
import com.vegamstories.app.presentation.preview.PreviewTheme
import com.vegamstories.app.ui.theme.DarkColorScheme
import com.vegamstories.app.ui.theme.SepiaColorScheme

/**
 * Re-themes the reader for the chosen page background. [PageBackground.DEFAULT] keeps the app
 * theme; sepia and dark swap the whole colour scheme so text, icons and cards stay readable.
 * Always one MaterialTheme call, so changing the background keeps the reader's scroll position.
 */
@Composable
fun ReaderPageTheme(background: PageBackground, content: @Composable () -> Unit) {
    val appBackground = MaterialTheme.colorScheme.background
    val colorScheme = when (background) {
        PageBackground.DEFAULT -> MaterialTheme.colorScheme
        PageBackground.SEPIA -> SepiaColorScheme
        PageBackground.DARK -> DarkColorScheme
    }
    MaterialTheme(colorScheme = colorScheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        CompositionLocalProvider(
            LocalContentColor provides colorScheme.onBackground,
            LocalAppBackground provides appBackground,
            content = content,
        )
        if (background != PageBackground.DEFAULT) {
            StatusBarIcons(light = background == PageBackground.DARK)
        }
    }
}

/** The app theme's background, so the "Default" swatch shows it even while sepia/dark is on. */
private val LocalAppBackground = staticCompositionLocalOf { Color.Unspecified }

/** Light or dark status-bar icons to match a page background that differs from the app theme. */
@Composable
private fun StatusBarIcons(light: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, light) {
        val window = view.context.findActivity()?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        val previous = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = !light
        onDispose { controller.isAppearanceLightStatusBars = previous }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Keeps the display awake while this is in the composition and [enabled] is true. */
@Composable
fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderOptionsSheet(
    settings: ReaderSettings,
    onScrollModeChange: (ReaderScrollMode) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onPageBackgroundChange: (PageBackground) -> Unit,
    onDismiss: () -> Unit,
    format: StoryFormat = StoryFormat.COMIC,
    onTextSizeChange: (ReaderTextSize) -> Unit = {},
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        ReaderOptionsContent(
            settings = settings,
            onScrollModeChange = onScrollModeChange,
            onKeepScreenOnChange = onKeepScreenOnChange,
            onPageBackgroundChange = onPageBackgroundChange,
            format = format,
            onTextSizeChange = onTextSizeChange,
        )
    }
}

@Composable
private fun ReaderOptionsContent(
    settings: ReaderSettings,
    onScrollModeChange: (ReaderScrollMode) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onPageBackgroundChange: (PageBackground) -> Unit,
    format: StoryFormat = StoryFormat.COMIC,
    onTextSizeChange: (ReaderTextSize) -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.reader_options_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )

        // ---- Text size (Read tab) ----
        if (format == StoryFormat.TEXT) {
            OptionLabel(stringResource(R.string.reader_text_size))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ReaderTextSize.entries.forEachIndexed { i, size ->
                    val label = stringResource(size.labelRes)
                    SegmentedButton(
                        selected = settings.textSize == size,
                        onClick = { onTextSizeChange(size) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = ReaderTextSize.entries.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        icon = {},
                        modifier = Modifier.semantics { contentDescription = label },
                    ) {
                        // A letter at the real size shows the choice at a glance.
                        Text("అ", fontSize = size.fontSizeSp.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ---- Scroll mode (comic only) ----
        if (format == StoryFormat.COMIC) OptionLabel(stringResource(R.string.reader_scroll))
        if (format == StoryFormat.COMIC) SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ScrollOption.entries.forEachIndexed { i, option ->
                SegmentedButton(
                    selected = settings.scrollMode == option.mode,
                    onClick = { onScrollModeChange(option.mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = ScrollOption.entries.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) { Text(stringResource(option.titleRes)) }
            }
        }

        // ---- Page background ----
        OptionLabel(stringResource(R.string.reader_background))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            BackgroundOption.entries.forEach { option ->
                BackgroundSwatch(
                    option = option,
                    selected = settings.pageBackground == option.background,
                    onClick = { onPageBackgroundChange(option.background) },
                )
            }
        }

        // ---- Keep screen on ----
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // The whole row is one switch for TalkBack and touch.
                .toggleable(
                    value = settings.keepScreenOn,
                    onValueChange = onKeepScreenOnChange,
                    role = Role.Switch,
                )
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.reader_keep_screen_on), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(R.string.reader_keep_screen_on_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            VegamSwitch(checked = settings.keepScreenOn, onCheckedChange = null)
        }
    }
}

private val ReaderTextSize.labelRes: Int
    get() = when (this) {
        ReaderTextSize.SMALL -> R.string.reader_text_size_small
        ReaderTextSize.MEDIUM -> R.string.reader_text_size_medium
        ReaderTextSize.LARGE -> R.string.reader_text_size_large
        ReaderTextSize.EXTRA_LARGE -> R.string.reader_text_size_extra_large
    }

private enum class ScrollOption(val mode: ReaderScrollMode, @StringRes val titleRes: Int) {
    PAGED(ReaderScrollMode.PAGED, R.string.reader_scroll_paged),
    VERTICAL(ReaderScrollMode.VERTICAL, R.string.reader_scroll_vertical),
}

private enum class BackgroundOption(val background: PageBackground, @StringRes val titleRes: Int) {
    DEFAULT(PageBackground.DEFAULT, R.string.reader_background_default),
    SEPIA(PageBackground.SEPIA, R.string.reader_background_sepia),
    DARK(PageBackground.DARK, R.string.reader_background_dark),
}

@Composable
private fun OptionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun BackgroundSwatch(option: BackgroundOption, selected: Boolean, onClick: () -> Unit) {
    // "Default" previews the app's own background (which may be light or dark).
    val swatch = when (option.background) {
        PageBackground.DEFAULT -> LocalAppBackground.current.takeOrElse { MaterialTheme.colorScheme.background }
        PageBackground.SEPIA -> SepiaColorScheme.background
        PageBackground.DARK -> DarkColorScheme.background
    }
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(swatch)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_circle),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(option.titleRes),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified,
        )
    }
}

// ---------------- Previews ----------------

@ComponentPreviews
@Composable
private fun ReaderOptionsPreview() = PreviewTheme {
    ReaderOptionsContent(
        settings = ReaderSettings(scrollMode = ReaderScrollMode.VERTICAL, pageBackground = PageBackground.SEPIA),
        onScrollModeChange = {}, onKeepScreenOnChange = {}, onPageBackgroundChange = {},
    )
}
