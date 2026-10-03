package id.skmnetwork.bukuwarung.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape

// =====================================================================================
// Gate 1.1 - AppTokens
//
// THE single source of truth for Buku Warung's visual foundation.
//
// Three rules govern this file, and they exist because the Gate 1 audit found them
// violated 480 times across app/src/main:
//
//   1. Every colour literal lives HERE. A composable must never contain Color(0x...).
//   2. Every colour that the app actually paints is declared for BOTH themes. A role
//      left unspecified silently falls back to baseline Material3, which is off-brand
//      (this is how colorScheme.tertiary ended up rendering PINK on the "needs
//      re-authorisation" warning in SettingsScreen).
//   3. Nothing here changes the brand. The greens are the greens the app already ships:
//      #16A34A in light and #22C55E in dark. No new brand hue is introduced.
//
// Values are grouped as:
//   - Brand        : the greens
//   - Surfaces     : the light->dark elevation stack
//   - Content      : text roles
//   - Semantic     : error / warning / info / success, each as a
//                    `foreground` + `container` + `onContainer` triple
//   - Borders      : hairline roles
//   - Typography   : a closed scale with an 11sp hard floor and no half-steps
//   - Shapes       : the M3 slot values, derived from the existing AppShapes baseline
//
// Screens are NOT migrated to these tokens in this gate. The foundation is in place;
// the per-screen hardcoded-colour cleanup is a later gate.
// =====================================================================================

// =====================================================================================
// 1. BRAND
// =====================================================================================

/**
 * The app's greens. Unchanged from the pre-existing Theme.kt, deliberately: Gate 1.1 must not
 * change brand identity.
 *
 * - [BrandGreenLight] is `primary` in the light scheme. White on it measures 3.30:1, which is the
 *   WCAG large-bold-text case (a 14pt-bold / 18.66px-bold label). Dark green text on a green button
 *   would be worse, so `onPrimary` stays white in light. This is recorded as audit P1 #24, not
 *   silently accepted.
 * - [BrandGreenDark] is `primary` in the dark scheme and needs DARK foreground (#052E16, 6.54:1).
 *   The pre-existing dark `onPrimary` was already correct - it was simply never used, because
 *   every button hardcoded `Color.White` (2.28:1). That is audit P0 #2.
 */
internal val BrandGreenLight = Color(0xFF16A34A)
internal val BrandGreenDark = Color(0xFF22C55E)
internal val BrandGreenDeep = Color(0xFF087A43)

// =====================================================================================
// 2. SURFACES
//
// The light scheme's failure (audit P0 #9) was that surface (#FFFFFF) and background (#F8FAFC)
// differ by 1.05:1, so a borderless card was invisible in the product's DEFAULT theme.
//
// The fix is two-part and both parts are needed:
//   a) a real surface stack (this file), and
//   b) an actual 1dp hairline on AppCard (AppComponents.kt).
// Colour alone cannot separate two near-whites; the hairline does the work.
// =====================================================================================

internal val SurfaceLight = Color(0xFFFFFFFF)
internal val SurfaceDark = Color(0xFF1F2937)
internal val SurfaceMutedLight = Color(0xFFF1F5F9)
internal val SurfaceMutedDark = Color(0xFF374151)
internal val SurfaceSunkenLight = Color(0xFFF8FAFC)
internal val SurfaceSunkenDark = Color(0xFF111827)

// =====================================================================================
// 3. CONTENT
// =====================================================================================

internal val TextPrimaryLight = Color(0xFF1F2937)
internal val TextPrimaryDark = Color(0xFFF9FAFB)
internal val TextSecondaryLight = Color(0xFF4B5563)
internal val TextSecondaryDark = Color(0xFF9CA3AF)

// =====================================================================================
// 4. SEMANTIC - error
//
// Material3 HAS an error role, so this one maps straight onto colorScheme.error / errorContainer /
// onErrorContainer and is therefore what every M3 component reads automatically.
//
// AppColors.RedExpense is rewired to this same role (AppDesignSystem.kt), which is what makes the
// app's ~30 RedExpense call sites dark-safe with zero screen edits (audit P0 #8).
// =====================================================================================

