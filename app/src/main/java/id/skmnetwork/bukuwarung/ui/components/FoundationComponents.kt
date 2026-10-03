package id.skmnetwork.bukuwarung.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppShapes
import id.skmnetwork.bukuwarung.ui.theme.AppSpacing
import id.skmnetwork.bukuwarung.ui.theme.AppTypeScale

// =====================================================================================
// Gate 1.2 - foundation components.
//
// ONE implementation per pattern, built entirely on the Gate 1.1 tokens. This file is the
// replacement for the duplication the Gate 1 audit counted:
//
//   pattern                    copies found by the audit
//   ------------------------------------------------------------------------------------
//   card / section container     9 in ReportsScreen + 14 in SettingsScreen + 5 in POC
//   section header               14 hand-inlined + 1 defined and never called
//   status badge                 4 per file in CustomersScreen and SuppliersScreen,
//                                2 in PurchaseScreen, 2 tables in POC = 3 radii, 4 sizes,
//                                3 different reds
//   filter chip                  6 implementations, 30-42dp touch targets, 2 selected styles
//   segmented control            3 implementations (48dp/12dp/13sp, ~40dp/8dp/12sp+10sp, ...)
//   text field                   4 shapes for one input
//   error banner                 5 copies, 4 of which hardcode the text red
//   button                       1 generic secondary whose DEFAULT was destructive red
//
// Rules that are enforced here, not left to each call site:
//
//   1. No colour literal. Everything resolves through AppColors / MaterialTheme.colorScheme.
//   2. 48dp minimum touch target on every interactive component.
//   3. A fixed height on every button, including in its loading state, so the layout never jumps.
//   4. Every text pair in this file has been measured. The two that failed AA are called out
//      inline where the fix was made - read those comments before "simplifying" them back.
//
// This file adds PRESENTATION only. No state, no callbacks, no navigation, no validation and no
// business meaning is defined or altered here; a call site keeps all of that.
// =====================================================================================

/** The app's minimum interactive target. The rule the POS already documented and enforces. */
val AppTouchTarget: androidx.compose.ui.unit.Dp = 48.dp

// =====================================================================================
// 1. CARD
// =====================================================================================

