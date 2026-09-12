package id.skmnetwork.bukuwarung.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = GreenDark,
    tertiary = GreenPrimary
)

@Composable
fun BukuWarungTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}
