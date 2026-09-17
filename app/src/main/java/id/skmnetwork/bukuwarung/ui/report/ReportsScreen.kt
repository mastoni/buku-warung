package id.skmnetwork.bukuwarung.ui.report

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.skmnetwork.bukuwarung.data.local.dao.TopProductSummary
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.pdf.PdfReportGenerator
import id.skmnetwork.bukuwarung.pdf.PdfShareManager
import id.skmnetwork.bukuwarung.pdf.reports.BusinessSummaryPdfBuilder
import id.skmnetwork.bukuwarung.pdf.reports.CustomerDebtReportPdfBuilder
import id.skmnetwork.bukuwarung.pdf.reports.DebtReportMode
import id.skmnetwork.bukuwarung.pdf.reports.ProductReportPdfBuilder
import id.skmnetwork.bukuwarung.pdf.reports.PurchaseReportPdfBuilder
import id.skmnetwork.bukuwarung.pdf.reports.SalesReportPdfBuilder
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppSpacing
import id.skmnetwork.bukuwarung.util.formatRupiah
import id.skmnetwork.bukuwarung.domain.business.BusinessTaxonomyRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ReportsScreen(
    reportViewModel: ReportViewModel,
    userPreferencesRepository: UserPreferencesRepository? = null,
    userSettings: UserSettings? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedPeriod by reportViewModel.selectedPeriod.collectAsStateWithLifecycle()
    var isRincianLabaExpanded by remember { mutableStateOf(false) }

    val resolvedProfile = remember(userSettings?.primaryBusinessType, userSettings?.secondaryActivities) {
        BusinessTaxonomyRegistry.resolve(
            userSettings?.primaryBusinessType,
            userSettings?.secondaryActivities
        )
    }
    val terminology = resolvedProfile.terminology
    val productLabel = terminology.productLabel
    val transactionLabel = terminology.transactionLabel
    val purchaseLabel = terminology.purchaseLabel
    val customerLabel = terminology.customerLabel
    val supplierLabel = terminology.supplierLabel
    val debtLabel = terminology.debtLabel
    val stockLabel = terminology.stockLabel

    // PDF Export State
    var isGeneratingPdf by remember { mutableStateOf(false) }
    var generatedPdfFile by remember { mutableStateOf<File?>(null) }
    var showPdfSuccessDialog by remember { mutableStateOf(false) }
    var showReportTypeDialog by remember { mutableStateOf(false) }
    var currentPdfTitle by remember { mutableStateOf("Ringkasan Usaha") }
    var pdfErrorMessage by remember { mutableStateOf<String?>(null) }

    fun generateBusinessSummaryPdf() {
        if (isGeneratingPdf) return
        isGeneratingPdf = true
        currentPdfTitle = "Ringkasan Usaha"
        scope.launch {
            try {
                val settings = userSettings ?: (userPreferencesRepository?.userSettings?.first() ?: UserSettings())
                val summaryData = reportViewModel.buildBusinessSummaryData(settings, selectedPeriod)
                val doc = BusinessSummaryPdfBuilder.build(summaryData)
                val generator = PdfReportGenerator(context)
                val result = generator.generatePdf(doc)
                if (result.isSuccess) {
                    generatedPdfFile = result.getOrThrow()
                    showPdfSuccessDialog = true
                } else {
                    pdfErrorMessage = result.exceptionOrNull()?.localizedMessage ?: "Gagal membuat PDF"
                }
            } catch (e: Exception) {
                pdfErrorMessage = e.localizedMessage ?: "Terjadi kesalahan saat memproses laporan"
            } finally {
                isGeneratingPdf = false
            }
        }
    }

    fun generateSalesReportPdf() {
        if (isGeneratingPdf) return
        isGeneratingPdf = true
        currentPdfTitle = "Laporan $transactionLabel"
        scope.launch {
            try {
                val settings = userSettings ?: (userPreferencesRepository?.userSettings?.first() ?: UserSettings())
                val salesData = reportViewModel.buildSalesReportData(settings, selectedPeriod)
                val doc = SalesReportPdfBuilder.build(salesData)
                val generator = PdfReportGenerator(context)
                val result = generator.generatePdf(doc)
                if (result.isSuccess) {
                    generatedPdfFile = result.getOrThrow()
                    showPdfSuccessDialog = true
                } else {
                    pdfErrorMessage = result.exceptionOrNull()?.localizedMessage ?: "Gagal membuat PDF"
                }
            } catch (e: Exception) {
                pdfErrorMessage = e.localizedMessage ?: "Terjadi kesalahan saat memproses laporan"
            } finally {
                isGeneratingPdf = false
            }
        }
    }

    fun generateProductReportPdf() {
        if (isGeneratingPdf) return
        isGeneratingPdf = true
        currentPdfTitle = "Laporan $productLabel"
        scope.launch {
            try {
                val settings = userSettings ?: (userPreferencesRepository?.userSettings?.first() ?: UserSettings())
                val productData = reportViewModel.buildProductReportData(settings, selectedPeriod)
                val doc = ProductReportPdfBuilder.build(productData)
                val generator = PdfReportGenerator(context)
                val result = generator.generatePdf(doc)
                if (result.isSuccess) {
                    generatedPdfFile = result.getOrThrow()
                    showPdfSuccessDialog = true
                } else {
                    pdfErrorMessage = result.exceptionOrNull()?.localizedMessage ?: "Gagal membuat PDF"
                }
            } catch (e: Exception) {
                pdfErrorMessage = e.localizedMessage ?: "Terjadi kesalahan saat memproses laporan"
            } finally {
                isGeneratingPdf = false
            }
        }
    }

    fun generatePurchaseReportPdf() {
        if (isGeneratingPdf) return
        isGeneratingPdf = true
        currentPdfTitle = "Laporan $purchaseLabel"
        scope.launch {
            try {
                val settings = userSettings ?: (userPreferencesRepository?.userSettings?.first() ?: UserSettings())
                val purchaseData = reportViewModel.buildPurchaseReportData(settings, selectedPeriod)
                val doc = PurchaseReportPdfBuilder.build(purchaseData)
                val generator = PdfReportGenerator(context)
                val result = generator.generatePdf(doc)
                if (result.isSuccess) {
                    generatedPdfFile = result.getOrThrow()
                    showPdfSuccessDialog = true
                } else {
                    pdfErrorMessage = result.exceptionOrNull()?.localizedMessage ?: "Gagal membuat PDF"
                }
            } catch (e: Exception) {
                pdfErrorMessage = e.localizedMessage ?: "Terjadi kesalahan saat memproses laporan"
            } finally {
                isGeneratingPdf = false
            }
        }
    }

    fun generateCustomerDebtReportPdf(mode: DebtReportMode = DebtReportMode.CURRENT_OUTSTANDING) {
        if (isGeneratingPdf) return
        isGeneratingPdf = true
        currentPdfTitle = if (mode == DebtReportMode.CURRENT_OUTSTANDING) "Piutang Aktif" else "Mutasi Piutang"
        scope.launch {
            try {
                val settings = userSettings ?: (userPreferencesRepository?.userSettings?.first() ?: UserSettings())
                val debtData = reportViewModel.buildCustomerDebtReportData(settings, mode = mode, period = selectedPeriod)
                val doc = CustomerDebtReportPdfBuilder.build(debtData)
                val generator = PdfReportGenerator(context)
                val result = generator.generatePdf(doc)
                if (result.isSuccess) {
                    generatedPdfFile = result.getOrThrow()
                    showPdfSuccessDialog = true
                } else {
                    pdfErrorMessage = result.exceptionOrNull()?.localizedMessage ?: "Gagal membuat PDF"
                }
            } catch (e: Exception) {
                pdfErrorMessage = e.localizedMessage ?: "Terjadi kesalahan saat memproses laporan"
            } finally {
                isGeneratingPdf = false
            }
        }
    }

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
            Surface(
                color = Color.White,
                shadowElevation = 0.5.dp
            ) {
                ReportsTopHeader(
                    isGeneratingPdf = isGeneratingPdf,
                    onExportPdf = { showReportTypeDialog = true }
                )
            }
        },
        containerColor = Color.White
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ==========================================
            // PERIOD FILTER CHIPS
            // ==========================================
            item {
                Spacer(Modifier.height(2.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(ReportPeriod.entries.toTypedArray()) { period ->
                        val isSelected = period == selectedPeriod
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) AppColors.GreenPrimary else Color(0xFFF4F6F4),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) AppColors.GreenPrimary else Color(0xFFE0E5E0)
                            ),
                            modifier = Modifier.clickable { reportViewModel.selectPeriod(period) }
                        ) {
                            Text(
                                text = period.label,
                                color = if (isSelected) Color.White else AppColors.TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // Empty state notice banner if no activity in this period
            if (!hasPeriodTransactions) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF9FBF9),
                        border = BorderStroke(1.dp, Color(0xFFEFF3F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE8F5E9)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = AppColors.GreenPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Belum Ada Transaksi Periode Ini",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = AppColors.TextPrimary
                                    )
                                )
                                Text(
                                    text = "Data laba rugi dan ringkasan aktivitas masih kosong untuk periode yang dipilih.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        color = AppColors.TextSecondary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // ==============================================================
            // SECTION 1: LABA RUGI SEDERHANA (MAIN FOCAL POINT CARD)
            // ==============================================================
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFE8F5E9),
                    border = BorderStroke(1.dp, Color(0xFFC8E6C9)),
                    shadowElevation = 0.5.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = AppColors.GreenPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Laba Rugi Sederhana",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp,
                                    color = AppColors.TextPrimary
                                )
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        // 1. Penjualan Bersih
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "$transactionLabel Bersih",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = AppColors.TextPrimary
                                )
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                formatRupiah(netSalesTotal),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp,
                                    color = AppColors.GreenDark
                                )
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        // 2. HPP / Modal Barang Terjual
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "HPP / Modal $productLabel Terjual",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 12.sp,
                                    color = AppColors.TextSecondary
                                )
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "-${formatRupiah(netCogsTotal)}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp,
                                    color = AppColors.TextSecondary
                                )
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        // 3. Laba Kotor
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Laba Kotor",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = AppColors.TextPrimary
                                )
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                formatRupiah(grossProfitTotal),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (grossProfitTotal >= 0) AppColors.GreenPrimary else AppColors.RedExpense
                                )
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        // 4. Pengeluaran Operasional
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Pengeluaran Operasional",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 12.sp,
                                    color = AppColors.TextSecondary
                                )
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "-${formatRupiah(opExpenseVal)}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp,
                                    color = if (opExpenseVal > 0) AppColors.RedExpense else AppColors.TextSecondary
                                )
                            )
                        }

                        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Color(0xFFC8E6C9))

                        // 5. Laba Bersih
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Laba Bersih",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = AppColors.TextPrimary
                                    )
                                )
                                Text(
                                    "Laba Kotor - Operasional",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        color = AppColors.TextSecondary
                                    )
                                )
                            }
                            Text(
                                formatRupiah(netProfitTotal),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.5.sp,
                                    color = if (netProfitTotal >= 0) AppColors.GreenPrimary else AppColors.RedExpense
                                )
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        // Expandable Trigger: Rincian Laba
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isRincianLabaExpanded = !isRincianLabaExpanded }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (isRincianLabaExpanded) "Sembunyikan Rincian Laba" else "Lihat Rincian Laba",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = AppColors.GreenDark
                                )
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = if (isRincianLabaExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = AppColors.GreenDark,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        AnimatedVisibility(visible = isRincianLabaExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Color(0xFFC8E6C9)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            "Rincian Laba",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, color = AppColors.TextPrimary)
                                        )

                                        Spacer(Modifier.height(2.dp))

                                        // Penjualan Bruto
                                        Row(Modifier.fillMaxWidth()) {
                                            Text("$transactionLabel Bruto", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), modifier = Modifier.weight(1f))
                                            Text(formatRupiah(grossSalesVal), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
                                        }

                                        // Retur Penjualan
                                        Row(Modifier.fillMaxWidth()) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Retur $transactionLabel", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, color = AppColors.RedExpense))
                                                if (salesReturnCount > 0) {
                                                    Text("$salesReturnCount kali retur", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, color = AppColors.TextSecondary))
                                                }
                                            }
                                            Text(
                                                if (salesReturnVal > 0) "-${formatRupiah(salesReturnVal)}" else formatRupiah(0L),
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (salesReturnVal > 0) AppColors.RedExpense else AppColors.TextPrimary
                                                )
                                            )
                                        }

                                        HorizontalDivider(Modifier.padding(vertical = 2.dp), color = Color(0xFFEFF3F0))

                                        // Penjualan Bersih
                                        Row(Modifier.fillMaxWidth()) {
                                            Text("$transactionLabel Bersih", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), modifier = Modifier.weight(1f))
                                            Text(formatRupiah(netSalesTotal), fontWeight = FontWeight.Bold, color = AppColors.GreenDark, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
                                        }

                                        Spacer(Modifier.height(2.dp))

                                        // HPP / Modal Barang Terjual
                                        Row(Modifier.fillMaxWidth()) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("HPP / Modal $productLabel Terjual", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, color = AppColors.TextSecondary))
                                                Text("HPP $transactionLabel - HPP Retur", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, color = AppColors.TextSecondary))
                                            }
                                            Text(
                                                if (netCogsTotal > 0) "-${formatRupiah(netCogsTotal)}" else formatRupiah(0L),
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            )
                                        }

                                        HorizontalDivider(Modifier.padding(vertical = 2.dp), color = Color(0xFFEFF3F0))

                                        // Laba Kotor
                                        Row(Modifier.fillMaxWidth()) {
                                            Text("Laba Kotor", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), modifier = Modifier.weight(1f))
                                            Text(
                                                formatRupiah(grossProfitTotal),
                                                fontWeight = FontWeight.Bold,
                                                color = if (grossProfitTotal >= 0) AppColors.GreenPrimary else AppColors.RedExpense,
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                                            )
                                        }

                                        Spacer(Modifier.height(2.dp))

                                        // Pengeluaran Operasional
                                        Row(Modifier.fillMaxWidth()) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Pengeluaran Operasional", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, color = AppColors.TextSecondary))
                                                Text("Beban listrik, sewa, gaji, operasional", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, color = AppColors.TextSecondary))
                                            }
                                            Text(
                                                if (opExpenseVal > 0) "-${formatRupiah(opExpenseVal)}" else formatRupiah(0L),
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (opExpenseVal > 0) AppColors.RedExpense else AppColors.TextPrimary
                                                )
                                            )
                                        }

                                        HorizontalDivider(Modifier.padding(vertical = 2.dp), color = Color(0xFFEFF3F0))

                                        // Laba Bersih
                                        Row(Modifier.fillMaxWidth()) {
                                            Text("Laba Bersih", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp), modifier = Modifier.weight(1f))
                                            Text(
                                                formatRupiah(netProfitTotal),
                                                fontWeight = FontWeight.Bold,
                                                color = if (netProfitTotal >= 0) AppColors.GreenPrimary else AppColors.RedExpense,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp)
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
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Posisi Keuangan",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = AppColors.TextPrimary
                        )
                    )
                }

                Spacer(Modifier.height(5.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Saldo Kas
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF0F4F8),
                        border = BorderStroke(1.dp, Color(0xFFDCE4EC)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text("Saldo Kas", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, color = AppColors.TextSecondary))
                            Spacer(Modifier.height(3.dp))
                            Text(
                                formatRupiah(currentCashBalance ?: 0L),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = AppColors.GreenDark
                                )
                            )
                        }
                    }

                    // Nilai Stok Modal
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF0F4F8),
                        border = BorderStroke(1.dp, Color(0xFFDCE4EC)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text("Nilai $stockLabel Modal", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, color = AppColors.TextSecondary))
                            Spacer(Modifier.height(3.dp))
                            Text(
                                formatRupiah(totalStockValue ?: 0L),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = AppColors.TextPrimary
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Piutang Pelanggan
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF0F4F8),
                        border = BorderStroke(1.dp, Color(0xFFDCE4EC)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text("Piutang $customerLabel", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, color = AppColors.TextSecondary))
                            Spacer(Modifier.height(3.dp))
                            Text(
                                formatRupiah(totalOutstandingDebt ?: 0L),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = if ((totalOutstandingDebt ?: 0L) > 0) AppColors.RedExpense else AppColors.TextPrimary
                                )
                            )
                        }
                    }

                    // Hutang Supplier
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF0F4F8),
                        border = BorderStroke(1.dp, Color(0xFFDCE4EC)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text("$debtLabel $supplierLabel", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, color = AppColors.TextSecondary))
                            Spacer(Modifier.height(3.dp))
                            Text(
                                formatRupiah(totalOutstandingPayable ?: 0L),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = if ((totalOutstandingPayable ?: 0L) > 0) AppColors.RedExpense else AppColors.TextPrimary
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { generateCustomerDebtReportPdf(DebtReportMode.CURRENT_OUTSTANDING) },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AppColors.GreenPrimary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.GreenPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                ) {
                    Icon(Icons.Default.Description, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cetak PDF Piutang Aktif", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }

            // ==============================================================
            // SECTION 3: AKTIVITAS TRANSAKSI (PENJUALAN, PEMBELIAN, KAS)
            // ==============================================================
            item {
                Text(
                    text = "Aktivitas Transaksi (${selectedPeriod.label})",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = AppColors.TextPrimary
                    )
                )
                Spacer(Modifier.height(4.dp))

                // Detail Penjualan Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF9FBF9),
                    border = BorderStroke(1.dp, Color(0xFFEFF3F0)),
                    shadowElevation = 0.5.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE8F5E9)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Receipt, null, tint = AppColors.GreenPrimary, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("Aktivitas $transactionLabel", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp))
                            Spacer(Modifier.weight(1f))
                            Text(formatRupiah(grossSalesVal), fontWeight = FontWeight.Bold, color = AppColors.GreenPrimary, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row {
                            Text("$transactionLabel: $salesCount kali", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, color = AppColors.TextSecondary), modifier = Modifier.weight(1f))
                            val soldQty = itemsSoldTotal ?: 0.0
                            val qtyText = if (soldQty % 1.0 == 0.0) "${soldQty.toInt()} pcs" else "$soldQty pcs"
                            Text("$productLabel Terjual: $qtyText", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, color = AppColors.TextSecondary))
                        }
                        Spacer(Modifier.height(4.dp))
                        Row {
                            Text("Tunai: ${formatRupiah(cashSales)}", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, color = AppColors.GreenDark), modifier = Modifier.weight(1f))
                            Text("$debtLabel: ${formatRupiah(creditSales)}", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, color = AppColors.RedExpense))
                        }
                        if (qrisSales > 0L) {
                            Spacer(Modifier.height(4.dp))
                            Text("QRIS: ${formatRupiah(qrisSales)}", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, color = AppColors.TextSecondary))
                        }
                        if (salesReturnVal > 0L) {
                            Spacer(Modifier.height(4.dp))
                            Text("Retur: -${formatRupiah(salesReturnVal)} ($salesReturnCount transaksi)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, color = AppColors.RedExpense))
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { generateSalesReportPdf() },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, AppColors.GreenPrimary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.GreenPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) {
                            Icon(Icons.Default.Description, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Cetak PDF Laporan $transactionLabel", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Detail Pembelian / Kulakan Card
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFFF9F9),
                    border = BorderStroke(1.dp, Color(0xFFFFEBEE)),
                    shadowElevation = 0.5.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFEBEE)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.ShoppingCart, null, tint = AppColors.RedExpense, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("Aktivitas $purchaseLabel", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp))
                            Spacer(Modifier.weight(1f))
                            Text(formatRupiah(purchaseTotal ?: 0L), fontWeight = FontWeight.Bold, color = AppColors.RedExpense, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row {
                            Text("Transaksi: $purchaseCount kali", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, color = AppColors.TextSecondary), modifier = Modifier.weight(1f))
                            val purchasedQty = itemsPurchasedTotal ?: 0.0
                            val qtyText = if (purchasedQty % 1.0 == 0.0) "${purchasedQty.toInt()} pcs" else "$purchasedQty pcs"
                            Text("$productLabel Dibeli: $qtyText", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, color = AppColors.TextSecondary))
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { generatePurchaseReportPdf() },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFE53935)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE53935)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) {
                            Icon(Icons.Default.ShoppingCart, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Cetak PDF Laporan $purchaseLabel", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Detail Pergerakan Kas Card
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF0F7FF),
                    border = BorderStroke(1.dp, Color(0xFFD9ECFF)),
                    shadowElevation = 0.5.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Pergerakan Kas Periode Ini", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp, color = AppColors.TextPrimary))
                            Spacer(Modifier.weight(1f))
                            Text(
                                formatRupiah(netCashMovement),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (netCashMovement >= 0) AppColors.GreenPrimary else AppColors.RedExpense
                                )
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row {
                            Text("Pemasukan Kas: ${formatRupiah(cashIncomeTotal ?: 0L)}", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, color = AppColors.GreenDark), modifier = Modifier.weight(1f))
                            Text("Pengeluaran Kas: ${formatRupiah(cashExpenseTotal ?: 0L)}", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, color = AppColors.RedExpense))
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
                    Spacer(Modifier.width(8.dp))
                    Text("$productLabel Terlaris", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AppColors.TextPrimary))
                }
                Spacer(Modifier.height(4.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF9FBF9),
                    border = BorderStroke(1.dp, Color(0xFFEFF3F0)),
                    shadowElevation = 0.5.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        if (topSellingProducts.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Belum ada ${productLabel.lowercase()} terjual pada periode ini.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, color = AppColors.TextSecondary)
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
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        color = Color(0xFFEFF3F0)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { generateProductReportPdf() },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, AppColors.GreenPrimary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.GreenPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) {
                            Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Cetak PDF Laporan $productLabel", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // ==========================================
    // REPORT TYPE CHOOSER DIALOG
    // ==========================================
    if (showReportTypeDialog) {
        AlertDialog(
            onDismissRequest = { showReportTypeDialog = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White,
            title = {
                Text(
                    text = "Pilih Jenis Laporan PDF",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = AppColors.TextPrimary
                    )
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Pilih jenis laporan yang ingin dicetak untuk periode ${selectedPeriod.label}:",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, color = AppColors.TextSecondary)
                    )
                    Spacer(Modifier.height(4.dp))

                    ReportChooserItem(
                        icon = Icons.Default.Assessment,
                        title = "Ringkasan Usaha",
                        subtitle = "Laba rugi, posisi kas, $stockLabel, $debtLabel/piutang",
                        onClick = {
                            showReportTypeDialog = false
                            generateBusinessSummaryPdf()
                        }
                    )

                    ReportChooserItem(
                        icon = Icons.Default.Receipt,
                        title = "Laporan $transactionLabel",
                        subtitle = "Daftar detail transaksi ${transactionLabel.lowercase()} periode ini",
                        onClick = {
                            showReportTypeDialog = false
                            generateSalesReportPdf()
                        }
                    )

                    ReportChooserItem(
                        icon = Icons.Default.Inventory2,
                        title = "Laporan $productLabel",
                        subtitle = "Omzet, HPP historical snapshot, laba kotor per ${productLabel.lowercase()}",
                        onClick = {
                            showReportTypeDialog = false
                            generateProductReportPdf()
                        }
                    )

                    ReportChooserItem(
                        icon = Icons.Default.ShoppingCart,
                        title = "Laporan $purchaseLabel",
                        subtitle = "Daftar transaksi ${purchaseLabel.lowercase()} ke ${supplierLabel.lowercase()}",
                        onClick = {
                            showReportTypeDialog = false
                            generatePurchaseReportPdf()
                        }
                    )

                    ReportChooserItem(
                        icon = Icons.Default.People,
                        title = "Laporan Piutang Aktif",
                        subtitle = "Daftar saldo piutang aktif yang belum lunas per saat ini",
                        onClick = {
                            showReportTypeDialog = false
                            generateCustomerDebtReportPdf(DebtReportMode.CURRENT_OUTSTANDING)
                        }
                    )

                    ReportChooserItem(
                        icon = Icons.Default.Receipt,
                        title = "Mutasi Piutang Periode",
                        subtitle = "Daftar piutang yang timbul pada periode ${selectedPeriod.label}",
                        onClick = {
                            showReportTypeDialog = false
                            generateCustomerDebtReportPdf(DebtReportMode.MUTATION_PERIOD)
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showReportTypeDialog = false }
                ) {
                    Text("Batal", color = AppColors.TextSecondary, fontSize = 13.sp)
                }
            }
        )
    }

    // ==========================================
    // PDF SUCCESS DIALOG
    // ==========================================
    if (showPdfSuccessDialog && generatedPdfFile != null) {
        val file = generatedPdfFile!!
        AlertDialog(
            onDismissRequest = { showPdfSuccessDialog = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White,
            icon = {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFE8F5E9),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AppColors.GreenPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = "PDF $currentPdfTitle Siap",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = AppColors.TextPrimary
                    )
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Laporan $currentPdfTitle periode ${selectedPeriod.label} berhasil dibuat dan disimpan di memori aplikasi.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, color = AppColors.TextSecondary)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = AppColors.TextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val shareIntent = PdfShareManager.createSharePdfIntent(context, file, "Bagikan Laporan $currentPdfTitle")
                        context.startActivity(shareIntent)
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.GreenPrimary)
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Bagikan PDF", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        val viewIntent = PdfShareManager.createViewPdfIntent(context, file)
                        context.startActivity(viewIntent)
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFE0E0E0))
                ) {
                    Text("Buka PDF", fontSize = 13.sp)
                }
            }
        )
    }

    // ==========================================
    // PDF ERROR DIALOG
    // ==========================================
    if (pdfErrorMessage != null) {
        AlertDialog(
            onDismissRequest = { pdfErrorMessage = null },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White,
            title = { Text("Gagal Membuat PDF", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = AppColors.TextPrimary) },
            text = { Text(pdfErrorMessage ?: "", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, color = AppColors.TextSecondary)) },
            confirmButton = {
                Button(
                    onClick = { pdfErrorMessage = null },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.GreenPrimary)
                ) {
                    Text("Tutup", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        )
    }
}

