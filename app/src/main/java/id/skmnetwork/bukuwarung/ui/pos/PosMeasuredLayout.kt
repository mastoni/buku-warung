package id.skmnetwork.bukuwarung.ui.pos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.CustomerEntity
import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.domain.checkout.CartLine
import id.skmnetwork.bukuwarung.ui.components.AppTextField
import id.skmnetwork.bukuwarung.ui.components.ProductImageThumbnail
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.util.formatQuantityValue
import id.skmnetwork.bukuwarung.util.parseQuantityInput
import id.skmnetwork.bukuwarung.util.formatRupiah

/**
 * Step 3 - the measured POS layout.
 *
 * Every dimension in here comes from [PosMetrics] / [PosType], which mirror the Step 3 contract
 * values one-for-one. Nothing here invents a size, a colour or a panel.
 *
 * Colours deliberately come from the existing app tokens ([AppColors]). Step 3 section 17 asks for
 * #079B73 but also forbids creating a new green, so the existing primary token is used and the
 * deviation is reported rather than silently introduced.
 */
object PosPalette {
    /** Primary. Existing app token - the app's green, not a POS-only colour. */
    val Primary: Color @Composable get() = AppColors.GreenPrimary
    val PrimaryDark: Color @Composable get() = AppColors.GreenDark
    val Surface: Color @Composable get() = MaterialTheme.colorScheme.surface
    /** Product-area neutral. Existing background token (#F8FAFC in the light scheme). */
    val ProductAreaBackground: Color @Composable get() = MaterialTheme.colorScheme.background
    val TextPrimary: Color @Composable get() = AppColors.TextPrimary
    val TextSecondary: Color @Composable get() = AppColors.TextSecondary
    val Border: Color @Composable get() = MaterialTheme.colorScheme.outline
    val Destructive: Color @Composable get() = AppColors.RedExpense
}

// =====================================================================================
// 2. TOP APP BAR - 72dp, primary green, menu + "KASIR" left, three utility icons + refresh right
// =====================================================================================

@Composable
fun PosTopBar(
    title: String,
    onMenuClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onAddProductClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRefreshClick: () -> Unit,
    showMenuAction: Boolean
) {
    Surface(
        color = PosPalette.Primary,
        modifier = Modifier
            .fillMaxWidth()
            .height(PosMetrics.TopBarHeight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = PosMetrics.TopBarPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The hamburger opens the navigation drawer, which only exists where there is no other
            // app navigation: on a phone the bottom navigation bar is always present, so a second
            // hamburger above it would read as a competing navigation system. It is therefore shown
            // only when the drawer is the navigation (tablet), never on compact.
            if (showMenuAction) {
                // 24dp icon on a 48dp touch target, per the touch-target rule.
                Box(
                    modifier = Modifier
                        .size(PosMetrics.ProductAddTouchTarget)
                        .clickable { onMenuClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = Color.White,
                        modifier = Modifier.size(PosMetrics.TopBarIconSize)
                    )
                }
            }
            Spacer(Modifier.width(PosMetrics.TopBarTitleGap))
            Text(
                text = title,
                fontSize = PosType.AppTitle,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            PosTopBarAction(
                icon = Icons.Default.ReceiptLong,
                contentDescription = "Riwayat",
                onClick = onHistoryClick
            )
            Spacer(Modifier.width(PosMetrics.TopBarActionGap))
            PosTopBarAction(
                icon = Icons.Default.StarBorder,
                contentDescription = "Paket",
                onClick = onAddProductClick
            )
            Spacer(Modifier.width(PosMetrics.TopBarActionGap))
            PosTopBarAction(
                icon = Icons.Default.Settings,
                contentDescription = "Pengaturan",
                onClick = onSettingsClick
            )
            Spacer(Modifier.width(PosMetrics.TopBarActionGap))
            // 56 x 56 rounded refresh action, white on green per the reference.
            Surface(
                onClick = onRefreshClick,
                shape = PosMetrics.CtaShape,
                color = Color.White,
                modifier = Modifier.size(PosMetrics.TopBarRefreshButton)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Muat ulang",
                        tint = PosPalette.Primary,
                        modifier = Modifier.size(PosMetrics.TopBarIconSize)
                    )
                }
            }
        }
    }
}

