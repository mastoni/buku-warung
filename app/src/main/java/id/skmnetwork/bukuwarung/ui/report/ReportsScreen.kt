package id.skmnetwork.bukuwarung.ui.report

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.skmnetwork.bukuwarung.data.local.dao.TopProductSummary
import id.skmnetwork.bukuwarung.ui.components.AppCard
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppShapes
import id.skmnetwork.bukuwarung.ui.theme.AppSpacing
import id.skmnetwork.bukuwarung.util.formatRupiah

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(reportViewModel: ReportViewModel) {
    val selectedPeriod by reportViewModel.selectedPeriod.collectAsStateWithLifecycle()
    var isRincianLabaExpanded by remember { mutableStateOf(false) }

    // 1. Sales & Returns
    val grossSales by reportViewModel.salesTotal.collectAsStateWithLifecycle()
    val salesReturnTotal by reportViewModel.salesReturnTotal.collectAsStateWithLifecycle()
    val netSalesTotal by reportViewModel.netSalesTotal.collectAsStateWithLifecycle()
    val salesCount by reportViewModel.salesCount.collectAsStateWithLifecycle()
    val salesReturnCount by reportViewModel.salesReturnCount.collectAsStateWithLifecycle()
    val itemsSoldTotal by reportViewModel.itemsSoldTotal.collectAsStateWithLifecycle()
    val cashSalesTotal by reportViewModel.cashSalesTotal.collectAsStateWithLifecycle()
    val creditSalesTotal by reportViewModel.creditSalesTotal.collectAsStateWithLifecycle()
    val topSellingProducts by reportViewModel.topSellingProducts.collectAsStateWithLifecycle()

    // 2. COGS & Profit
    val saleCogsTotal by reportViewModel.saleCogsTotal.collectAsStateWithLifecycle()
    val returnCogsTotal by reportViewModel.returnCogsTotal.collectAsStateWithLifecycle()
    val netCogsTotal by reportViewModel.netCogsTotal.collectAsStateWithLifecycle()
    val grossProfitTotal by reportViewModel.grossProfitTotal.collectAsStateWithLifecycle()
    val operatingExpenseTotal by reportViewModel.operatingExpenseTotal.collectAsStateWithLifecycle()
    val netProfitTotal by reportViewModel.netProfitTotal.collectAsStateWithLifecycle()

    // 3. Purchase
    val purchaseTotal by reportViewModel.purchaseTotal.collectAsStateWithLifecycle()
    val purchaseCount by reportViewModel.purchaseCount.collectAsStateWithLifecycle()
    val itemsPurchasedTotal by reportViewModel.itemsPurchasedTotal.collectAsStateWithLifecycle()

    // 4. Cash
    val cashIncomeTotal by reportViewModel.cashIncomeTotal.collectAsStateWithLifecycle()
    val cashExpenseTotal by reportViewModel.cashExpenseTotal.collectAsStateWithLifecycle()
    val netCashMovement by reportViewModel.netCashMovement.collectAsStateWithLifecycle()
    val currentCashBalance by reportViewModel.currentCashBalance.collectAsStateWithLifecycle()

    // 5. Position: Stock, Debt, Payable
    val totalStockValue by reportViewModel.totalStockValue.collectAsStateWithLifecycle()
    val totalOutstandingDebt by reportViewModel.totalOutstandingDebt.collectAsStateWithLifecycle()
    val totalOutstandingPayable by reportViewModel.totalOutstandingPayable.collectAsStateWithLifecycle()

    val grossSalesVal = grossSales ?: 0L
    val salesReturnVal = salesReturnTotal ?: 0L
    val opExpenseVal = operatingExpenseTotal ?: 0L

    // Calculate QRIS / Non-Cash sales if any
    val cashSales = cashSalesTotal ?: 0L
    val creditSales = creditSalesTotal ?: 0L
    val qrisSales = (grossSalesVal - cashSales - creditSales).coerceAtLeast(0L)

    val hasPeriodTransactions = salesCount > 0 || purchaseCount > 0 || (cashIncomeTotal ?: 0L) > 0L || (cashExpenseTotal ?: 0L) > 0L

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Laporan & Laba", fontWeight = FontWeight.Bold) })
        },
        containerColor = Color.White
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            // Period Filter Chips
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    items(ReportPeriod.entries.toTypedArray()) { period ->
                        val isSelected = period == selectedPeriod
                        Surface(
                            shape = AppShapes.ChipShape,
                            color = if (isSelected) AppColors.GreenPrimary else AppColors.SurfaceGray,
                            modifier = Modifier.clickable { reportViewModel.selectPeriod(period) }
                        ) {
                            Text(
                                text = period.label,
                                color = if (isSelected) Color.White else AppColors.TextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm)
                            )
                        }
                    }
                }
            }

            // Empty state notice banner if no activity in this period
            if (!hasPeriodTransactions) {
                item {
                    Surface(
                        shape = AppShapes.CardShape,
                        color = AppColors.SurfaceGray,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(AppSpacing.md)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = AppColors.TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(AppSpacing.sm))
                                Text(
                                    text = "Belum ada data laporan",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(Modifier.height(AppSpacing.xs))
                            Text(
                                text = "Mulai catat penjualan dan pengeluaran untuk melihat perkembangan usaha Anda.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.TextSecondary
                            )
                        }
                    }
                }
            }

            // ==============================================================
            // SECTION 1: LABA RUGI SEDERHANA (MAIN CARD)
            // ==============================================================
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = AppColors.GreenPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(AppSpacing.xs))
                    Text(
                        text = "Laba Rugi Sederhana",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(Modifier.height(AppSpacing.xs))

                AppCard(backgroundColor = AppColors.GreenLight) {
                    Column(Modifier.padding(AppSpacing.md)) {
                        // 1. Penjualan Bersih
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Penjualan Bersih",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                formatRupiah(netSalesTotal),
                                fontWeight = FontWeight.Bold,
                                color = AppColors.GreenDark,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }

                        Spacer(Modifier.height(AppSpacing.xs))

                        // 2. HPP / Modal Barang Terjual
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "HPP / Modal Barang Terjual",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppColors.TextSecondary
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "-${formatRupiah(netCogsTotal)}",
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Spacer(Modifier.height(AppSpacing.xs))

                        // 3. Laba Kotor
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Laba Kotor",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                formatRupiah(grossProfitTotal),
                                fontWeight = FontWeight.Bold,
                                color = if (grossProfitTotal >= 0) AppColors.GreenPrimary else AppColors.RedExpense,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Spacer(Modifier.height(AppSpacing.xs))

                        // 4. Pengeluaran Operasional
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Pengeluaran Operasional",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppColors.TextSecondary
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "-${formatRupiah(opExpenseVal)}",
                                fontWeight = FontWeight.SemiBold,
                                color = if (opExpenseVal > 0) AppColors.RedExpense else AppColors.TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Spacer(Modifier.height(AppSpacing.sm))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.8f))
                        Spacer(Modifier.height(AppSpacing.sm))

                        // 5. Laba Bersih (Highlighted Box)
                        Surface(
                            shape = AppShapes.CardShape,
                            color = if (netProfitTotal >= 0) Color.White else Color(0xFFFFE8E8),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(AppSpacing.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Laba Bersih",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        "Laba Kotor - Operasional",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppColors.TextSecondary
                                    )
                                }
                                Text(
                                    formatRupiah(netProfitTotal),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (netProfitTotal >= 0) AppColors.GreenPrimary else AppColors.RedExpense
                                )
                            }
                        }

                        Spacer(Modifier.height(AppSpacing.sm))

                        // Expandable Trigger: Rincian Laba
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isRincianLabaExpanded = !isRincianLabaExpanded }
                                .padding(vertical = AppSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (isRincianLabaExpanded) "Sembunyikan Rincian Laba" else "Lihat Rincian Laba",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.GreenDark
                            )
                            Spacer(Modifier.width(AppSpacing.xs))
                            Icon(
                                imageVector = if (isRincianLabaExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = AppColors.GreenDark,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        AnimatedVisibility(visible = isRincianLabaExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = AppSpacing.sm),
                                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                            ) {
                                Surface(
                                    shape = AppShapes.CardShape,
                                    color = AppColors.SurfaceGray,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(AppSpacing.md),
                                        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                                    ) {
                                        Text(
                                            "Rincian Laba",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelLarge
                                        )

                                        Spacer(Modifier.height(AppSpacing.xs))

                                        // Penjualan Bruto
                                        Row(Modifier.fillMaxWidth()) {
                                            Text("Penjualan Bruto", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                            Text(formatRupiah(grossSalesVal), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                        }

                                        // Retur Penjualan
                                        Row(Modifier.fillMaxWidth()) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Retur Penjualan", style = MaterialTheme.typography.bodySmall, color = AppColors.RedExpense)
                                                if (salesReturnCount > 0) {
                                                    Text("$salesReturnCount kali retur", style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
                                                }
                                            }
                                            Text(
                                                if (salesReturnVal > 0) "-${formatRupiah(salesReturnVal)}" else formatRupiah(0L),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (salesReturnVal > 0) AppColors.RedExpense else AppColors.TextPrimary
                                            )
                                        }

                                        HorizontalDivider(Modifier.padding(vertical = 2.dp))

                                        // Penjualan Bersih
                                        Row(Modifier.fillMaxWidth()) {
                                            Text("Penjualan Bersih", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                            Text(formatRupiah(netSalesTotal), fontWeight = FontWeight.Bold, color = AppColors.GreenDark, style = MaterialTheme.typography.bodySmall)
                                        }

                                        Spacer(Modifier.height(AppSpacing.xs))

                                        // HPP / Modal Barang Terjual
                                        Row(Modifier.fillMaxWidth()) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("HPP / Modal Barang Terjual", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                                                Text("HPP Penjualan - HPP Retur", style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
                                            }
                                            Text(
                                                if (netCogsTotal > 0) "-${formatRupiah(netCogsTotal)}" else formatRupiah(0L),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        HorizontalDivider(Modifier.padding(vertical = 2.dp))

                                        // Laba Kotor
                                        Row(Modifier.fillMaxWidth()) {
                                            Text("Laba Kotor", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                            Text(
                                                formatRupiah(grossProfitTotal),
                                                fontWeight = FontWeight.Bold,
                                                color = if (grossProfitTotal >= 0) AppColors.GreenPrimary else AppColors.RedExpense,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }

                                        Spacer(Modifier.height(AppSpacing.xs))

                                        // Pengeluaran Operasional
                                        Row(Modifier.fillMaxWidth()) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Pengeluaran Operasional", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                                                Text("Beban listrik, sewa, gaji, operasional", style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
                                            }
                                            Text(
                                                if (opExpenseVal > 0) "-${formatRupiah(opExpenseVal)}" else formatRupiah(0L),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (opExpenseVal > 0) AppColors.RedExpense else AppColors.TextPrimary
                                            )
                                        }

                                        HorizontalDivider(Modifier.padding(vertical = 2.dp))

                                        // Laba Bersih
                                        Row(Modifier.fillMaxWidth()) {
                                            Text("Laba Bersih", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                            Text(
                                                formatRupiah(netProfitTotal),
                                                fontWeight = FontWeight.Bold,
                                                color = if (netProfitTotal >= 0) AppColors.GreenPrimary else AppColors.RedExpense,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==============================================================
            // SECTION 2: POSISI KEUANGAN (FINANCIAL POSITION)
            // ==============================================================
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = AppColors.BlueCash,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(AppSpacing.xs))
                    Text(
                        text = "Posisi Keuangan",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(Modifier.height(AppSpacing.xs))

                AppCard(backgroundColor = Color(0xFFF0F4F8)) {
                    Column(Modifier.padding(AppSpacing.md)) {
                        // 2x2 Grid of Financial Position
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                        ) {
                            // Saldo Kas
                            Surface(
                                shape = AppShapes.CardShape,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(Modifier.padding(AppSpacing.sm)) {
                                    Text("Saldo Kas", style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        formatRupiah(currentCashBalance ?: 0L),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = AppColors.GreenDark
                                    )
                                }
                            }

                            // Nilai Stok
                            Surface(
                                shape = AppShapes.CardShape,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(Modifier.padding(AppSpacing.sm)) {
                                    Text("Nilai Stok Modal", style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        formatRupiah(totalStockValue ?: 0L),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = AppColors.TextPrimary
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(AppSpacing.sm))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                        ) {
                            // Piutang Pelanggan
                            Surface(
                                shape = AppShapes.CardShape,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(Modifier.padding(AppSpacing.sm)) {
                                    Text("Piutang Pelanggan", style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        formatRupiah(totalOutstandingDebt ?: 0L),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if ((totalOutstandingDebt ?: 0L) > 0) AppColors.RedExpense else AppColors.TextPrimary
                                    )
                                }
                            }

                            // Hutang Supplier
                            Surface(
                                shape = AppShapes.CardShape,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(Modifier.padding(AppSpacing.sm)) {
                                    Text("Hutang Supplier", style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        formatRupiah(totalOutstandingPayable ?: 0L),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if ((totalOutstandingPayable ?: 0L) > 0) AppColors.RedExpense else AppColors.TextPrimary
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(AppSpacing.sm))
                        Text(
                            text = "Catatan: Saldo kas dan nilai stok terpisah dari perhitungan laba usaha.",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.TextSecondary
                        )
                    }
                }
            }

            // ==============================================================
            // SECTION 3: AKTIVITAS TRANSAKSI (PENJUALAN, PEMBELIAN, KAS)
            // ==============================================================
            item {
                Text(
                    text = "Aktivitas Transaksi (${selectedPeriod.label})",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(AppSpacing.xs))

                // Detail Penjualan Card
                AppCard(backgroundColor = Color(0xFFF9FBF9)) {
                    Column(Modifier.padding(AppSpacing.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Receipt, null, tint = AppColors.GreenPrimary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(AppSpacing.xs))
                            Text("Aktivitas Penjualan", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.weight(1f))
                            Text(formatRupiah(grossSalesVal), fontWeight = FontWeight.Bold, color = AppColors.GreenPrimary)
                        }
                        Spacer(Modifier.height(AppSpacing.sm))
                        Row {
                            Text("Transaksi: $salesCount kali", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary, modifier = Modifier.weight(1f))
                            val soldQty = itemsSoldTotal ?: 0.0
                            val qtyText = if (soldQty % 1.0 == 0.0) "${soldQty.toInt()} pcs" else "$soldQty pcs"
                            Text("Item Terjual: $qtyText", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                        }
                        Spacer(Modifier.height(AppSpacing.xs))
                        Row {
                            Text("Tunai: ${formatRupiah(cashSales)}", style = MaterialTheme.typography.bodySmall, color = AppColors.GreenDark, modifier = Modifier.weight(1f))
                            Text("Hutang: ${formatRupiah(creditSales)}", style = MaterialTheme.typography.bodySmall, color = AppColors.RedExpense)
                        }
                        if (qrisSales > 0L) {
                            Spacer(Modifier.height(AppSpacing.xs))
                            Text("QRIS: ${formatRupiah(qrisSales)}", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                        }
                        if (salesReturnVal > 0L) {
                            Spacer(Modifier.height(AppSpacing.xs))
                            Text("Retur: -${formatRupiah(salesReturnVal)} ($salesReturnCount transaksi)", style = MaterialTheme.typography.bodySmall, color = AppColors.RedExpense)
                        }
                    }
                }
            }

            // Detail Pembelian / Kulakan Card
            item {
                AppCard(backgroundColor = Color(0xFFFFF9F9)) {
                    Column(Modifier.padding(AppSpacing.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ShoppingCart, null, tint = AppColors.RedExpense, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(AppSpacing.xs))
                            Text("Aktivitas Pembelian (Kulakan)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.weight(1f))
                            Text(formatRupiah(purchaseTotal ?: 0L), fontWeight = FontWeight.Bold, color = AppColors.RedExpense)
                        }
                        Spacer(Modifier.height(AppSpacing.sm))
                        Row {
                            Text("Transaksi: $purchaseCount kali", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary, modifier = Modifier.weight(1f))
                            val purchasedQty = itemsPurchasedTotal ?: 0.0
                            val qtyText = if (purchasedQty % 1.0 == 0.0) "${purchasedQty.toInt()} pcs" else "$purchasedQty pcs"
                            Text("Item Dibeli: $qtyText", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                        }
                    }
                }
            }

            // Detail Pergerakan Kas Card
            item {
                AppCard(backgroundColor = AppColors.BlueCash) {
                    Column(Modifier.padding(AppSpacing.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Pergerakan Kas Periode Ini", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.weight(1f))
                            Text(
                                formatRupiah(netCashMovement),
                                fontWeight = FontWeight.Bold,
                                color = if (netCashMovement >= 0) AppColors.GreenPrimary else AppColors.RedExpense
                            )
                        }
                        Spacer(Modifier.height(AppSpacing.xs))
                        Row {
                            Text("Pemasukan Kas: ${formatRupiah(cashIncomeTotal ?: 0L)}", style = MaterialTheme.typography.bodySmall, color = AppColors.GreenDark, modifier = Modifier.weight(1f))
                            Text("Pengeluaran Kas: ${formatRupiah(cashExpenseTotal ?: 0L)}", style = MaterialTheme.typography.bodySmall, color = AppColors.RedExpense)
                        }
                    }
                }
            }

            // ==============================================================
            // SECTION 4: PRODUK TERLARIS
            // ==============================================================
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = AppColors.OrangeWarning,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(AppSpacing.xs))
                    Text("Produk Terlaris", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(AppSpacing.xs))
                AppCard(backgroundColor = Color(0xFFF9FBF9)) {
                    Column(Modifier.padding(AppSpacing.md)) {
                        if (topSellingProducts.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = AppSpacing.md),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Belum ada produk terjual pada periode ini.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.TextSecondary
                                )
                            }
                        } else {
                            topSellingProducts.forEachIndexed { index, product ->
                                TopProductItemRow(
                                    rank = index + 1,
                                    item = product
                                )
                                if (index < topSellingProducts.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = AppSpacing.xs),
                                        color = AppColors.SurfaceGray
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(AppSpacing.lg))
            }
        }
    }
}

@Composable
private fun TopProductItemRow(
    rank: Int,
    item: TopProductSummary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = when (rank) {
                1 -> Color(0xFFFFD700)
                2 -> Color(0xFFC0C0C0)
                3 -> Color(0xFFCD7F32)
                else -> AppColors.SurfaceGray
            },
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "$rank",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (rank <= 3) Color.White else AppColors.TextSecondary
                )
            }
        }
        Spacer(Modifier.width(AppSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.productName,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium
            )
            val qtyText = if (item.totalQuantity % 1.0 == 0.0) "${item.totalQuantity.toInt()} pcs" else "${item.totalQuantity} pcs"
            Text(
                text = "Terjual: $qtyText",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextSecondary
            )
        }
        Text(
            text = formatRupiah(item.totalRevenue),
            fontWeight = FontWeight.Bold,
            color = AppColors.GreenPrimary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
