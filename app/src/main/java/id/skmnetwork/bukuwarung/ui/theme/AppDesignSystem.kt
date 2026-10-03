package id.skmnetwork.bukuwarung.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Gate 1.1 - the app-level token accessors.
 *
 * ## What changed and why
 *
 * This object's public surface is deliberately UNCHANGED. Every member below has the same name and
 * the same type as before this gate, so none of the ~45 screen files needed editing. What changed
 * is where the values come from:
 *
 *   BEFORE: `if (MaterialTheme.colorScheme.background == Color(0xFF111827)) dark else light`
 *   AFTER:  `if (BwTheme.isDark) dark else light`, where `isDark` is the app's own resolved theme,
 *           published by BukuWarungTheme via LocalIsDarkTheme.
 *
 * The old form compared a live colour against a hex literal, which meant these tokens returned the
 * LIGHT branch whenever `DarkColors.background` differed from #111827 for any reason at all - a new
 * theme, a Material3 default shift, a test harness - with no failure, just wrong colours.
 *
 * The one type change is [RedExpense], which was a plain `val Color` and is now a
 * `@Composable get()`. That is what makes it dark-safe. It is source-compatible: all of its call
 * sites are already inside composables or are @Composable default arguments, and
 * `PrimaryButton(containerColor = AppColors.GreenPrimary)` already relied on exactly that pattern
 * before this gate.
 */
object AppColors {
    /** `colorScheme.primary`. The app's green. Foreground on it is `colorScheme.onPrimary`. */
    val GreenPrimary: Color
        @Composable @ReadOnlyComposable get() = MaterialThemeColorScheme.primary

    /**
     * A deeper green used for headings, active nav labels and icon tints.
     *
     * Light #087A43 on `GreenLight` #DCFCE7 measures 4.93:1 and dark #22C55E on #163A2A measures
     * 5.51:1, so it is legible as an icon/label colour on a primaryContainer chip in both themes.
     */
    val GreenDark: Color
        @Composable @ReadOnlyComposable get() = if (BwTheme.isDark) BrandGreenDark else BrandGreenDeep

    /** `colorScheme.primaryContainer` - the pale green fill. */
    val GreenLight: Color
        @Composable @ReadOnlyComposable get() = MaterialThemeColorScheme.primaryContainer

    /** `colorScheme.background` - the sunken page background. */
    val BackgroundLight: Color
        @Composable @ReadOnlyComposable get() = MaterialThemeColorScheme.background

    /** `colorScheme.surfaceVariant` - muted fills, chips, wells. */
    val SurfaceGray: Color
        @Composable @ReadOnlyComposable get() = MaterialThemeColorScheme.surfaceVariant

    /** `colorScheme.onSurface` - body and heading text. */
    val TextPrimary: Color
        @Composable @ReadOnlyComposable get() = MaterialThemeColorScheme.onSurface

    /** `colorScheme.onSurfaceVariant` - secondary text. */
    val TextSecondary: Color
        @Composable @ReadOnlyComposable get() = MaterialThemeColorScheme.onSurfaceVariant

    /**
     * Destructive / expense / unpaid colour.
     *
     * Was a fixed `Color(0xFFE53935)` with no dark variant, measuring 3.47:1 on the dark surface
     * (audit P0 #8). Now `colorScheme.error`, which is 4.98:1 on white in light and 6.45:1 on the
     * dark surface.
     */
    val RedExpense: Color
        @Composable @ReadOnlyComposable get() = MaterialThemeColorScheme.error

    /** Cash-in container fill. */
    val BlueCash: Color
        @Composable @ReadOnlyComposable get() = Semantic.cashContainer

    /**
     * Attention foreground.
     *
     * Was returning a pale CONTAINER colour (#FFF1DD) from a call site that used it as an ICON TINT,
     * which measured 1.05:1 on the light background - an invisible icon. It is now a real foreground
     * (light #B45309, 5.02:1; dark #F59E0B, 6.83:1). `warningContainer` remains available for fills.
     */
    val OrangeWarning: Color
        @Composable @ReadOnlyComposable get() = Semantic.warning