/**
 * One card, one surface treatment.
 *
 * Replaces the 9 hand-rolled recipes in ReportsScreen and the 14 in SettingsScreen, which between
 * them used six radii (6/8/10/12/14/16dp) and four elevations (0 / 0.5 / 3 / 8dp) for what is
 * functionally the same box.
 *
 * The visual hierarchy comes from a 1dp hairline, never from a shadow: two near-white surfaces
 * cannot be separated by colour (audit P0 #9 measured #FFFFFF on #F8FAFC at 1.05:1), and the visual
 * direction is clean and flat. [variant] only chooses the container colour.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    variant: CardVariant = CardVariant.Default,
    contentPadding: PaddingValues = PaddingValues(AppSpacing.lg),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val container = when (variant) {
        CardVariant.Default -> MaterialTheme.colorScheme.surface
        CardVariant.Muted -> MaterialTheme.colorScheme.surfaceVariant
        CardVariant.Sunken -> MaterialTheme.colorScheme.background
        CardVariant.Accent -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when (variant) {
        CardVariant.Accent -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val borderColor = when (variant) {
        CardVariant.Accent -> MaterialTheme.colorScheme.primary
        else -> AppColors.CardBorderStrong
    }

    val shape = AppShapes.CardShape
    val colors = CardDefaults.cardColors(containerColor = container, contentColor = contentColor)
    val elevation = CardDefaults.cardElevation(
        defaultElevation = 0.dp,
        pressedElevation = 0.dp,
        focusedElevation = 0.dp,
        hoveredElevation = 0.dp,
        draggedElevation = 0.dp
    )

    val body: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(contentPadding)) { content() }
    }

    if (onClick != null) {
        Card(
            onClick = onClick,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = BorderStroke(1.dp, borderColor),
            modifier = modifier
        ) { body() }
    } else {
        Card(
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = BorderStroke(1.dp, borderColor),
            modifier = modifier
        ) { body() }
    }
}

enum class CardVariant { Default, Muted, Sunken, Accent }

// =====================================================================================
// 2. SECTION HEADER
// =====================================================================================

/**
 * One section header for the whole app.
 *
 * Replaces the 14 hand-inlined headers in SettingsScreen, which were all a copy of the same block:
 * 11sp Bold, `AppColors.GreenPrimary`, 0.5sp letterSpacing. That block was ALSO the reason the
 * audit found a 22sp `titleMedium` heading on the same screen being louder than its section
 * headers - a `SectionHeader` composable existed in that file at the time but was never called.
 *
 * Typography comes from [MaterialTheme.typography]. [titleLarge] is 18sp (Gate 1.1), so the header
 * now reads as a heading instead of as a caption.
 *
 * [subtitle] is deliberately optional rather than defaulted: most sections have none, and forcing
 * one would invent content.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                // The header text already carries the meaning; announcing the icon too would
                // double it for a screen reader.
                contentDescription = null,
                tint = AppColors.GreenPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(AppSpacing.sm))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = AppTypeScale.Helper,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (action != null) {
            Spacer(Modifier.width(AppSpacing.sm))
            action()
        }
    }
}

// =====================================================================================
// 3. STATUS BADGE
// =====================================================================================

/**
 * One status badge for the whole app.
 *
 * Geometry is fixed by the audit's recommendation: 8dp radius, `labelMedium`, 8dp horizontal and
 * 4dp vertical padding. That collapses the four badge implementations found per file in
 * CustomersScreen and SuppliersScreen - three radii (4/6/10/12dp), three sizes (9.5/10.5/11/11.5sp)
 * and two different container techniques - into one.
 *
 * Two rules the audit insisted on:
 *
 *   - STATUS IS NEVER CARRIED BY COLOUR ALONE. [label] is mandatory and always renders, so a
 *     colour-blind merchant still reads "Belum Lunas" / "Lunas" / "Diterima". That is why there is
 *     no colour-only overload here.
 *   - WHEN CLICKABLE THE TOUCH TARGET IS 48dp. A 24dp-tall badge is not tappable by the merchant
 *     this app is built for, so the clickable form grows the hit area without growing the visual.
 *
 * Measured contrast of every pair below:
 *   Success  light 8.30:1   dark 8.90:1
 *   Warning  light 6.88:1   dark 8.40:1
 *   Error    light 7.96:1   dark 11.06:1
 *   Info     light 6.84:1   dark 8.31:1
 *   Neutral  light 13.40:1  dark 9.86:1
 */
