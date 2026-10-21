package com.vegam.myapplication.presentation.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vegam.myapplication.R
import com.vegam.myapplication.presentation.common.StoryListUiState
import com.vegam.myapplication.presentation.components.EmptyState
import com.vegam.myapplication.presentation.components.StoryGrid
import com.vegam.myapplication.presentation.preview.PreviewData
import com.vegam.myapplication.presentation.preview.PreviewTheme
import com.vegam.myapplication.presentation.preview.ScreenPreviews

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onStoryClick: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    SearchContent(
        query = query,
        results = results,
        onQueryChange = viewModel::onQueryChange,
        onBack = onBack,
        onStoryClick = onStoryClick,
        focusRequester = focusRequester,
    )
}

@Composable
fun SearchContent(
    query: String,
    results: StoryListUiState,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onStoryClick: (String) -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.cd_back))
            }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.cd_clear))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }

        when {
            query.isBlank() -> EmptyState(
                icon = R.drawable.ic_search,
                title = stringResource(R.string.search_hint),
                message = stringResource(R.string.search_prompt),
            )
            results.stories.isEmpty() -> EmptyState(
                icon = R.drawable.ic_search,
                title = stringResource(R.string.search_no_results, query.trim()),
                message = stringResource(R.string.search_prompt),
            )
            else -> StoryGrid(
                stories = results.stories,
                onStoryClick = { onStoryClick(it.id) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ---------------- Previews ----------------

@ScreenPreviews
@Composable
private fun SearchResultsPreview() = PreviewTheme {
    SearchContent(
        query = "కథ",
        results = StoryListUiState(isLoading = false, stories = PreviewData.stories.take(4)),
        onQueryChange = {}, onBack = {}, onStoryClick = {},
    )
}

@ScreenPreviews
@Composable
private fun SearchIdlePreview() = PreviewTheme {
    SearchContent(
        query = "",
        results = StoryListUiState(isLoading = false),
        onQueryChange = {}, onBack = {}, onStoryClick = {},
    )
}

@ScreenPreviews
@Composable
private fun SearchNoResultsPreview() = PreviewTheme {
    SearchContent(
        query = "dragon",
        results = StoryListUiState(isLoading = false),
        onQueryChange = {}, onBack = {}, onStoryClick = {},
    )
}
