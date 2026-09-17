package id.skmnetwork.bukuwarung.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.domain.business.BusinessCapability
import id.skmnetwork.bukuwarung.domain.business.BusinessTaxonomyRegistry
import id.skmnetwork.bukuwarung.domain.business.BusinessTerminology
import id.skmnetwork.bukuwarung.domain.business.ResolvedBusinessProfile
import id.skmnetwork.bukuwarung.ui.navigation.AppScreen
import id.skmnetwork.bukuwarung.ui.product.ProductViewModel
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppSpacing
import id.skmnetwork.bukuwarung.util.formatRupiah
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Gate I.2-R1 & G6 — Home / Beranda Screen (Adaptive Design Master)
 *
 * Visual & Structural Hierarchy:
 * 1. Store Identity Header (Branding, Shop Name, Subtitle, Active Badge & Initial Avatar)
 * 2. Welcoming Greeting & Dynamic Indonesian Date Banner (Focal Point)
 * 3. Financial Summary 2x2 Pastel Grid (Penjualan, Pengeluaran, Saldo Kas, Stok Menipis)
 * 4. Menu Utama (8 comfortable pastel icon tiles in 4-column grid with generous touch targets)
 * 5. Low Stock Notice Banner (if active)
 * 6. UMKM Motivation Banner ("Warung Kecil, Langkah Besar Masa Depan")
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ProductViewModel,
    userSettings: UserSettings = UserSettings(),
    shopName: String = userSettings.shopName,
    unreadNotificationCount: Int = 0,
    onNavigate: (AppScreen) -> Unit
) {
    val resolvedProfile = remember(userSettings.primaryBusinessType, userSettings.secondaryActivities) {
        BusinessTaxonomyRegistry.resolve(
            primaryType = userSettings.primaryBusinessType,
            secondaryActivities = userSettings.secondaryActivities
        )
    }
    val terminology = resolvedProfile.terminology
    val hasStockCapability = resolvedProfile.hasCapability(BusinessCapability.CAP_INVENTORY_STOCK)

    val dbProducts by viewModel.products.collectAsStateWithLifecycle()
    val cashBalance by viewModel.cashBalance.collectAsStateWithLifecycle()
    val todaySalesTotal by viewModel.todaySalesTotal.collectAsStateWithLifecycle()
    val todayExpenseTotal by viewModel.todayExpenseTotal.collectAsStateWithLifecycle()

    val lowStockCount = if (userSettings.lowStockAlertEnabled && hasStockCapability) {
        dbProducts.count { it.stock <= it.minimumStock }
    } else {
        0
    }

    val todaySalesFormatted = formatRupiah(todaySalesTotal ?: 0L)
    val todayExpenseFormatted = formatRupiah(todayExpenseTotal ?: 0L)
    val cashBalanceFormatted = formatRupiah(cashBalance ?: 0L)

    val effectiveShopName = shopName.trim().ifEmpty { "Warung Saya" }

    // Dynamic greeting based on current hour
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 4..10 -> "Selamat pagi,"
            in 11..14 -> "Selamat siang,"
            in 15..18 -> "Selamat sore,"
            else -> "Selamat malam,"
        }
    }

    // Dynamic Indonesian date (e.g. "Sabtu, 12 September 2026")
    val currentDateStr = remember {
        val sdf = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.forLanguageTag("id-ID"))
        sdf.format(Date())
    }

    val shopInitial = remember(effectiveShopName) {
        effectiveShopName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "W"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        // 1. Buku Warung Branding Icon
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(AppColors.GreenPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storefront,
                                contentDescription = "Buku Warung Logo",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(Modifier.width(10.dp))

                        // 2. Shop Name & Subtitle
                        Column(
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Text(
                                text = effectiveShopName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.5.sp,
                                color = AppColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Pembukuan ${resolvedProfile.businessType.displayName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.TextSecondary,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = AppSpacing.sm)
                    ) {
                        // 1. Notification Bell
                        Box(
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable { onNavigate(AppScreen.NOTIFICATIONS) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notifikasi",
                                tint = AppColors.TextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            if (unreadNotificationCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 4.dp, end = 4.dp)
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE53935))
                                )
                            }
                        }

                        // 2. Active status pill
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = AppColors.GreenLight,
                            border = BorderStroke(1.dp, Color(0xFFCCE8D7)),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(AppColors.GreenPrimary)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Aktif",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppColors.GreenDark
                                )
                            }
                        }

                        // 3. Shop Avatar (User / Warung Initial)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AppColors.GreenLight)
                                .border(BorderStroke(1.5.dp, Color(0xFFCCE8D7)), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = shopInitial,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = AppColors.GreenDark
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        containerColor = Color(0xFFF8FAF9)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ==========================================
            // 1. GREETING & DATE BANNER (FOCAL POINT)
            // ==========================================
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    Text(
                        text = greeting,
                        fontSize = 14.sp,
                        color = AppColors.TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "$effectiveShopName 👋",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AppColors.GreenDark
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = "Tanggal",
                                tint = AppColors.TextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = currentDateStr,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = AppColors.TextSecondary
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 2. FINANCIAL SUMMARY 2X2 GRID
            // ==========================================
            item {
                SummaryGrid(
                    todaySalesStr = todaySalesFormatted,
                    todayExpenseStr = todayExpenseFormatted,
                    cashBalanceStr = cashBalanceFormatted,
                    lowStockCount = lowStockCount,
                    showLowStockAlert = userSettings.lowStockAlertEnabled && hasStockCapability,
                    terminology = terminology
                )
            }

            // ==========================================
            // 3. MENU UTAMA HEADER & GRID
            // ==========================================
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Menu Utama",
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                    Text(
                        text = "8 Fitur",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppColors.TextSecondary
                    )
                }
            }

            item {
                MenuGrid(
                    terminology = terminology,
                    onNavigate = onNavigate
                )
            }

            // ==========================================
            // 4. LOW STOCK WARNING BANNER (IF ACTIVE)
            // ==========================================
            if (userSettings.lowStockAlertEnabled && hasStockCapability && lowStockCount > 0) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7E6)),
                        border = BorderStroke(1.dp, Color(0xFFFFD591)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate(AppScreen.PRODUCTS) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFE7BA)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = "Peringatan Stok",
                                    tint = Color(0xFFD46B08),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${terminology.productLabel} Hampir Habis",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = Color(0xFFD46B08)
                                )
                                Text(
                                    text = "$lowStockCount ${terminology.productLabel.lowercase()} mencapai batas minimum stok",
                                    fontSize = 12.sp,
                                    color = AppColors.TextSecondary
                                )
                            }
                            Text(
                                text = "Lihat >",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD46B08)
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 5. MOTIVATIONAL VALUE BANNER (DESIGN MASTER ALIGNED)
            // ==========================================
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F7EF)),
                    border = BorderStroke(1.dp, Color(0xFFCEECD9)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFD3F2E0)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Eco,
                                contentDescription = "Buku Warung Icon",
                                tint = AppColors.GreenDark,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Warung Kecil, Langkah Besar Masa Depan",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = AppColors.GreenDark
                            )
                            Text(
                                text = "Data Anda, Aset Anda, Masa Depan Anda 💚",
                                fontSize = 11.5.sp,
                                color = AppColors.GreenPrimary
                            )
                        }
                    }
                }
            }

            // Bottom spacer to ensure smooth scrolling above bottom navigation bar
            item {
                Spacer(Modifier.height(AppSpacing.lg))
            }
        }
    }
}