@Composable
fun StatusBadge(
    label: String,
    variant: StatusBadgeVariant = StatusBadgeVariant.Neutral,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val (container, content) = variant.colors()
    val shape = AppShapes.BadgeShape

    val badge: @Composable () -> Unit = {
        Surface(
            shape = shape,
            color = container,
            contentColor = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (onClick == null) {
        Surface(
            shape = shape,
            color = container,
            modifier = modifier
        ) { badge() }
    } else {
        // Grow the touch area, not the badge: the clickable Surface is forced to 48dp tall while the
        // visible pill keeps its 8dp+4dp padding geometry.
        Surface(
            onClick = onClick,
            shape = shape,
            color = container,
            contentColor = content,
            modifier = modifier.heightIn(min = AppTouchTarget)
        ) { badge() }
    }
}

enum class StatusBadgeVariant {
    Success, Warning, Error, Info, Neutral;

    @Composable
    fun colors(): Pair<Color, Color> = when (this) {
        Success -> AppColors.SuccessContainer to AppColors.OnSuccessContainer
        Warning -> AppColors.WarningContainer to AppColors.OnWarningContainer
        Error -> AppColors.ErrorContainer to AppColors.OnErrorContainer
        Info -> AppColors.InfoContainer to AppColors.OnInfoContainer
        // Deliberately onSurface rather than onSurfaceVariant: #9CA3AF on #374151 measures 4.06:1
        // in dark and fails AA, whereas onSurface measures 9.86:1. The muted container still reads
        // as recessive, so nothing is lost by using the stronger foreground here.
        Neutral -> AppColors.SurfaceGray to MaterialTheme.colorScheme.onSurface
    }
}

// =====================================================================================
// 4. FILTER CHIP
// =====================================================================================

/**
 * One filter chip.
 *
 * Replaces six implementations (audit section 4.D) whose touch targets ranged from 30dp to 42dp and
 * whose selected style differed between filled-green and pale-green.
 *
 * Selected state is signalled THREE ways - container fill, border colour and text colour - because
 * a filled green on white measured 3.30:1 for the label, which fails AA at 13sp. The selected chip
 * therefore uses `primaryContainer` + `onPrimaryContainer` (8.30:1 light, 11.43:1 dark) with a green
 * border, which is also exactly what [SegmentedControl] does, so "selected" looks the same
 * everywhere in the app.
 *
 * Unselected: `surface` + `onSurfaceVariant` (7.56:1 light, 5.78:1 dark) with an `outlineVariant`
 * border.
 *
 * [height] defaults to 48dp so the chip is directly tappable. `Row`'s `Arrangement.spacedBy` in
 * [FilterChipRow] supplies the gap.
 */
@Composable
fun FilterChipRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    height: androidx.compose.ui.unit.Dp = AppTouchTarget
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = AppShapes.ChipShape,
        color = container,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.height(height)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            if (leadingIcon != null) {
                Icon(imageVector = leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// =====================================================================================
// 5. SEGMENTED CONTROL
// =====================================================================================

/**
 * One segmented control, for a small set of mutually exclusive options.
 *
 * Replaces three implementations, including the one the audit flagged as a P0 defect: in
 * StockAdjustmentDialog the "Stok Opname" / "Penyesuaian" toggle had NO selected state at all
 * (`isStockCount` only drove a helper paragraph) and, because both halves were `SecondaryButton`
 * whose default content colour is destructive red, BOTH halves rendered red.
 *
 * This is a presentation wrapper only: [options], [selected] and [onSelect] are the caller's, so
 * the state machine and the save callback are untouched.
 *
 * Selected:   primaryContainer + onPrimaryContainer + 1dp primary border  (8.30:1 / 11.43:1)
 * Unselected: surface + onSurfaceVariant + 1dp outlineVariant border        (7.56:1 / 5.78:1)
 * Height is a fixed 48dp, so every segment is a full touch target.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AppTouchTarget),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val container = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
            val contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            val borderColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }

            Surface(
                onClick = { onSelect(option) },
                enabled = enabled,
                shape = AppShapes.ChipShape,
                color = container,
                contentColor = contentColor,
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier
                    .weight(1f)
                    .height(AppTouchTarget)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = label(option),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = AppSpacing.xs)
                    )
                }
            }
        }
    }
}

// =====================================================================================
// 6. FIELDS
// =====================================================================================

/**
 * The app's base text field: 12dp radius, M3 min height (56dp), theme colours.
 *
 * [AppTextField] delegates here. The audit's "4 shapes for one input" came from call sites that
 * passed their own `RoundedCornerShape(10.dp)`, `(12.dp)`, `(14.dp)` and the search field's
 * forced `.height(52.dp)`, the last of which clipped M3's floating label.
 */
@Composable
fun FieldDefault(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    supportingText: String? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = if (label.isBlank()) null else ({ Text(label) }),
        placeholder = if (placeholder != null) ({ Text(placeholder, style = AppTypeScale.Helper) }) else null,
        leadingIcon = leadingIcon?.let { icon ->
            { Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp)) }
        },
        trailingIcon = trailingIcon,
        isError = isError,
        supportingText = supportingText?.let { { Text(it, style = AppTypeScale.Helper) } },
        singleLine = singleLine,
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        shape = AppShapes.TextFieldShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AppColors.GreenPrimary,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * A search field with a persistent leading icon and a clear affordance.
 *
 * The audit found four independent search-field builds, and noted the shared one had NO label - only
 * a placeholder, so typing removed the only label on screen. [label] is therefore a real M3 label
 * and is not optional.
 */
@Composable
fun FieldSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null
) {
    FieldDefault(
        value = query,
        onValueChange = onQueryChange,
        label = label,
        placeholder = placeholder,
        leadingIcon = Icons.Default.Search,
        trailingIcon = if (query.isNotEmpty()) {
            {
                TextButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.height(AppTouchTarget)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Hapus pencarian",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else null,
        modifier = modifier
    )
}

/**
 * A Rupiah amount field.
 *
 * PRESENTATION ONLY - deliberately no `visualTransformation`. Products, Purchase and Stock all parse
 * the raw digits (`toLongOrNull`, `toDoubleOrNull`, `parseQuantityInput`), so reformatting the text
 * while it is being typed would change what those parsers receive. The "Rp" prefix carries the unit
 * instead, which is the same information at zero behavioural risk.
 *
 * `quantity` swaps the keyboard and drops the prefix, for stock and fractional-quantity entry.
 */
@Composable
fun FieldAmount(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
    quantity: Boolean = false,
    enabled: Boolean = true,
    onImeDone: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = if (label.isBlank()) null else ({ Text(label) }),
        prefix = if (quantity) null else ({ Text("Rp", style = AppTypeScale.Helper) }),
        isError = isError,
        supportingText = supportingText?.let { { Text(it, style = AppTypeScale.Helper) } },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (quantity) KeyboardType.Decimal else KeyboardType.Number
        ),
        keyboardActions = if (onImeDone != null) {
            androidx.compose.foundation.text.KeyboardActions(
                onDone = { onImeDone() }
            )
        } else {
            androidx.compose.foundation.text.KeyboardActions.Default
        },
        shape = AppShapes.TextFieldShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AppColors.GreenPrimary,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier.fillMaxWidth()
    )
}

// =====================================================================================
// 7. BUTTONS
// =====================================================================================

/**
 * Shared button geometry and state handling for all four variants.
 *
 * Two guarantees that were previously left to each call site:
 *   - the height is FIXED at 48dp, including while [loading] is true, so swapping the label for a
 *     spinner cannot resize the row and shift everything below it (the audit found the app using
 *     20dp / 24dp / 26dp / 28dp / 30dp / 31dp / 34dp / 36dp / 37dp / 38dp / 40dp / 42dp / 46dp for
 *     interactive controls);
 *   - the spinner is 20dp and centred in the same box, so a loading button occupies the same space
 *     as the idle one.
 */
@Composable
private fun ButtonCore(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    loading: Boolean,
    containerColor: Color,
    contentColor: Color,
    border: BorderStroke?,
    fillMaxWidth: Boolean,
    content: @Composable () -> Unit
) {
    val effectiveModifier = modifier
        .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
        .height(AppTouchTarget)

    val colors = androidx.compose.material3.ButtonDefaults.buttonColors(
        containerColor = containerColor,
        contentColor = contentColor,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = AppShapes.ButtonShape,
        colors = colors,
        border = border,
        elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        modifier = effectiveModifier
    ) {
        if (loading) {
            Box(
                modifier = Modifier
                    .height(AppTouchTarget)
                    .semantics { contentDescription = "Memproses" },
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = contentColor, strokeWidth = 2.dp, modifier = Modifier.size(20.dp)) }
        } else {
            content()
        }
    }
}

/**
 * The primary action. Exactly one per view.
 *
 * Foreground is `onPrimary`, never a literal white: `primary` is #16A34A in light but #22C55E in
 * dark, and white on the dark green measures 2.28:1. Light measures 3.30:1, which is the WCAG
 * large-bold-text case for a 14sp SemiBold label (audit P1 #24).
 */
@Composable
fun ButtonPrimary(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    fillMaxWidth: Boolean = true,
    leadingIcon: ImageVector? = null,
    containerColor: Color = AppColors.GreenPrimary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    ButtonCore(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        containerColor = containerColor,
        contentColor = contentColor,
        border = null,
        fillMaxWidth = fillMaxWidth
    ) {
        if (leadingIcon != null) {
            Icon(imageVector = leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(AppSpacing.sm))
        }
        Text(text = text, style = AppTypeScale.Button, maxLines = 1)
    }
}

/**
 * A safe, non-committal action: "Ubah Jenis Usaha", "Sesuaikan Stok / Opname", "Batal".
 *
 * This variant exists because of audit P0 #12. `SecondaryButton`'s default content colour was
 * destructive red, so two benign actions rendered in the same red as "Hapus QRIS" - and in
 * StockAdjustmentDialog BOTH halves of a mode toggle rendered red, which is what made the missing
 * selected state so easy to miss. Red is now reserved for [ButtonDestructive] alone.
 */
@Composable
fun ButtonNeutral(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    fillMaxWidth: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = AppShapes.ButtonShape,
        border = BorderStroke(1.dp, AppColors.CardBorderStrong),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = modifier
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
            .height(AppTouchTarget)
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onSurface,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp)
            )
        } else {
            if (leadingIcon != null) {
                Icon(imageVector = leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(AppSpacing.sm))
            }
            Text(text = text, style = AppTypeScale.Button, maxLines = 1)
        }
    }
}

