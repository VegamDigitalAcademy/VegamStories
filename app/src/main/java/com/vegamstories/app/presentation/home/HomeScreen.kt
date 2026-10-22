package com.vegamstories.app.presentation.home

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.vegamstories.app.domain.model.ContentLanguages
import com.vegamstories.app.domain.model.Language
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vegamstories.app.R
import com.vegamstories.app.domain.model.Category
import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.presentation.components.EmptyState
import com.vegamstories.app.presentation.components.SectionHeader
import com.vegamstories.app.presentation.components.StoryGrid
import com.vegamstories.app.presentation.components.StoryGridSkeleton
import com.vegamstories.app.presentation.components.StoryCover
import com.vegamstories.app.presentation.components.StoryImage
import com.vegamstories.app.presentation.components.VegamLogoMark
import com.vegamstories.app.presentation.components.fullWidthItem
import com.vegamstories.app.presentation.preview.PreviewData
import com.vegamstories.app.presentation.preview.PreviewTheme
import com.vegamstories.app.presentation.preview.ScreenPreviews
import com.vegamstories.app.presentation.preview.TeluguPreview

/** Stateful entry point: wires the ViewModel to the stateless [HomeContent]. */
@Composable
fun HomeScreen(
    onStoryClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onShowMessage: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val refreshFailedMsg = stringResource(R.string.refresh_failed)
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                HomeEvent.RefreshFailed -> onShowMessage(refreshFailedMsg)
            }
        }
    }

    HomeContent(
        state = state,
        onCategorySelected = viewModel::onCategorySelected,
        onRefresh = viewModel::refresh,
        onStoryClick = onStoryClick,
        onSearchClick = onSearchClick,
        onLanguageSelected = viewModel::onLanguageSelected,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    onCategorySelected: (String?) -> Unit,
    onRefresh: () -> Unit,
    onStoryClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onLanguageSelected: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        HomeTopBar(
            languages = state.contentLanguages,
            onSearchClick = onSearchClick,
            onLanguageSelected = onLanguageSelected,
        )
        CategoryTabs(
            categories = state.categories,
            selectedCategoryId = state.selectedCategoryId,
            onCategorySelected = onCategorySelected,
        )
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.stories.isEmpty() && (state.isLoading || state.isRefreshing) -> StoryGridSkeleton()
                state.stories.isEmpty() -> EmptyState(
                    icon = R.drawable.ic_home,
                    title = stringResource(R.string.no_stories_title),
                    message = stringResource(R.string.no_stories_message),
                    action = { TextButton(onClick = onRefresh) { Text(stringResource(R.string.retry)) } },
                )
                else -> StoryGrid(
                    stories = state.stories,
                    onStoryClick = { onStoryClick(it.id) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    state.continueReading?.let { story ->
                        fullWidthItem(key = "continue") {
                            Column {
                                SectionHeader(title = stringResource(R.string.continue_reading))
                                ContinueReadingCard(story = story, onClick = { onStoryClick(story.id) })
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                    fullWidthItem(key = "header") {
                        SectionHeader(
                            title = state.selectedCategory?.name ?: stringResource(R.string.latest_stories),
                            trailing = stringResource(R.string.stories_count, state.stories.size),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    languages: ContentLanguages?,
    onSearchClick: () -> Unit,
    onLanguageSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VegamLogoMark()
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.brand_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSearchClick) {
            Icon(painterResource(R.drawable.ic_search), contentDescription = stringResource(R.string.cd_search))
        }
        // Only when stories exist in more than one language.
        if (languages != null && languages.all.size > 1) {
            LanguageButton(languages = languages, onLanguageSelected = onLanguageSelected)
        }
    }
}

/**
 * The story language: "తెలుగు ▾". Opens the server's languages, each in its own script; Home,
 * Search, the reader and the widget then show stories in the chosen one.
 */
@Composable
private fun LanguageButton(languages: ContentLanguages, onLanguageSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = stringResource(R.string.cd_story_language)
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = "$label: ${languages.selectedLanguage?.nativeName.orEmpty()}" },
        ) {
            Icon(painterResource(R.drawable.ic_language), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = languages.selectedLanguage?.nativeName ?: languages.selected.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            languages.all.forEach { language ->
                val selected = language.code == languages.selected
                DropdownMenuItem(
                    text = {
                        Text(
                            text = language.nativeName,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    trailingIcon = {
                        if (selected) {
                            Icon(
                                painterResource(R.drawable.ic_check_circle),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        if (!selected) onLanguageSelected(language.code)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryTabs(
    categories: List<Category>,
    selectedCategoryId: String?,
    onCategorySelected: (String?) -> Unit,
) {
    // Index 0 is "ALL"; categories follow.
    val selectedIndex = if (selectedCategoryId == null) {
        0
    } else {
        (categories.indexOfFirst { it.id == selectedCategoryId } + 1).coerceAtLeast(0)
    }
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 8.dp,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Tab(
            selected = selectedIndex == 0,
            onClick = { onCategorySelected(null) },
            text = { TabLabel(stringResource(R.string.tab_all), selectedIndex == 0) },
        )
        categories.forEachIndexed { i, category ->
            val selected = selectedIndex == i + 1
            Tab(
                selected = selected,
                onClick = { onCategorySelected(category.id) },
                text = { TabLabel(category.name, selected) },
            )
        }
    }
}

@Composable
private fun TabLabel(text: String, selected: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

@Composable
private fun ContinueReadingCard(story: Story, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            StoryCover(
                story = story,
                contentDescription = story.title,
                modifier = Modifier
                    .size(width = 64.dp, height = 80.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.episode_category, story.episodeNumber, story.categoryName),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = story.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { story.progress?.fraction ?: 0f },
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline,
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.continue_action),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ---------------- Previews ----------------

@ScreenPreviews
@TeluguPreview
@Composable
private fun HomeAllPreview() = PreviewTheme {
    HomeContent(
        state = HomeUiState(
            categories = PreviewData.categories,
            stories = PreviewData.stories,
            continueReading = PreviewData.continueReading,
            isLoading = false,
            contentLanguages = ContentLanguages(
                all = listOf(Language("te", "Telugu", "తెలుగు"), Language("en", "English", "English"), Language("or", "Odia", "ଓଡ଼ିଆ")),
                selected = "te",
            ),
        ),
        onCategorySelected = {}, onRefresh = {}, onStoryClick = {},
        onSearchClick = {}, onLanguageSelected = {},
    )
}

@ScreenPreviews
@Composable
private fun HomeCategoryPreview() = PreviewTheme {
    HomeContent(
        state = HomeUiState(
            categories = PreviewData.categories,
            selectedCategoryId = PreviewData.categories.first().id,
            stories = PreviewData.stories.take(4),
            isLoading = false,
        ),
        onCategorySelected = {}, onRefresh = {}, onStoryClick = {},
        onSearchClick = {}, onLanguageSelected = {},
    )
}

@ScreenPreviews
@Composable
private fun HomeLoadingPreview() = PreviewTheme {
    HomeContent(
        state = HomeUiState(isLoading = true),
        onCategorySelected = {}, onRefresh = {}, onStoryClick = {},
        onSearchClick = {}, onLanguageSelected = {},
    )
}

@ScreenPreviews
@Composable
private fun HomeEmptyPreview() = PreviewTheme {
    HomeContent(
        state = HomeUiState(categories = PreviewData.categories, isLoading = false),
        onCategorySelected = {}, onRefresh = {}, onStoryClick = {},
        onSearchClick = {}, onLanguageSelected = {},
    )
}