/**
 * Top Header matching Home/Kasir/Produk/Pembelian/Cash/Customers/Suppliers language.
 */
@Composable
private fun ReportsTopHeader(
    isGeneratingPdf: Boolean,
    onExportPdf: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AppColors.GreenPrimary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Assessment,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Laporan & Laba",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = AppColors.TextPrimary
                ),
                maxLines = 1
            )
            Text(
                text = "Pantau laba rugi usaha",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    color = AppColors.TextSecondary
                ),
                maxLines = 1
            )
        }

        Spacer(Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFE8F5E9),
                border = BorderStroke(1.dp, Color(0xFFC8E6C9)),
                modifier = Modifier.clickable(enabled = !isGeneratingPdf, onClick = onExportPdf)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isGeneratingPdf) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = AppColors.GreenPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Membuat...",
                            color = AppColors.GreenPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = AppColors.GreenPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Cetak PDF",
                            color = AppColors.GreenPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }
        }
    }

/**
 * Clean item inside PDF chooser dialog.
 */
@Composable
private fun ReportChooserItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF9FBF9),
        border = BorderStroke(1.dp, Color(0xFFEFF3F0)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE8F5E9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AppColors.GreenPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = AppColors.TextPrimary
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        color = AppColors.TextSecondary
                    )
                )
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
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = when (rank) {
                1 -> Color(0xFFFFD700)
                2 -> Color(0xFFC0C0C0)
                3 -> Color(0xFFCD7F32)
                else -> Color(0xFFF0F4F0)
            },
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "$rank",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    fontWeight = FontWeight.Bold,
                    color = if (rank <= 3) Color.White else AppColors.TextSecondary
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.productName,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    color = AppColors.TextPrimary
                )
            )
            val qtyText = if (item.totalQuantity % 1.0 == 0.0) "${item.totalQuantity.toInt()} pcs" else "${item.totalQuantity} pcs"
            Text(
                text = "Terjual: $qtyText",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.5.sp,
                    color = AppColors.TextSecondary
                )
            )
        }
        Text(
            text = formatRupiah(item.totalRevenue),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                color = AppColors.GreenPrimary
            )
        )
    }
}
