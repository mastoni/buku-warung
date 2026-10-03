package id.skmnetwork.bukuwarung.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import id.skmnetwork.bukuwarung.ui.components.AppCard
import id.skmnetwork.bukuwarung.ui.components.AppLoadingState
import id.skmnetwork.bukuwarung.ui.components.AppTextField
import id.skmnetwork.bukuwarung.ui.components.PrimaryButton
import id.skmnetwork.bukuwarung.ui.components.SecondaryButton

// =====================================================================================
// Gate 1.1 - preview helpers.
//
// Purpose: every token change in AppTokens.kt must be verifiable in BOTH themes without a device
// or an emulator. That is the whole point of the `uiMode` wrapper below - before this gate there
// was no way to preview the dark theme at all, which is a large part of how 118 light-only colour
// literals accumulated unnoticed.
//
// Usage:
//   @Preview
//   @BukuWarungAppPreviewLight
//   @Composable
//   private fun Something() = BukuWarungThemePreview { ... }
//
// NOTE: BwSystemBars is deliberately NOT called here. It mutates a real Activity window, and a
// preview has none - AppCard/PrimaryButton/etc. must stay preview-safe.
// =====================================================================================

@Preview(name = "Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
annotation class BukuWarungAppPreviewBoth

@Preview(name = "Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
annotation class BukuWarungAppPreviewLight

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
annotation class BukuWarungAppPreviewDark

/**
 * Wraps [content] in the real [BukuWarungTheme] for the ambient preview mode.
 *
 * [isSystemInDarkTheme] reads `Configuration.UI_MODE_NIGHT_*`, so the `uiMode` on the `@Preview`
 * annotation selects the scheme - no extra plumbing needed.
 */
@Composable
fun BukuWarungThemePreview(content: @Composable () -> Unit) {
    BukuWarungTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            content()
        }
    }
}

// -------------------------------------------------------------------------------------
// Foundation smoke surface: the four things the gate must prove render correctly in both themes.
// -------------------------------------------------------------------------------------

@Preview(name = "Foundation - Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
annotation class PreviewFoundationLight

@Preview(name = "Foundation - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
annotation class PreviewFoundationDark

@Composable
private fun FoundationSurface() {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
        Text("Buku Warung", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Kartu di atas background",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        AppCard {
            Column(
                modifier = Modifier.padding(AppSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                Text("Rupiah 25.000", style = AppTypeScale.MoneySecondary)
                Text(
                    "Saldo tunai",
                    style = AppTypeScale.Helper,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        PrimaryButton(text = "BAYAR", onClick = {})
        SecondaryButton(text = "Batal", onClick = {})

        AppTextField(
            value = "Indomie Goreng",
            onValueChange = {},
            label = "Nama produk"
        )

        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            NavigationBar(modifier = Modifier.weight(1f)) {
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = {},
                    label = { Text("Beranda", style = AppTypeScale.LabelSmall) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = {},
                    icon = {},
                    label = { Text("Laporan", style = AppTypeScale.LabelSmall) }
                )
            }
        }

        AppLoadingState(modifier = Modifier)
    }
}

@PreviewFoundationLight
@Composable
private fun FoundationLightPreview() = BukuWarungThemePreview { FoundationSurface() }

@PreviewFoundationDark
@Composable
private fun FoundationDarkPreview() = BukuWarungThemePreview { FoundationSurface() }
