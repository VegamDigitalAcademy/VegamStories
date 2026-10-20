package com.vegam.myapplication.presentation.info

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.vegam.myapplication.presentation.components.BackTopBar
import com.vegam.myapplication.presentation.components.VegamLogoMark
import com.vegam.myapplication.presentation.navigation.InfoPage
import com.vegam.myapplication.presentation.preview.PreviewTheme
import com.vegam.myapplication.presentation.preview.ScreenPreviews

/** Profile, About, Privacy and Terms — placeholder text until real content exists. */
@Composable
fun InfoScreen(page: InfoPage, onBack: () -> Unit) {
    Scaffold(
        topBar = { BackTopBar(title = stringResource(page.titleRes), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            VegamLogoMark(size = 48)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(page.bodyRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ---------------- Previews ----------------

/** Renders one preview per menu page (Profile, About, Privacy, Terms). */
private class InfoPageProvider : PreviewParameterProvider<InfoPage> {
    override val values = InfoPage.entries.asSequence()
}

@ScreenPreviews
@Composable
private fun InfoPreview(@PreviewParameter(InfoPageProvider::class) page: InfoPage) = PreviewTheme {
    InfoScreen(page = page, onBack = {})
}