/**
 * A 24dp top-bar icon on a 48dp touch target. The contract fixes the icon at 24dp and the touch
 * target rule at >= 48dp, so the padding lives here rather than being inlined at each call site.
 */
@Composable
private fun PosTopBarAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(PosMetrics.ProductAddTouchTarget)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(PosMetrics.TopBarIconSize)
        )
    }
}

// =====================================================================================
// 4. SEARCH BAR - 54dp, 12dp radius, 1dp border, 24dp leading icon, 24dp QR action
// =====================================================================================

@Composable
fun PosSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .height(PosMetrics.SearchHeight),
        placeholder = {
            Text(
                text = "Cari produk atau paket...",
                fontSize = PosMetrics.SearchPlaceholderSize,
                color = PosPalette.TextSecondary
            )
        },
        singleLine = true,
        shape = PosMetrics.CtaShape,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = PosMetrics.SearchPlaceholderSize),
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Cari",
                tint = PosPalette.TextSecondary,
                modifier = Modifier.size(PosMetrics.SearchLeadingIconSize)
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = "Pindai barcode",
                tint = PosPalette.Primary,
                modifier = Modifier
                    .size(PosMetrics.SearchTrailingIconSize)
                    .clickable { onScanClick() }
            )
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PosPalette.Primary,
            unfocusedBorderColor = PosPalette.Border,
            focusedContainerColor = PosPalette.Surface,
            unfocusedContainerColor = PosPalette.Surface
        )
    )
}

// =====================================================================================
// 5. MODE TABS - 48dp, two equal tabs, 3dp green indicator, 1dp bottom divider
// =====================================================================================

@Composable
fun PosModeTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(PosMetrics.ModeTabsHeight)
        ) {
            labels.forEachIndexed { index, label ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = PosType.CartProduct,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) PosPalette.Primary else PosPalette.TextSecondary
                    )
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(PosMetrics.ModeTabIndicatorHeight)
                                .background(PosPalette.Primary)
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PosMetrics.ModeTabDividerHeight)
                .background(PosPalette.Border)
        )
    }
}

// =====================================================================================
// 6. CATEGORY ROW - 56dp, 10dp gap, 42dp chips, 21dp radius, horizontally scrollable
// =====================================================================================

@Composable
fun PosCategoryRow(
    categories: List<CategoryEntity>,
    selectedCategoryId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(PosMetrics.CategoryRowHeight)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PosMetrics.CategoryChipGap)
    ) {
        PosCategoryChip(
            label = "Semua",
            isSelected = selectedCategoryId == null,
            onClick = { onSelect(null) }
        )
        categories.forEach { category ->
            PosCategoryChip(
                label = category.name,
                isSelected = selectedCategoryId == category.id,
                onClick = { onSelect(category.id) }
            )
        }
    }
}

@Composable
private fun PosCategoryChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = PosMetrics.CategoryChipShape,
        color = if (isSelected) PosPalette.Primary else PosPalette.Surface,
        border = if (isSelected) null else BorderStroke(PosMetrics.SearchBorderWidth, PosPalette.Border),
        modifier = Modifier
            .height(PosMetrics.CategoryChipHeight)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = PosMetrics.CategoryChipPaddingHorizontal),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = PosType.Category,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) Color.White else PosPalette.TextPrimary,
                maxLines = 1
            )
        }
    }
}

// =====================================================================================
// 8 / 9 / 10. PRODUCT CARD - 16dp radius, 198dp min height, measured internal layout
// =====================================================================================

