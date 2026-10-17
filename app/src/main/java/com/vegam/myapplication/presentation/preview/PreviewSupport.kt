package com.vegam.myapplication.presentation.preview

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.vegam.myapplication.ui.theme.MyApplicationTheme

/** Full phone-sized screen, light + dark. Use on screen-level previews. */
@Preview(name = "Light", showBackground = true, device = "spec:width=411dp,height=891dp")
@Preview(
    name = "Dark",
    showBackground = true,
    device = "spec:width=411dp,height=891dp",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
annotation class ScreenPreviews

/** Content-sized, light + dark. Use on component previews. */
@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
annotation class ComponentPreviews

/** Wraps preview content in the app theme on a themed background. */
@Composable
fun PreviewTheme(content: @Composable () -> Unit) {
    MyApplicationTheme {
        Surface(color = MaterialTheme.colorScheme.background) { content() }
    }
}
