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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
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
 * Phase 6B.2 — Home / Beranda Screen (Design Master Alignment)
 *
 * Structure:
 * 1. Store Identity Header (Store Avatar, Shop Name, Subtitle, Status)
 * 2. Greeting & Dynamic Indonesian Date
 * 3. Financial Summary 2x2 Grid (Penjualan, Pengeluaran, Saldo Kas, Stok Menipis)
 * 4. Menu Utama (8 compact, colorful pastel icon tiles in 4-column grid)
 * 5. Low Stock Notice (if applicable)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ProductViewModel,
    userSettings: UserSettings = UserSettings(),
    shopName: String = userSettings.shopName,
    onNavigate: (AppScreen) -> Unit
) {
    val dbProducts by viewModel.products.collectAsStateWithLifecycle()
    val cashBalance by viewModel.cashBalance.collectAsStateWithLifecycle()
    val todaySalesTotal by viewModel.todaySalesTotal.collectAsStateWithLifecycle()
    val todayExpenseTotal by viewModel.todayExpenseTotal.collectAsStateWithLifecycle()

    val lowStockCount = if (userSettings.lowStockAlertEnabled) {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        // Store Avatar / Logo
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AppColors.GreenPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storefront,
                                contentDescription = "Store Avatar",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(Modifier.width(10.dp))

                        Column {
                            Text(
                                text = effectiveShopName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = AppColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Pembukuan Warung Kecil",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.TextSecondary
                            )
                        }
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = AppColors.GreenLight,
                        border = BorderStroke(1.dp, Color(0xFFCCE8D7)),
                        modifier = Modifier.padding(end = AppSpacing.sm)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Online Status",
                                tint = AppColors.GreenPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Aktif",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
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
                .padding(horizontal = AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            // ==========================================
            // 1. GREETING & DATE BANNER
            // ==========================================
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                ) {
                    Text(
                        text = greeting,
                        fontSize = 13.sp,
                        color = AppColors.TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = "$effectiveShopName 👋",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = AppColors.GreenDark
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .border(width = 1.dp, color = Color(0xFFE8E8E8), shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Tanggal",
                            tint = AppColors.TextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = currentDateStr,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppColors.TextSecondary
                        )
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
                    showLowStockAlert = userSettings.lowStockAlertEnabled
                )
            }

            // ==========================================
            // 3. MENU UTAMA HEADER & GRID
            // ==========================================
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Menu Utama",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                    Text(
                        text = "8 Fitur",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppColors.TextSecondary
                    )
                }
            }

            item {
                MenuGrid(onNavigate = onNavigate)
            }

            // ==========================================
            // 4. LOW STOCK WARNING BANNER (IF ACTIVE)
            // ==========================================
            if (userSettings.lowStockAlertEnabled && lowStockCount > 0) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7E6)),
                        border = BorderStroke(1.dp, Color(0xFFFFD591)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate(AppScreen.PRODUCTS) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFE7BA)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = "Peringatan Stok",
                                    tint = Color(0xFFD46B08),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Barang Hampir Habis",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    color = Color(0xFFD46B08)
                                )
                                Text(
                                    text = "$lowStockCount produk mencapai batas minimum stok",
                                    fontSize = 11.sp,
                                    color = AppColors.TextSecondary
                                )
                            }
                            Text(
                                text = "Lihat >",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD46B08)
                            )
                        }
                    }
                    Spacer(Modifier.height(AppSpacing.xs))
                }
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
    showLowStockAlert: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SummaryCard(
                title = "Penjualan Hari Ini",
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
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                title = "Stok Menipis",
                value = if (showLowStockAlert) "$lowStockCount barang" else "Nonaktif",
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
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
 * 4-column compact rounded tiles matching Design Master
 */
@Composable
private fun MenuGrid(onNavigate: (AppScreen) -> Unit) {
    val menus = listOf(
        MenuItemData(
            label = "Jualan",
            icon = Icons.Default.PointOfSale,
            iconBg = Color(0xFFE8F7EF),
            iconTint = Color(0xFF0B9F57),
            destination = AppScreen.POS
        ),
        MenuItemData(
            label = "Produk\n& Stok",
            icon = Icons.Default.Inventory2,
            iconBg = Color(0xFFE8F3FF),
            iconTint = Color(0xFF096DD9),
            destination = AppScreen.PRODUCTS
        ),
        MenuItemData(
            label = "Pembelian\n(Kulakan)",
            icon = Icons.Default.ShoppingBag,
            iconBg = Color(0xFFFFF7E6),
            iconTint = Color(0xFFD46B08),
            destination = AppScreen.PURCHASE
        ),
        MenuItemData(
            label = "Pelanggan\n& Piutang",
            icon = Icons.Default.People,
            iconBg = Color(0xFFF0EDFE),
            iconTint = Color(0xFF722ED1),
            destination = AppScreen.CUSTOMERS
        ),
        MenuItemData(
            label = "Supplier\n& Hutang",
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

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        menus.chunked(4).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowItems.forEach { item ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFEDEDED)),
                        onClick = { onNavigate(item.destination) },
                        modifier = Modifier
                            .weight(1f)
                            .height(88.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 6.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(item.iconBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = item.iconTint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = item.label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.TextPrimary,
                                textAlign = TextAlign.Center,
                                lineHeight = 11.sp,
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