/**
 * 2x2 Summary Grid matching Design Master pastel styling & icons
 */
@Composable
private fun SummaryGrid(
    todaySalesStr: String,
    todayExpenseStr: String,
    cashBalanceStr: String,
    lowStockCount: Int,
    showLowStockAlert: Boolean,
    terminology: BusinessTerminology = BusinessTerminology()
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryCard(
                title = "${terminology.transactionLabel} Hari Ini",
                value = todaySalesStr,
                icon = Icons.Default.ShoppingCart,
                iconTint = Color(0xFF087A43),
                iconBg = Color(0xFFD3F2E0),
                cardBg = Color(0xFFE8F7EF),
                borderColor = Color(0xFFCEECD9),
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                title = "Pengeluaran Hari Ini",
                value = todayExpenseStr,
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                iconTint = Color(0xFFCF1322),
                iconBg = Color(0xFFFFD6D6),
                cardBg = Color(0xFFFFEAEA),
                borderColor = Color(0xFFFFD4D4),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryCard(
                title = "Saldo Kas",
                value = cashBalanceStr,
                icon = Icons.Default.AccountBalanceWallet,
                iconTint = Color(0xFF096DD9),
                iconBg = Color(0xFFD0E8FF),
                cardBg = Color(0xFFE8F3FF),
                borderColor = Color(0xFFBAE0FF),
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                title = "${terminology.stockLabel} Menipis",
                value = if (showLowStockAlert) "$lowStockCount ${terminology.productLabel.lowercase()}" else "Nonaktif",
                icon = Icons.Default.Inventory2,
                iconTint = if (showLowStockAlert && lowStockCount > 0) Color(0xFFD46B08) else Color(0xFF8C8C8C),
                iconBg = if (showLowStockAlert && lowStockCount > 0) Color(0xFFFFE7BA) else Color(0xFFE8E8E8),
                cardBg = if (showLowStockAlert && lowStockCount > 0) Color(0xFFFFF7E6) else Color(0xFFF5F5F5),
                borderColor = if (showLowStockAlert && lowStockCount > 0) Color(0xFFFFD591) else Color(0xFFE8E8E8),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    cardBg: Color,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.TextSecondary,
                    lineHeight = 12.sp,
                    maxLines = 2
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = value,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private data class MenuItemData(
    val label: String,
    val icon: ImageVector,
    val iconBg: Color,
    val iconTint: Color,
    val destination: AppScreen
)

/**
 * 4-column comfortable rounded tiles matching Design Master
 */
@Composable
private fun MenuGrid(
    terminology: BusinessTerminology = BusinessTerminology(),
    onNavigate: (AppScreen) -> Unit
) {
    val menus = listOf(
        MenuItemData(
            label = "${terminology.transactionLabel}\n(Kasir)",
            icon = Icons.Default.PointOfSale,
            iconBg = Color(0xFFE8F7EF),
            iconTint = Color(0xFF0B9F57),
            destination = AppScreen.POS
        ),
        MenuItemData(
            label = "${terminology.productLabel}\n& ${terminology.stockLabel}",
            icon = Icons.Default.Inventory2,
            iconBg = Color(0xFFE8F3FF),
            iconTint = Color(0xFF096DD9),
            destination = AppScreen.PRODUCTS
        ),
        MenuItemData(
            label = "${terminology.purchaseLabel}\n(Kulakan)",
            icon = Icons.Default.ShoppingBag,
            iconBg = Color(0xFFFFF7E6),
            iconTint = Color(0xFFD46B08),
            destination = AppScreen.PURCHASE
        ),
        MenuItemData(
            label = "${terminology.customerLabel}\n& Piutang",
            icon = Icons.Default.People,
            iconBg = Color(0xFFF0EDFE),
            iconTint = Color(0xFF722ED1),
            destination = AppScreen.CUSTOMERS
        ),
        MenuItemData(
            label = "${terminology.supplierLabel}\n& ${terminology.debtLabel}",
            icon = Icons.Default.LocalShipping,
            iconBg = Color(0xFFE6FFFB),
            iconTint = Color(0xFF08979C),
            destination = AppScreen.SUPPLIERS
        ),
        MenuItemData(
            label = "Uang\nKas",
            icon = Icons.Default.AccountBalanceWallet,
            iconBg = Color(0xFFFFF0F6),
            iconTint = Color(0xFFC41D7F),
            destination = AppScreen.CASH
        ),
        MenuItemData(
            label = "Laporan\nBisnis",
            icon = Icons.Default.Assessment,
            iconBg = Color(0xFFE6F7FF),
            iconTint = Color(0xFF1890FF),
            destination = AppScreen.REPORTS
        ),
        MenuItemData(
            label = "Pengaturan\nAplikasi",
            icon = Icons.Default.Settings,
            iconBg = Color(0xFFF5F5F5),
            iconTint = Color(0xFF595959),
            destination = AppScreen.SETTINGS
        )
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        menus.chunked(4).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { item ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFEDEDED)),
                        onClick = { onNavigate(item.destination) },
                        modifier = Modifier
                            .weight(1f)
                            .height(96.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 8.dp, horizontal = 3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(item.iconBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = item.iconTint,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(
                                text = item.label,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.TextPrimary,
                                textAlign = TextAlign.Center,
                                lineHeight = 12.sp,
                                maxLines = 2
                            )
                        }
                    }
                }
                // Fill remaining spaces in row if less than 4 items
                if (rowItems.size < 4) {
                    repeat(4 - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}


