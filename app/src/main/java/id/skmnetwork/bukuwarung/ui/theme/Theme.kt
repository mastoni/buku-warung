package id.skmnetwork.bukuwarung.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

// =====================================================================================
// Gate 1.1 - Theme
//
// Gate 1.1 audit findings addressed here:
//   P0 #1  no values-night, system bars hardcoded white, no edge-to-edge control
//   P0 #6  dark mode detected by comparing colorScheme.background to a hex literal
//   P1 #19 MaterialTheme received colorScheme only - no typography, no shapes
//   P1 #20 error / tertiary / errorContainer never specified, so baseline Material3 leaked in
//          (which is why SettingsScreen's warning rendered PINK)
//
// What is deliberately NOT here: any screen layout. Gate 1.1 is plumbing only.
// =====================================================================================

// -------------------------------------------------------------------------------------
// Explicit theme state
//
// This replaces the pre-existing mechanism in AppDesignSystem.kt, which inferred dark mode with
// `MaterialTheme.colorScheme.background == Color(0xFF111827)`. That silently returned the LIGHT
// branch for AppColors.GreenDark, BlueCash and OrangeWarning if DarkColors.background ever
// changed, if a new theme was added, or if Material3 shifted its own default.
//
// The value is carried explicitly and is the single answer to "is this app dark right now?".
// -------------------------------------------------------------------------------------

private val LocalIsDarkThemeValue: ProvidableCompositionLocal<Boolean> =
    compositionLocalOf { false }

/**
 * Whether Buku Warung is currently rendering its dark theme.
 *
 * This is the app's OWN resolved theme, which is not always the system's: the merchant can pick
 * LIGHT / DARK / SYSTEM in Settings, and that choice - not `isSystemInDarkTheme()` - is what the UI
 * actually paints. Anything that needs to branch on the theme must read this, never a colour.
 */
val LocalIsDarkTheme: ProvidableCompositionLocal<Boolean> = LocalIsDarkThemeValue

/** Convenience accessor mirroring [LocalIsDarkTheme] for call sites that prefer a property. */
object BwTheme {
    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalIsDarkThemeValue.current
}

/**
 * warning / info / success, which Material3's `ColorScheme` has no slot for.
 *
 * `error` is deliberately NOT here: it is a real M3 role and lives on `colorScheme.error`, so M3
 * components resolve it (and its `onError`) automatically. Only the roles M3 cannot express are
 * carried by this extension.
 */
private val LocalSemanticColorsValue: ProvidableCompositionLocal<SemanticColors> =
    compositionLocalOf { LightSemanticColors }

val LocalAppSemanticColors: ProvidableCompositionLocal<SemanticColors> = LocalSemanticColorsValue

// -------------------------------------------------------------------------------------
// Colour schemes
//
// Every role the app actually reads is now declared. Nothing falls back to baseline Material3.
//
//   Light background  #F8FAFC   surface #FFFFFF   surfaceVariant #F1F5F9   (unchanged)
//   Dark  background  #111827   surface #1F2937   surfaceVariant #374151   (unchanged)
//
// The lightness relationships are preserved exactly so LIGHT mode looks the same as before this
// gate. The two deliberate changes are both fixes:
//
//   primaryContainer / secondaryContainer / tertiaryContainer in DARK: previously these had no
//   meaningful relationship to the surfaces, and the audit measured a text-on-container figure of
//   2.26:1 once a hardcoded light tint was involved. They now sit between surfaceMuted and surface.
//
//   error: light moves from Material3's default #B3261E to #D32F2F, which is the red the app already
//   uses in 28 places, and dark gets a light red (6.45:1) so the ~30 AppColors.RedExpense call sites
//   become readable without a single screen edit.
// -------------------------------------------------------------------------------------

private val LightColors = lightColorScheme(
    primary = BrandGreenLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCFCE7),
    onPrimaryContainer = Color(0xFF14532D),
    inversePrimary = BrandGreenLight,

    secondary = Color(0xFF22C55E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0FDF4),
    onSecondaryContainer = Color(0xFF166534),

    // M3 has no warning role. The app used colorScheme.tertiary for warnings in at least two places,
    // which rendered baseline-purple / PINK. Teal is the nearest in-family hue that still reads as
    // "attention" without introducing a second brand colour.
    tertiary = Color(0xFF0F766E),                 // 5.47:1 on white
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCCFBF1),
    onTertiaryContainer = Color(0xFF134E4A),      // 8.41:1

    background = SurfaceSunkenLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceMutedLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceTint = BrandGreenLight,
    inverseSurface = Color(0xFF1F2937),
    inverseOnSurface = Color(0xFFF8FAFC),

    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,

    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    scrim = Color(0xFF000000)
)

private val DarkColors = darkColorScheme(
    primary = BrandGreenDark,
    // This is the audit's P0 #2 fix. The value was ALREADY correct here; it was never used,
    // because every primary button hardcoded Color.White - 2.28:1 in this theme. #052E16 measures
    // 6.54:1 on #22C55E.
    onPrimary = Color(0xFF052E16),
    primaryContainer = Color(0xFF163A2A),
    onPrimaryContainer = Color(0xFFDCFCE7),
    inversePrimary = BrandGreenLight,

    secondary = Color(0xFF16A34A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF143823),
    onSecondaryContainer = Color(0xFFBBF7D0),

    tertiary = Color(0xFF2DD4BF),                 // 7.89:1 on the dark surface
    onTertiary = Color(0xFF003731),
    tertiaryContainer = Color(0xFF115E59),
    onTertiaryContainer = Color(0xFF99F6E4),

    background = SurfaceSunkenDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceMutedDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceTint = BrandGreenDark,
    inverseSurface = Color(0xFFF1F5F9),
    inverseOnSurface = Color(0xFF1F2937),

    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,

    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    scrim = Color(0xFF000000)
)

/**
 * Buku Warung's theme.
 *
 * @param darkTheme the app's already-resolved theme choice, supplied by MainActivity from the
 *   merchant's LIGHT / DARK / SYSTEM preference. The default keeps `isSystemInDarkTheme()` so that
 *   Compose previews and the instrumented screenshot tests can call `BukuWarungTheme { }` with no
 *   arguments, exactly as they did before this gate.
 */
@Composable
fun BukuWarungTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

    // remember() on a stable pair: avoids re-providing an equal-but-new instance on every
    // recomposition of a screen that recomposes often (the POS recomposes on every cart change).
    val resolvedSemantic = remember(semanticColors) { semanticColors }

    CompositionLocalProvider(
        LocalIsDarkThemeValue provides darkTheme,
        LocalSemanticColorsValue provides resolvedSemantic
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapesMaterial,
            content = content
        )
    }
}