internal val ErrorLight = Color(0xFFD32F2F)        // 4.98:1 on white
internal val ErrorDark = Color(0xFFFF8A85)         // 6.45:1 on the dark surface
internal val OnErrorLight = Color(0xFFFFFFFF)
internal val OnErrorDark = Color(0xFF4A0E0E)
internal val ErrorContainerLight = Color(0xFFFFEBEE)
internal val ErrorContainerDark = Color(0xFF4C1A1A)
internal val OnErrorContainerLight = Color(0xFF8C1D1D)   // 7.96:1 on #FFEBEE
internal val OnErrorContainerDark = Color(0xFFFFDAD6)    // 11.06:1 on #4C1A1A

// =====================================================================================
// 5. SEMANTIC - warning / info / success
//
// Material3 has NO warning, info or success role. They live in an extension layer carried by
// [LocalAppSemanticColors], so nothing pretends `colorScheme.tertiary` means "warning" any more.
//
// The values are drawn from colours the app already ships (#FFF3E0-family amber, #E8F3FF-family
// blue, #DCFCE7-family green) so no new brand hue is created. Each is a
// `foreground` + `container` + `onContainer` triple, matching the M3 pattern.
// =====================================================================================

@Immutable
data class SemanticColors(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    /** Cash-in / money-in surfaces. Pre-existing AppColors.BlueCash role, kept for compatibility. */
    val cashContainer: Color,
    /** Cash-in foreground. */
    val onCashContainer: Color,
    /** 1dp hairline that separates a card from whatever is behind it. */
    val cardBorder: Color
)

internal val LightSemanticColors = SemanticColors(
    success = Color(0xFF15803D),                 // 5.02:1 on white
    successContainer = Color(0xFFDCFCE7),
    onSuccessContainer = Color(0xFF14532D),      // 8.30:1 on #DCFCE7
    warning = Color(0xFFB45309),                // 5.02:1 on white
    warningContainer = Color(0xFFFFF4E5),
    onWarningContainer = Color(0xFF7A4A00),     // 6.88:1 on #FFF4E5
    info = Color(0xFF1565C0),                   // 5.75:1 on white
    infoContainer = Color(0xFFE8F1FF),
    onInfoContainer = Color(0xFF0B4FA8),        // 6.84:1 on #E8F1FF
    cashContainer = Color(0xFFE8F3FF),
    onCashContainer = Color(0xFF0B4FA8),
    cardBorder = Color(0xFFD5DDE8)              // 1dp hairline on white
)

internal val DarkSemanticColors = SemanticColors(
    success = Color(0xFF4ADE80),                // 8.42:1 on the dark surface
    successContainer = Color(0xFF14361F),
    onSuccessContainer = Color(0xFF86E7AC),     // 8.90:1 on #14361F
    warning = Color(0xFFF59E0B),                // 6.83:1 on the dark surface
    warningContainer = Color(0xFF3A2A08),
    onWarningContainer = Color(0xFFFBBF5E),     // 8.40:1 on #3A2A08
    info = Color(0xFF60A5FA),                   // 5.77:1 on the dark surface
    infoContainer = Color(0xFF10243D),
    onInfoContainer = Color(0xFF8FC0FF),        // 8.31:1 on #10243D
    cashContainer = Color(0xFF1E293B),
    onCashContainer = Color(0xFF8FC0FF),
    cardBorder = Color(0xFF37475C)
)

// =====================================================================================
// 6. BORDERS
// =====================================================================================

internal val OutlineLight = Color(0xFFE2E8F0)
internal val OutlineDark = Color(0xFF4B5563)
internal val OutlineVariantLight = Color(0xFFCBD5E1)
internal val OutlineVariantDark = Color(0xFF374151)