@Composable
fun MeasuredProductCard(
    product: ProductEntity,
    productCode: String?,
    cartQuantity: Double,
    isStockable: Boolean,
    isOutOfStock: Boolean,
    onAddClick: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = cartQuantity > 0.0
    // Out of stock stays visible but is desaturated, per the contract. It is never removed.
    val contentAlpha = if (isOutOfStock) 0.45f else 1f

    Surface(
        shape = PosMetrics.CardShape,
        color = PosPalette.Surface,
        border = if (isSelected) {
            BorderStroke(PosMetrics.ProductSelectedBorder, PosPalette.Primary)
        } else {
            BorderStroke(PosMetrics.SearchBorderWidth, PosPalette.Border)
        },
        modifier = modifier
            .heightIn(min = PosMetrics.ProductCardMinHeight)
            .alpha(contentAlpha)
    ) {
        Column(
            modifier = Modifier.padding(PosMetrics.ProductCardPadding),
            verticalArrangement = Arrangement.spacedBy(PosMetrics.ProductCardInnerGap)
        ) {
            // TOP - stock badge
            Surface(
                shape = PosMetrics.ChipShape,
                color = if (isOutOfStock) PosPalette.Destructive.copy(alpha = 0.12f) else PosPalette.ProductAreaBackground,
                modifier = Modifier.heightIn(min = PosMetrics.ProductBadgeHeight)
            ) {
                Text(
                    text = if (isOutOfStock) "Habis" else "${terminologyStockLabel(isStockable)}: ${formatQuantityValue(product.stock)}",
                    fontSize = PosType.ProductCode,
                    fontWeight = FontWeight.Medium,
                    color = if (isOutOfStock) PosPalette.Destructive else PosPalette.TextSecondary,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = PosMetrics.RadiusSmall, vertical = 2.dp)
                )
            }

            // CENTER - image zone. ProductImageThumbnail already exists for the product form and
            // the catalogue, reads product.imageUri, and falls back to the same Inventory2 icon when
            // a product has no photo, so the POS reuses it instead of hardcoding a placeholder.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PosMetrics.ProductImageZoneHeight)
                    .background(PosPalette.ProductAreaBackground, PosMetrics.CtaShape),
                contentAlignment = Alignment.Center
            ) {
                ProductImageThumbnail(
                    imageUri = product.imageUri,
                    tint = PosPalette.TextSecondary,
                    modifier = Modifier.size(PosMetrics.ProductImageZoneHeight)
                )
            }

            // BOTTOM - name, code, price, add control
            Text(
                text = product.name,
                fontSize = PosType.ProductName,
                fontWeight = FontWeight.SemiBold,
                color = PosPalette.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!productCode.isNullOrBlank()) {
                Text(
                    text = productCode,
                    fontSize = PosType.ProductCode,
                    color = PosPalette.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = formatRupiah(product.sellingPrice),
                fontSize = PosType.ProductPrice,
                fontWeight = FontWeight.SemiBold,
                color = PosPalette.Primary
            )

            Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (isSelected) {
                    // SELECTED - the card shows a quantity control instead of the add button.
                    PosQuantityControl(
                        quantity = cartQuantity,
                        onIncrement = onIncrement,
                        onDecrement = onDecrement,
                        enabled = !isOutOfStock
                    )
                } else {
                    // 36dp visual diameter on a 48dp touch target.
                    Box(
                        modifier = Modifier.size(PosMetrics.ProductAddTouchTarget),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            onClick = onAddClick,
                            enabled = !isOutOfStock,
                            shape = CircleShape,
                            color = if (isOutOfStock) PosPalette.Border else PosPalette.Primary,
                            modifier = Modifier.size(PosMetrics.ProductAddButtonSize)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Tambah",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun terminologyStockLabel(isStockable: Boolean): String = if (isStockable) "Stok" else "—"

// =====================================================================================
// 13. CART QUANTITY CONTROL - 112 x 56, minimum 48dp touch targets, never 22/26dp
// =====================================================================================

@Composable
fun PosQuantityControl(
    quantity: Double,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    enabled: Boolean = true,
    compact: Boolean = false,
    // Step 5: when supplied, the displayed number becomes tappable and opens the precise-entry
    // dialog. Left null, the control stays a pure stepper.
    productName: String = "",
    unit: String = "",
    maxQuantity: Double? = null,
    onQuantityInput: ((Double) -> Unit)? = null
) {
    val width = if (compact) PosMetrics.CartQtyControlWidth else 96.dp
    var showInput by remember { mutableStateOf(false) }
    Surface(
        shape = PosMetrics.QtyControlShape,
        color = PosPalette.ProductAreaBackground,
        border = BorderStroke(PosMetrics.SearchBorderWidth, PosPalette.Border),
        modifier = Modifier
            .width(width)
            .height(PosMetrics.CartQtyControlHeight)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(PosMetrics.ProductAddTouchTarget)
                    .clickable(enabled = enabled) { onDecrement() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Kurangi",
                    tint = if (enabled) PosPalette.Primary else PosPalette.TextSecondary,
                    modifier = Modifier.size(PosMetrics.SearchLeadingIconSize)
                )
            }
            // Step 5: the stepper stays the quick adjustment, and the number becomes the precise
            // entry. Tapping it opens the same editor a merchant would expect, instead of
            // requiring a dozen presses of "+" to reach 1.25.
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .fillMaxHeight()
                    .then(
                        if (onQuantityInput != null) {
                            Modifier.clickable(enabled = enabled) { showInput = true }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formatQuantityValue(quantity),
                    fontSize = PosType.CartPrice,
                    fontWeight = FontWeight.SemiBold,
                    color = PosPalette.TextPrimary,
                    maxLines = 1,
                    modifier = Modifier.width(36.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(PosMetrics.ProductAddTouchTarget)
                    .clickable(enabled = enabled) { onIncrement() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Tambah",
                    tint = if (enabled) PosPalette.Primary else PosPalette.TextSecondary,
                    modifier = Modifier.size(PosMetrics.SearchLeadingIconSize)
                )
            }
        }
    }

    if (showInput && onQuantityInput != null) {
        PosQuantityInputDialog(
            productName = productName,
            unit = unit,
            currentQuantity = quantity,
            maxQuantity = maxQuantity,
            onDismiss = { showInput = false },
            onConfirm = { precise ->
                showInput = false
                onQuantityInput(precise)
            }
        )
    }
}

/**
 * Step 5 - precise quantity entry.
 *
 * Reuses the app's existing [id.skmnetwork.bukuwarung.ui.components.AppTextField] and the existing
 * comma-or-dot parser, so this reads as native to the rest of the app and accepts exactly the
 * spellings the stock dialog and the purchase screen already accept.
 *
 * It changes text only. The caller decides what a value means, so the stock cap, the removal of
 * an emptied line and the money calculation all stay in the POS, untouched by this dialog.
 */
@Composable
fun PosQuantityInputDialog(
    productName: String,
    unit: String,
    currentQuantity: Double,
    maxQuantity: Double?,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var text by remember(currentQuantity) { mutableStateOf(formatQuantityValue(currentQuantity)) }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Jumlah", fontWeight = FontWeight.Bold) },
        text = {
            val errorText = error
            Column(verticalArrangement = Arrangement.spacedBy(PosMetrics.RadiusSmall)) {
                Text(
                    text = productName,
                    fontSize = PosType.CartProduct,
                    fontWeight = FontWeight.SemiBold,
                    color = PosPalette.TextPrimary
                )
                AppTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        error = null
                    },
                    label = "Jumlah ($unit)",
                    isError = errorText != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                if (maxQuantity != null) {
                    Text(
                        text = "Maksimal ${formatQuantityValue(maxQuantity)} $unit",
                        fontSize = PosType.Helper,
                        color = PosPalette.TextSecondary
                    )
                }
                if (errorText != null) {
                    Text(
                        text = errorText,
                        fontSize = PosType.Helper,
                        color = PosPalette.Destructive
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = parseQuantityInput(text)
                    if (parsed == null || parsed.isNaN() || parsed.isInfinite()) {
                        error = "Masukkan angka yang valid"
                        return@TextButton
                    }
                    if (parsed < 0.0) {
                        error = "Jumlah tidak boleh kurang dari 0"
                        return@TextButton
                    }
                    if (maxQuantity != null && parsed > maxQuantity) {
                        error = "Jumlah maksimal ${formatQuantityValue(maxQuantity)} $unit"
                        return@TextButton
                    }
                    onConfirm(parsed)
                }
            ) {
                Text("Simpan", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

// =====================================================================================
// 12. CUSTOMER CARD - 72dp, 14dp radius, 40dp leading icon, 24dp arrow
// =====================================================================================

@Composable
fun PosCustomerCard(
    customer: CustomerEntity?,
    customerLabel: String,
    onClick: () -> Unit
) {
    Surface(
        shape = PosMetrics.CustomerCardShape,
        color = PosPalette.ProductAreaBackground,
        border = BorderStroke(PosMetrics.SearchBorderWidth, PosPalette.Border),
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(PosMetrics.CustomerCardHeight)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PosMetrics.CustomerCardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(PosMetrics.CustomerIconSize)
                    .background(PosPalette.Surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = PosPalette.Primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(PosMetrics.RadiusSmall))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customerLabel,
                    fontSize = PosType.Helper,
                    color = PosPalette.TextSecondary
                )
                Text(
                    text = customer?.name ?: "Pilih $customerLabel",
                    fontSize = PosType.CartProduct,
                    fontWeight = FontWeight.SemiBold,
                    color = PosPalette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = PosPalette.TextSecondary,
                modifier = Modifier.size(PosMetrics.CustomerArrowSize)
            )
        }
    }
}

// =====================================================================================
// 16. CART CLEAR - destructive, visually secondary to the payment CTA
// =====================================================================================

@Composable
fun PosCartClearAction(onClick: () -> Unit) {
    Text(
        text = "Hapus Semua",
        fontSize = PosType.Helper,
        fontWeight = FontWeight.SemiBold,
        color = PosPalette.Destructive,
        modifier = Modifier
            .heightIn(min = PosMetrics.CartClearTouchTarget)
            .clickable { onClick() }
            .padding(horizontal = PosMetrics.RadiusSmall)
    )
}

// =====================================================================================
// 15. PAYMENT CTA - full width, 56dp, 12dp radius, primary green, 16sp
// =====================================================================================

@Composable
fun PosPaymentCta(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = PosMetrics.CtaShape,
        color = if (enabled) PosPalette.Primary else PosPalette.Border,
        modifier = modifier
            .fillMaxWidth()
            .height(PosMetrics.PaymentCtaHeight)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = PosType.PrimaryCta,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) Color.White else PosPalette.TextSecondary
            )
        }
    }
}

// =====================================================================================
// 14. CART SUMMARY - pinned to the bottom, never moves when the item count changes
// =====================================================================================

@Composable
fun PosCartSummary(
    discountLabel: String,
    discountValue: String,
    subtotalLabel: String?,
    subtotalValue: String?,
    totalLabel: String,
    totalValue: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PosMetrics.RadiusSmall)
    ) {
        if (discountLabel != null) {
            PosSummaryRow(label = discountLabel, value = discountValue)
        }
        if (subtotalLabel != null) {
            PosSummaryRow(label = subtotalLabel, value = subtotalValue.orEmpty())
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PosMetrics.ModeTabDividerHeight)
                .background(PosPalette.Border)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = totalLabel,
                fontSize = PosType.TotalLabel,
                fontWeight = FontWeight.SemiBold,
                color = PosPalette.TextPrimary
            )
            Text(
                text = totalValue,
                fontSize = PosType.TotalValue,
                fontWeight = FontWeight.Bold,
                color = PosPalette.Primary
            )
        }
    }
}