    /** `colorScheme.outline` - hairlines and field borders. */
    val CardBorder: Color
        @Composable @ReadOnlyComposable get() = MaterialThemeColorScheme.outline

    /**
     * The hairline that separates a CARD from its background.
     *
     * Added for audit P0 #9. `outline` is #E2E8F0, which is too faint to define a card edge on a
     * white surface; this is the slightly stronger step reserved for card boundaries only.
     */
    val CardBorderStrong: Color
        @Composable @ReadOnlyComposable get() = Semantic.cardBorder

    /** `success` - settled, paid, connected, in stock. */
    val Success: Color
        @Composable @ReadOnlyComposable get() = Semantic.success

    /** `successContainer` - settled badge / banner fill. */
    val SuccessContainer: Color
        @Composable @ReadOnlyComposable get() = Semantic.successContainer

    /** `onSuccessContainer` - text on [SuccessContainer]. */
    val OnSuccessContainer: Color
        @Composable @ReadOnlyComposable get() = Semantic.onSuccessContainer

    /** `warningContainer` - attention badge / banner fill. */
    val WarningContainer: Color
        @Composable @ReadOnlyComposable get() = Semantic.warningContainer

    /** `onWarningContainer` - text on [WarningContainer]. */
    val OnWarningContainer: Color
        @Composable @ReadOnlyComposable get() = Semantic.onWarningContainer

    /** `info` - neutral informational foreground (QRIS, cash flows). */
    val Info: Color
        @Composable @ReadOnlyComposable get() = Semantic.info

    /** `infoContainer` - informational fill. */
    val InfoContainer: Color
        @Composable @ReadOnlyComposable get() = Semantic.infoContainer

    /** `onInfoContainer` - text on [InfoContainer]. */
    val OnInfoContainer: Color
        @Composable @ReadOnlyComposable get() = Semantic.onInfoContainer

    /** `errorContainer` - error badge / banner fill. */
    val ErrorContainer: Color
        @Composable @ReadOnlyComposable get() = MaterialThemeColorScheme.errorContainer

    /** `onErrorContainer` - text on [ErrorContainer]. */
    val OnErrorContainer: Color
        @Composable @ReadOnlyComposable get() = MaterialThemeColorScheme.onErrorContainer

    /** `onPrimary` - the only correct foreground for [GreenPrimary]. Never `Color.White`. */
    val OnPrimary: Color
        @Composable @ReadOnlyComposable get() = MaterialThemeColorScheme.onPrimary

    /** `onSurface` - foreground for `colorScheme.primary`-tinted surfaces. Alias of [TextPrimary]. */
    val OnSurface: Color
        @Composable @ReadOnlyComposable get() = MaterialThemeColorScheme.onSurface

    private val MaterialThemeColorScheme
        @Composable @ReadOnlyComposable get() = androidx.compose.material3.MaterialTheme.colorScheme

    private val Semantic
        @Composable @ReadOnlyComposable get() = LocalAppSemanticColors.current
}

object AppShapes {
    val CardShape = RoundedCornerShape(16.dp)
    val ButtonShape = RoundedCornerShape(16.dp)
    val TextFieldShape = RoundedCornerShape(12.dp)
    val ChipShape = RoundedCornerShape(20.dp)
    val PillShape = RoundedCornerShape(24.dp)

    /**
     * Gate 1.2: the status-badge radius.
     *
     * The audit found badges at 4, 6, 10 and 12dp - four radii for one pill, and the only shape in
     * the app with no token behind it. 8dp is the audit's recommended value and matches M3's
     * `extraSmall`, so a badge no longer reads as a smaller cousin of a chip.
     */
    val BadgeShape = RoundedCornerShape(8.dp)
}

object AppSpacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
}

object AppResponsive {
    val ContentMaxWidth = 640.dp
    val ExpandedContentMaxWidth = 720.dp
    val TabletHorizontalPadding = 24.dp
    val DialogMaxWidth = 560.dp
    val DialogMaxHeight = 520.dp
}