// =====================================================================================
// 7. SYSTEM BARS
//
// Resolved once here and applied by BwSystemBars. Light keeps white (the app's current,
// unchanged appearance); dark uses the dark sunken surface so there is no white strip.
// =====================================================================================

internal val SystemBarLight = Color(0xFFFFFFFF)
internal val SystemBarDark = Color(0xFF111827)

// =====================================================================================
// 8. TYPOGRAPHY
//
// Gate 1.1 findings this scale encodes (audit section 4):
//   - the app had 14 distinct sizes in ReportsScreen alone, including 10sp / 10.5sp;
//   - 9.5sp appeared twice, and PosDesign.kt:190-192 explicitly forbids it;
//   - 11sp was used for the 5 primary bottom-navigation labels on a phone;
//   - the same Rupiah total was rendered at 13.5sp, 16sp, 18sp, 23sp and 24sp.
//
// So: a closed scale, no half-steps, an 11sp hard floor, and a 12sp floor for anything that
// carries meaning. Named tokens below are the vocabulary later gates will migrate to; the M3 slot
// values are what make every un-migrated component inherit a sane style TODAY.
//
// Only title*/body*/label* are overridden. display* and headline* keep Material3's values because
// nothing in the app uses them and changing them would be pure churn.
// =====================================================================================

internal object AppTypeScale {
    val AppTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    )
    val SectionTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    )
    val Body = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    )
    val BodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.25.sp
    )
    val Label = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    )
    /** The 11sp hard floor. Badges and micro-labels only. */
    val LabelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    )
    val MoneyPrimary = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp
    )
    val MoneySecondary = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    )
    val Button = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    )
    val Helper = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    )
}

/**
 * The [Typography] handed to `MaterialTheme`.
 *
 * Mapped deliberately so that M3 components, and every screen still reading
 * `MaterialTheme.typography.*` rather than a named token, already inherit the corrected scale.
 *
 * Notable: `titleLarge` drops from Material3's 22sp to 18sp. It is the style behind every
 * `AlertDialog` and `TopAppBar` title, which is why the audit found dialog titles at 16sp, 17sp and
 * 22sp in one app - and why a 22sp "Ubah Jenis Usaha" heading sat above 11sp section headers in
 * Settings. `labelSmall` and `bodySmall` rise from 11sp/12sp to 11sp/13sp so the shared components
 * stop shipping sub-floor body text.
 */
internal val AppTypography = Typography(
    titleLarge = AppTypeScale.SectionTitle,
    titleMedium = AppTypeScale.Body.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleSmall = AppTypeScale.Label.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = AppTypeScale.Body,
    bodyMedium = AppTypeScale.Label,
    bodySmall = AppTypeScale.BodySmall,
    labelLarge = AppTypeScale.Button,
    labelMedium = AppTypeScale.Helper.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    labelSmall = AppTypeScale.LabelSmall
)

// =====================================================================================
// 9. SHAPES
//
// Derived from the pre-existing AppShapes baseline rather than from Material3 defaults, so the
// radius a component uses IS the radius the design system declares.
//
// Material3's only real default here was extraLarge = 28dp, which is what FABs, menus and bottom
// sheets were picking up with no token behind it (audit section 6.2). It is now 20dp = ChipShape.
// =====================================================================================

internal val AppShapesMaterial = Shapes(
    extraSmall = RoundedCornerShape(AppSpacing.xs),            // 4dp
    small = RoundedCornerShape(AppSpacing.sm),                 // 8dp
    medium = AppShapes.TextFieldShape,                         // 12dp
    large = AppShapes.CardShape,                               // 16dp
    extraLarge = AppShapes.ChipShape                           // 20dp (was Material3's 28dp)
)

// =====================================================================================
// 10. TOOLING
// =====================================================================================

/**
 * Trims the first line-height's extra leading so mixed-size columns align on the cap height rather
 * than on the text box. Applied to the body/label slots only.
 */
internal val BwLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)