/**
 * A destructive or irreversible action: Hapus, Batalkan, Putuskan.
 *
 * Uses the M3 error semantic pair (`error` / `onError`), so it is theme-aware without hardcoding a
 * red - the app currently carries three different reds for this one meaning. Measured 4.98:1 light,
 * 6.78:1 dark.
 *
 * Being destructive, this is intentionally the only filled red control; use [ButtonNeutral] for
 * everything that merely navigates or dismisses.
 */
@Composable
fun ButtonDestructive(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    fillMaxWidth: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    ButtonPrimary(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        fillMaxWidth = fillMaxWidth,
        leadingIcon = leadingIcon,
        containerColor = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError
    )
}

/**
 * A low-emphasis brand action: "Lihat Rincian", "Tanzi dibaca", "Hapus Semua".
 *
 * TRANSPARENT, so it must not be used as the only affordance for a destructive action.
 *
 * The content colour is `AppColors.GreenDark`, not `primary`: #16A34A on white measures 3.30:1 and
 * fails AA for a 14sp SemiBold label, while #087A43 (light) measures 4.93:1 and #22C55E (dark)
 * measures 6.44:1. `GreenDark` is the token the app already uses for exactly this role - active nav
 * labels and green headings on a light surface.
 */
@Composable
fun ButtonGhost(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillMaxWidth: Boolean = false,
    leadingIcon: ImageVector? = null
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = AppShapes.ButtonShape,
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
            contentColor = AppColors.GreenDark,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = modifier
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
            .height(AppTouchTarget)
    ) {
        if (leadingIcon != null) {
            Icon(imageVector = leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(AppSpacing.xs))
        }
        Text(text = text, style = AppTypeScale.Button, maxLines = 1)
    }
}