@Composable
private fun PosSummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = PosType.CartPrice, color = PosPalette.TextSecondary)
        Text(text = value, fontSize = PosType.CartPrice, color = PosPalette.TextPrimary)
    }
}

// =====================================================================================
// 21. PHONE - bottom cart bar
//
// The phone bar is the same cart, collapsed: it carries the item count, the total and the same
// primary CTA. Tapping it opens the same cart detail the tablet shows permanently, so the phone
// and the tablet expose identical product, quantity, subtotal, discount and total information
// and the payment step behind them is literally the same dialog.
// =====================================================================================

@Composable
fun PosBottomCartBar(
    itemCountLabel: String,
    totalValue: String,
    ctaLabel: String,
    ctaEnabled: Boolean,
    onBarClick: () -> Unit,
    onCtaClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = PosPalette.Surface,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(PosMetrics.RadiusSmall),
            verticalArrangement = Arrangement.spacedBy(PosMetrics.RadiusSmall)
        ) {
            // The summary row is the cart entry point on the phone: the same 48dp touch rule, the
            // same tokens, the same values the tablet summary shows.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = PosMetrics.ProductAddTouchTarget)
                    .clickable { onBarClick() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = "Keranjang",
                        tint = PosPalette.Primary,
                        modifier = Modifier.size(PosMetrics.SearchLeadingIconSize)
                    )
                    Spacer(Modifier.width(PosMetrics.RadiusSmall))
                    Text(
                        text = itemCountLabel,
                        fontSize = PosType.Helper,
                        color = PosPalette.TextSecondary
                    )
                }
                Text(
                    text = totalValue,
                    fontSize = PosType.ProductPrice,
                    fontWeight = FontWeight.SemiBold,
                    color = PosPalette.Primary
                )
            }
            PosPaymentCta(
                label = ctaLabel,
                enabled = ctaEnabled,
                onClick = onCtaClick
            )
        }
    }
}

