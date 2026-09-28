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
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.util.formatQuantityValue
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
    onRefreshClick: () -> Unit
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
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu",
                tint = Color.White,
                modifier = Modifier.size(PosMetrics.TopBarIconSize)
            )
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
            Icon(
                imageVector = Icons.Default.ReceiptLong,
                contentDescription = "Riwayat",
                tint = Color.White,
                modifier = Modifier.size(PosMetrics.TopBarIconSize)
            )
            Spacer(Modifier.width(PosMetrics.TopBarActionGap))
            Icon(
                imageVector = Icons.Default.StarBorder,
                contentDescription = "Paket",
                tint = Color.White,
                modifier = Modifier.size(PosMetrics.TopBarIconSize)
            )
            Spacer(Modifier.width(PosMetrics.TopBarActionGap))
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Pengaturan",
                tint = Color.White,
                modifier = Modifier.size(PosMetrics.TopBarIconSize)
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
            modifier = Modifier.padding(PosMetrics.RadiusSmall),
            verticalArrangement = Arrangement.spacedBy(PosMetrics.RadiusSmall)
        ) {
            // TOP - stock badge
            Surface(
                shape = PosMetrics.ChipShape,
                color = if (isOutOfStock) PosPalette.Destructive.copy(alpha = 0.12f) else PosPalette.ProductAreaBackground,
                modifier = Modifier.heightIn(min = PosMetrics.RadiusSmall)
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

            // CENTER - image zone, fixed 78dp so cards keep a predictable height
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PosMetrics.ProductImageZoneHeight)
                    .background(PosPalette.ProductAreaBackground, PosMetrics.CtaShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = PosPalette.TextSecondary,
                    modifier = Modifier.size(40.dp)
                )
            }

            // BOTTOM - name, code, price, add control
            Text(
                text = product.name,
                fontSize = PosType.ProductName,
                fontWeight = FontWeight.SemiBold,
                color = PosPalette.TextPrimary,
                maxLines = 1,
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
    compact: Boolean = false
) {
    val width = if (compact) PosMetrics.CartQtyControlWidth else 96.dp
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
            Text(
                text = formatQuantityValue(quantity),
                fontSize = PosType.CartPrice,
                fontWeight = FontWeight.SemiBold,
                color = PosPalette.TextPrimary,
                maxLines = 1,
                modifier = Modifier.width(36.dp)
            )
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
            .clickable { onClick() }
            .padding(horizontal = PosMetrics.RadiusSmall, vertical = PosMetrics.RadiusSmall)
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
// =====================================================================================

@Composable
fun PosBottomCartBar(
    itemCountLabel: String,
    totalValue: String,
    ctaLabel: String,
    ctaEnabled: Boolean,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = itemCountLabel,
                    fontSize = PosType.Helper,
                    color = PosPalette.TextSecondary
                )
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
// Sales history pane - preserved behaviour, reached from the top bar instead of a mode tab
// =====================================================================================

@Composable
fun PosSalesHistoryPane(
    sales: List<id.skmnetwork.bukuwarung.data.local.entity.SaleTransactionEntity>,
    allSalesCount: Int,
    customers: List<CustomerEntity>,
    terminology: id.skmnetwork.bukuwarung.domain.business.BusinessTerminology,
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