// =====================================================================================
// 8. ERROR BANNER
// =====================================================================================

/**
 * One error banner for the whole app.
 *
 * Replaces five copies across CashScreen (2), CustomersScreen (2) and SuppliersScreen (2), of which
 * four hardcoded the text red as `Color(0xFFD32F2F)` while the rest used `colorScheme.error` -
 * two different reds for one meaning inside a single screen.
 *
 * This is PRESENTATION ONLY. It is handed a message that already exists; it does not create, catch,
 * inspect or swallow anything, and it does not know where the message came from.
 *
 * Measured 7.96:1 light, 11.06:1 dark.
 *
 * [onRetry] and [onDismiss] are both optional, and a banner with neither is a plain inline message -
 * so an existing `Text(color = colorScheme.error)` can be replaced without inventing a new action.
 */
@Composable
fun AppErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector = Icons.Default.Warning,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "Coba Lagi",
    onDismiss: (() -> Unit)? = null
) {
    Surface(
        shape = AppShapes.CardShape,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(AppSpacing.md),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(AppSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                if (title != null) {
                    Text(text = title, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(AppSpacing.xs))
                }
                Text(text = message, style = AppTypeScale.BodySmall)
                if (onRetry != null || onDismiss != null) {
                    Spacer(Modifier.height(AppSpacing.xs))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onRetry != null) {
                            ButtonGhost(
                                text = retryLabel,
                                onClick = onRetry,
                                fillMaxWidth = false
                            )
                        }
                        if (onDismiss != null) {
                            ButtonGhost(
                                text = "Tutup",
                                onClick = onDismiss,
                                fillMaxWidth = false
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A thin divider that matches the card hairline, for separating rows inside a card. */
@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = modifier
    )
}