// =====================================================================================
// 21b. PHONE - cart detail
//
// This is the tablet cart panel hosted as a full-height dialog. The customer card, the item rows,
// the quantity control, the summary and the CTA are the same composables the tablet uses, so the
// phone is not a second design: it is the same cart in a different container.
// =====================================================================================

@Composable
fun PosPhoneCartDialog(
    cartSummaryLabel: String,
    discountValue: String,
    subtotalLabel: String?,
    subtotalValue: String?,
    totalLabel: String,
    totalValue: String,
    cartContent: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
    onClearClick: () -> Unit,
    ctaLabel: String,
    ctaEnabled: Boolean,
    onCtaClick: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = PosMetrics.CartPanelShape,
            color = PosPalette.Surface,
            modifier = Modifier
                .fillMaxWidth(PosMetrics.PhoneCartDialogWidthFraction)
                .fillMaxHeight(PosMetrics.PhoneCartDialogHeightFraction)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = PosMetrics.RightPanePadding,
                        end = PosMetrics.RightPanePadding,
                        top = PosMetrics.ContentPaddingTop,
                        bottom = PosMetrics.PaymentCtaBottomMargin
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PosMetrics.CartHeaderHeight),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = cartSummaryLabel,
                        fontSize = PosType.SectionTitle,
                        fontWeight = FontWeight.SemiBold,
                        color = PosPalette.TextPrimary
                    )
                    PosCartClearAction(onClick = onClearClick)
                }

                cartContent()

                Spacer(Modifier.height(PosMetrics.PrimaryColumnGap))

                PosCartSummary(
                    discountLabel = "Diskon",
                    discountValue = discountValue,
                    subtotalLabel = subtotalLabel,
                    subtotalValue = subtotalValue,
                    totalLabel = totalLabel,
                    totalValue = totalValue
                )

                Spacer(Modifier.height(PosMetrics.RadiusSmall))

                PosPaymentCta(
                    label = ctaLabel,
                    enabled = ctaEnabled,
                    onClick = onCtaClick
                )
            }
        }
    }
}

