package com.vegamstories.app.presentation.reader

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vegamstories.app.R
import com.vegamstories.app.domain.model.Language
import com.vegamstories.app.domain.model.ReaderTextSize
import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.domain.model.StoryFormat
import com.vegamstories.app.domain.model.StoryText
import com.vegamstories.app.presentation.preview.ComponentPreviews
import com.vegamstories.app.presentation.preview.PreviewData
import com.vegamstories.app.presentation.preview.PreviewTheme
import kotlinx.coroutines.flow.distinctUntilChanged

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
 * The Read tab: the story as text, one paragraph after another, then the like section.
 * Reports the last paragraph on screen as progress; reaching the end finishes the story.
 */
@Composable
fun TextReader(
    story: Story,
    text: StoryText,
    textSize: ReaderTextSize,
    startFraction: Float,
    onProgress: (paragraphIndex: Int, paragraphCount: Int) -> Unit,
    endContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paragraphs = text.paragraphs
    val fontSize = textSize.fontSizeSp.sp
    val currentOnProgress by rememberUpdatedState(onProgress)

    // A fresh scroll position per language: the two texts don't line up paragraph by paragraph.
    key(text.languageCode) {
        val start = (startFraction * paragraphs.size).toInt().coerceIn(0, paragraphs.lastIndex.coerceAtLeast(0))
        // Item 0 is the title, so paragraph i is item i + 1.
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = if (start > 0) start + 1 else 0)

        LaunchedEffect(listState, paragraphs.size) {
            snapshotFlow { listState.lastParagraphOnScreen(paragraphs.size) }
                .distinctUntilChanged()
                .collect { if (it >= 0) currentOnProgress(it, paragraphs.size) }
        }

        LazyColumn(
            state = listState,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(fontSize.value.dp * 0.9f),
        ) {
            item(key = "title") {
                Text(
                    text = story.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            itemsIndexed(paragraphs) { _, paragraph ->
                Text(
                    text = paragraph,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = fontSize,
                        // Telugu letters stack vowel signs above and below: give lines room.
                        lineHeight = fontSize * 1.6f,
                    ),
                )
            }
            item(key = "end") { endContent() }
        }
    }
}

/**
 * Index of the last paragraph that is on screen, or the last paragraph once the end of the text
 * (the like section) shows, so finishing the text finishes the story. -1 while the title fills the screen.
 */
private fun LazyListState.lastParagraphOnScreen(paragraphCount: Int): Int {
    val visible = layoutInfo.visibleItemsInfo
    if (visible.isEmpty() || paragraphCount == 0) return -1
    val lastItem = visible.last().index
    return (lastItem - 1).coerceAtMost(paragraphCount - 1)
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

@ComponentPreviews
@Composable
private fun TextReaderPreview() = PreviewTheme {
    TextReader(
        story = PreviewData.story(0),
        text = StoryText(
            "te",
            "ఒకప్పుడు ఒక చిన్న ఊరిలో ఒక నిజాయితీ గల కట్టెలు కొట్టేవాడు ఉండేవాడు.\n\n" +
                "ఒక రోజు అతని గొడ్డలి నదిలో పడిపోయింది.\n\nనీతి: నిజాయితీయే గొప్ప బహుమతి.",
        ),
        textSize = ReaderTextSize.MEDIUM,
        startFraction = 0f,
        onProgress = { _, _ -> },
        endContent = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.the_end), modifier = Modifier.width(80.dp).padding(8.dp))
            }
        },
        modifier = Modifier.height(600.dp),
    )
}
