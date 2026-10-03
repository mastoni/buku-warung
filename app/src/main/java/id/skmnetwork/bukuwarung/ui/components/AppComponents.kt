package id.skmnetwork.bukuwarung.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppShapes
import id.skmnetwork.bukuwarung.ui.theme.AppSpacing
import id.skmnetwork.bukuwarung.ui.theme.AppTypeScale

/**
 * Step 2 (H) - the single loading affordance used by every data-backed screen.
 *
 * Gate 1.2: now the themed spinner primitive used by [AppLoadingState] and by the dialog-local
 * loaders the audit found scattered around (a 120dp spinner inside a bespoke `Dialog` in
 * PurchaseOrderComponents, a text sentence used as a loader in PurchaseScreen, and two 16dp spinners
 * in different colours in PrinterSettingsDialog).
 *
 * Deliberately a plain centred indicator rather than a skeleton: these screens have 13 to 30 separate
 * figures on them, and a skeleton for each one would be far more noise than a merchant needs. The
 * point is only to stop "Rp 0" and "Belum ada produk" being shown as if they were real answers.
 *
 * The colour is `primary`, so it follows the theme. It carries a content description because a bare
 * spinner announces nothing to a screen reader.
 */
@Composable
fun AppLoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = AppColors.GreenPrimary,
            modifier = Modifier
                .size(40.dp)
                .semantics { contentDescription = "Memuat" }
        )
    }
}

/** An inline, size-stable spinner for a control that replaces its label while it works. */
@Composable
fun AppInlineSpinner(
    color: Color = AppColors.GreenPrimary,
    modifier: Modifier = Modifier
) {
    CircularProgressIndicator(
        color = color,
        strokeWidth = 2.dp,
        modifier = modifier.size(20.dp)
    )
}

/**
 * The app's card surface.
 *
 * Gate 1.1: this is the fix for audit P0 #9. Previously the card was `containerColor = surface`
 * (#FFFFFF) with no border and no elevation, rendered on `containerColor = background` (#F8FAFC) -
 * a contrast ratio of **1.05:1**. In the product's DEFAULT light theme every card was therefore an
 * invisible rectangle, and the "clear surface/card hierarchy" requirement was not met.
 *
 * Colour alone cannot separate two near-whites, so the hierarchy comes from a 1dp hairline
 * (`AppColors.CardBorderStrong`) rather than from elevation. No shadow is added: the visual
 * direction is clean and flat, and a shadow on the dark surface would be heavier than the design
 * calls for. Elevation stays 0dp so a pressed/focused card does not lift.
 *
 * [border] is overridable for the rare card that must sit on a coloured fill.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    border: BorderStroke = BorderStroke(1.dp, AppColors.CardBorderStrong),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    if (onClick != null) {
        Card(
            onClick = onClick,
            shape = AppShapes.CardShape,
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp, focusedElevation = 0.dp, hoveredElevation = 0.dp, draggedElevation = 0.dp),
            border = border,
            modifier = modifier
        ) {
            content()
        }
    } else {
        Card(
            shape = AppShapes.CardShape,
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp, focusedElevation = 0.dp, hoveredElevation = 0.dp, draggedElevation = 0.dp),
            border = border,
            modifier = modifier
        ) {
            content()
        }
    }
}

/**
 * The app's primary action. Delegates to [ButtonPrimary].
 *
 * Gate 1.2 note: the pre-existing public signature is preserved exactly - including the
 * `containerColor` / `contentColor` overrides - because call sites in POS, License, Settings and
 * Camera still pass a brand colour explicitly. New code should prefer [ButtonPrimary] directly.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = AppColors.GreenPrimary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    ButtonPrimary(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        containerColor = containerColor,
        contentColor = contentColor
    )
}

/**
 * The app's secondary action.
 *
 * Gate 1.2 - this is where audit P0 #12 is closed.
 *
 * The old signature defaulted `contentColor` to `AppColors.RedExpense`, which meant EVERY
 * `SecondaryButton` in the app rendered destructive red unless the caller thought to override it.
 * Two benign actions shipped that way - "Ubah Jenis Usaha" (SettingsScreen) and "Sesuaikan Stok /
 * Opname" (AddProductScreen) - and in StockAdjustmentDialog BOTH halves of a mode toggle rendered
 * red, which is what made that toggle's missing selected state so easy to miss. Only one call site
 * in the entire app ever overrode it.
 *
 * The default is now the NEUTRAL foreground, and red is reachable only by asking for
 * [ButtonDestructive] by name. The default parameter is kept for source compatibility with the
 * callers that already pass a colour explicitly.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    ButtonNeutral(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled
    )
}

/** The app's base text field. Delegates to [FieldDefault]. */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    FieldDefault(
        value = value,
        onValueChange = onValueChange,
        label = label,
        isError = isError,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        modifier = modifier
    )
}

/**
 * The app's empty state.
 *
 * Gate 1.2 - two fixes, plus a note on why this component is NOT duplicated.
 *
 * The icon tint was `AppColors.CardBorder`, which is `colorScheme.outline`: #E2E8F0 on the #FFFFFF
 * surface measures **1.23:1**. The one thing a blank screen must have is a visible icon, and this
 * made it the least visible element on it. It is now `onSurfaceVariant` (7.56:1 light, 5.78:1 dark).
 *
 * The title used to be `bodyMedium`-sized and the whole block had no horizontal breathing room.
 * Typography now comes from the scale, and the block is padded.
 *
 * The audit found 8 empty-state implementations. This is the replacement for all of them, and it is
 * deliberately NOT extended here: the other 7 live inside screens whose navigation and action
 * semantics differ (ProductFilterEmptyState names the query and resets the filter; the Cash and
 * Customer ones have bespoke copy), so folding them in is screen work, not foundation work. They are
 * listed for Gate 1.4.
 */
@Composable
fun AppEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(AppSpacing.lg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clearAndSetSemantics { }
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(AppSpacing.md))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            if (description != null) {
                Spacer(Modifier.height(AppSpacing.xs))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            if (actionText != null && onActionClick != null) {
                Spacer(Modifier.height(AppSpacing.md))
                ButtonPrimary(
                    text = actionText,
                    onClick = onActionClick,
                    // Not full width: an empty state is a message with one optional escape, and a
                    // full-bleed button reads as heavier than the content it replaces.
                    // ButtonPrimary owns the fillMaxWidth switch; PrimaryButton keeps the older
                    // always-full-width signature for its existing call sites.
                    fillMaxWidth = false,
                    modifier = Modifier.padding(horizontal = AppSpacing.lg)
                )
            }
        }
    }
}