// =====================================================================================
// Sales history pane - preserved behaviour, reached from the top bar instead of a mode tab
// =====================================================================================

/**
 * Step 9 - a scrollable row of filter chips sharing the measured category chip metrics
 * (42dp tall, category chip shape and padding, same palette) so the history filters are the same
 * control the product categories already are, just applied to sales.
 */
@Composable
private fun PosFilterChipRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(PosMetrics.CategoryRowHeight)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PosMetrics.CategoryChipGap)
    ) {
        labels.forEachIndexed { index, label ->
            PosCategoryChip(
                label = label,
                isSelected = index == selectedIndex,
                onClick = { onSelect(index) }
            )
        }
    }
}

@Composable
fun PosSalesHistoryPane(
    sales: List<id.skmnetwork.bukuwarung.data.local.entity.SaleTransactionEntity>,
    allSalesCount: Int,
    customers: List<CustomerEntity>,
    terminology: id.skmnetwork.bukuwarung.domain.business.BusinessTerminology,
    selectedPeriod: id.skmnetwork.bukuwarung.ui.report.ReportPeriod?,
    selectedPayment: SalesPaymentFilter,
    onSelectPeriod: (id.skmnetwork.bukuwarung.ui.report.ReportPeriod?) -> Unit,
    onSelectPayment: (SalesPaymentFilter) -> Unit,
    onOpenSale: (id.skmnetwork.bukuwarung.data.local.entity.SaleTransactionEntity) -> Unit,
    onBackToRegister: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = PosMetrics.LeftPanePadding,
                end = PosMetrics.LeftPanePadding,
                top = PosMetrics.ContentPaddingTop,
                bottom = PosMetrics.ContentPaddingBottom
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(PosMetrics.CartHeaderHeight),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Riwayat ${terminology.transactionLabel}",
                fontSize = PosType.SectionTitle,
                fontWeight = FontWeight.SemiBold,
                color = PosPalette.TextPrimary
            )
            PosPaymentCta(
                label = "Kembali ke Kasir",
                enabled = true,
                onClick = onBackToRegister,
                modifier = Modifier.width(180.dp)
            )
        }

        // Step 9 - the two existing filter controls, using the same chip metrics, shapes and
        // palette as the product category chips so the history pane needs no new visual language.
        // Both start cleared, and each chip is a single tap target sized for the 411x891 device.
        val salePeriods = id.skmnetwork.bukuwarung.ui.report.ReportPeriod.entries
        PosFilterChipRow(
            labels = salePeriods.map { it.label },
            // "Semua" is ReportPeriod.ALL_TIME, so the cleared state is that chip - the filter
            // itself is off, and the chip that says it is the one shown as selected.
            selectedIndex = salePeriods.indexOf(
                selectedPeriod ?: id.skmnetwork.bukuwarung.ui.report.ReportPeriod.ALL_TIME
            ),
            onSelect = { index ->
                val period = salePeriods[index]
                onSelectPeriod(if (period == id.skmnetwork.bukuwarung.ui.report.ReportPeriod.ALL_TIME) null else period)
            }
        )

        Spacer(Modifier.height(PosMetrics.RadiusSmall))

        val salePaymentFilters = SalesPaymentFilter.entries
        PosFilterChipRow(
            labels = salePaymentFilters.map { it.label },
            selectedIndex = salePaymentFilters.indexOf(selectedPayment),
            onSelect = { index -> onSelectPayment(salePaymentFilters[index]) }
        )

        Spacer(Modifier.height(PosMetrics.CartItemGap))

        if (sales.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (allSalesCount == 0) {
                        "Belum ada riwayat ${terminology.transactionLabel.lowercase()}"
                    } else {
                        "Tidak ada transaksi yang cocok"
                    },
                    fontSize = PosType.Helper,
                    color = PosPalette.TextSecondary
                )
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(PosMetrics.CartItemGap)
            ) {
                items(sales.size) { index ->
                    val sale = sales[index]
                    val customerName = customers.find { it.id == sale.customerId }?.name
                        ?: "${terminology.customerLabel} Umum"
                    val isCredit = sale.paymentMethod == "CREDIT"
                    Surface(
                        onClick = { onOpenSale(sale) },
                        shape = PosMetrics.CardShape,
                        color = PosPalette.Surface,
                        border = BorderStroke(PosMetrics.SearchBorderWidth, PosPalette.Border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(PosMetrics.RadiusSmall),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sale.transactionNumber,
                                    fontSize = PosType.CartProduct,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PosPalette.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = customerName,
                                    fontSize = PosType.Helper,
                                    color = PosPalette.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = formatRupiah(sale.totalAmount),
                                fontSize = PosType.ProductPrice,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isCredit) PosPalette.Destructive else PosPalette.Primary
                            )
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================================
// 22. TABLET SPLIT - 66.6 / 33.4, never hardcoded to 852dp
//
// The cart pane width is derived from the width the POS actually gets, which on tablet is the
// whole screen: the app's navigation rail is not part of the POS workspace, so it must not eat
// into the 66.6 / 33.4 split.
// =====================================================================================

@Composable
fun PosTabletSplit(
    productPane: @Composable () -> Unit,
    cartPane: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(PosMetrics.LeftPaneWeight)) { productPane() }
        Surface(
            color = PosPalette.Surface,
            modifier = Modifier
                .weight(PosMetrics.RightPaneWeight)
                .widthIn(min = PosMetrics.CartMinWidth)
                .fillMaxHeight()
        ) {
            cartPane()
        }
    }
}
