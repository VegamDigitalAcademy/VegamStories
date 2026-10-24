package com.vegamstories.app.presentation.reader

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vegamstories.app.R
import com.vegamstories.app.domain.model.Language
import com.vegamstories.app.domain.model.ReaderTextSize
import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.domain.model.StoryFormat
import com.vegamstories.app.presentation.preview.ComponentPreviews
import com.vegamstories.app.presentation.preview.PreviewTheme

/** Comic | Read | Watch, for the formats this story has. Not shown for a single-format story. */
@Composable
fun FormatTabs(
    formats: Set<StoryFormat>,
    selected: StoryFormat,
    onSelect: (StoryFormat) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        formats.forEachIndexed { i, format ->
            SegmentedButton(
                selected = format == selected,
                onClick = { onSelect(format) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = formats.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                icon = {
                    Icon(painterResource(format.tabIcon), contentDescription = null, modifier = Modifier.size(16.dp))
                },
            ) { Text(stringResource(format.tabLabel), maxLines = 1) }
        }
    }
}

@get:DrawableRes
private val StoryFormat.tabIcon: Int
    get() = when (this) {
        StoryFormat.COMIC -> R.drawable.ic_format_comic
        StoryFormat.TEXT -> R.drawable.ic_format_text
        StoryFormat.VIDEO -> R.drawable.ic_play
    }

@get:StringRes
private val StoryFormat.tabLabel: Int
    get() = when (this) {
        StoryFormat.COMIC -> R.string.reader_tab_comic
        StoryFormat.TEXT -> R.string.reader_tab_read
        StoryFormat.VIDEO -> R.string.reader_tab_watch
    }

/** Text size in the Read tab. */
val ReaderTextSize.fontSizeSp: Int
    get() = when (this) {
        ReaderTextSize.SMALL -> 16
        ReaderTextSize.MEDIUM -> 19
        ReaderTextSize.LARGE -> 22
        ReaderTextSize.EXTRA_LARGE -> 26
    }

/**
 * One chip per language the story is in, named in its own script (తెలుగు, English, ଓଡ଼ିଆ…).
 * Picking one shows that language's version: title, pages, text and video.
 */
@Composable
fun LanguageChips(
    languages: List<Language>,
    selectedCode: String?,
    onSelect: (languageCode: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        languages.forEach { language ->
            val selected = language.code == selectedCode
            FilterChip(
                selected = selected,
                onClick = { onSelect(language.code) },
                label = { Text(language.nativeName) },
                modifier = Modifier.semantics { role = Role.RadioButton },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

/** The Watch tab: the video (plays in the app when it can), then the like section. */
@Composable
fun WatchTab(story: Story, endContent: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        story.video?.let { video ->
            // runCatching: no browser/YouTube app installed must not crash the reader.
            VideoPage(video = video, onWatch = { runCatching { uriHandler.openUri(video.videoUrl) } })
        }
        endContent()
    }
}

// ---------------- Previews ----------------

@ComponentPreviews
@Composable
private fun FormatTabsPreview() = PreviewTheme {
    FormatTabs(
        formats = setOf(StoryFormat.COMIC, StoryFormat.TEXT, StoryFormat.VIDEO),
        selected = StoryFormat.TEXT,
        onSelect = {},
        modifier = Modifier.padding(16.dp),
    )
}

@ComponentPreviews
@Composable
private fun LanguageChipsPreview() = PreviewTheme {
    LanguageChips(
        languages = listOf(Language("te", "Telugu", "తెలుగు"), Language("en", "English", "English"), Language("or", "Odia", "ଓଡ଼ିଆ")),
        selectedCode = "te",
        onSelect = {},
        modifier = Modifier.padding(16.dp),
    )
}
