package id.skmnetwork.bukuwarung.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import id.skmnetwork.bukuwarung.data.local.dao.TopProductSummary
import id.skmnetwork.bukuwarung.data.repository.ReportRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

enum class ReportPeriod(val label: String) {
    TODAY("Hari Ini"),
    LAST_7_DAYS("7 Hari"),
    THIS_MONTH("Bulan Ini"),
    ALL_TIME("Semua")
}

data class DateRange(val startDate: Long, val endDate: Long)

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModel(
    private val repository: ReportRepository
) : ViewModel() {

    val selectedPeriod = MutableStateFlow(ReportPeriod.TODAY)

    val dateRange: StateFlow<DateRange> = selectedPeriod
        .flatMapLatest { period ->
            MutableStateFlow(calculateDateRange(period))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = calculateDateRange(ReportPeriod.TODAY)
        )

    fun selectPeriod(period: ReportPeriod) {
        selectedPeriod.value = period
    }

    // ==========================================
    // 1. SALES & RETURNS REPORTS (Period-Filtered)
    // ==========================================
    val salesTotal: StateFlow<Long?> = dateRange
        .flatMapLatest { range -> repository.getSalesTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val salesTaxableBaseTotal: StateFlow<Long?> = dateRange
        .flatMapLatest { range -> repository.getSalesTaxableBaseTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val salesTaxAmountTotal: StateFlow<Long?> = dateRange
        .flatMapLatest { range -> repository.getSalesTaxAmountTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val salesReturnTotal: StateFlow<Long?> = dateRange
        .flatMapLatest { range -> repository.getSalesReturnTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val netSalesTotal: StateFlow<Long> = dateRange
        .flatMapLatest { range -> repository.getNetSalesTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val salesCount: StateFlow<Int> = dateRange
        .flatMapLatest { range -> repository.getSalesCount(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val salesReturnCount: StateFlow<Int> = dateRange
        .flatMapLatest { range -> repository.getSalesReturnCount(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val itemsSoldTotal: StateFlow<Double?> = dateRange
        .flatMapLatest { range -> repository.getItemsSoldTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val itemsReturnedTotal: StateFlow<Double?> = dateRange
        .flatMapLatest { range -> repository.getItemsReturnedTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val cashSalesTotal: StateFlow<Long?> = dateRange
        .flatMapLatest { range -> repository.getCashSalesTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val creditSalesTotal: StateFlow<Long?> = dateRange
        .flatMapLatest { range -> repository.getCreditSalesTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val topSellingProducts: StateFlow<List<TopProductSummary>> = dateRange
        .flatMapLatest { range -> repository.getTopSellingProducts(range.startDate, range.endDate, limit = 5) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ==========================================
    // 2. COGS & PROFIT REPORTS (Period-Filtered)
    // ==========================================
    val saleCogsTotal: StateFlow<Long?> = dateRange
        .flatMapLatest { range -> repository.getSaleCogsTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val returnCogsTotal: StateFlow<Long?> = dateRange
        .flatMapLatest { range -> repository.getReturnCogsTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val netCogsTotal: StateFlow<Long> = dateRange
        .flatMapLatest { range -> repository.getNetCogsTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val grossProfitTotal: StateFlow<Long> = dateRange
        .flatMapLatest { range -> repository.getGrossProfitTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val operatingExpenseTotal: StateFlow<Long?> = dateRange
        .flatMapLatest { range -> repository.getOperatingExpenseTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val netProfitTotal: StateFlow<Long> = dateRange
        .flatMapLatest { range -> repository.getNetProfitTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    // ==========================================
    // 3. PURCHASE REPORTS (Period-Filtered)
    // ==========================================
    val purchaseTotal: StateFlow<Long?> = dateRange
        .flatMapLatest { range -> repository.getPurchaseTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val purchaseCount: StateFlow<Int> = dateRange
        .flatMapLatest { range -> repository.getPurchaseCount(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val itemsPurchasedTotal: StateFlow<Double?> = dateRange
        .flatMapLatest { range -> repository.getItemsPurchasedTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // ==========================================
    // 4. CASH REPORTS (Period & All-Time)
    // ==========================================
    val cashIncomeTotal: StateFlow<Long?> = dateRange
        .flatMapLatest { range -> repository.getCashIncomeTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val cashExpenseTotal: StateFlow<Long?> = dateRange
        .flatMapLatest { range -> repository.getCashExpenseTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    // Net cash movement for the selected period (income - expense)
    val netCashMovement: StateFlow<Long> = combine(cashIncomeTotal, cashExpenseTotal) { income, expense ->
        (income ?: 0L) - (expense ?: 0L)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    // All-time current cash balance (unaffected by selected report period)
    val currentCashBalance: StateFlow<Long?> = repository.totalCashBalance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    // All-time current stock value
    val totalStockValue: StateFlow<Long?> = repository.totalStockValue
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    // ==========================================
    // 5. CUSTOMER DEBT REPORTS (Global / Lifetime)
    // ==========================================
    val totalOutstandingDebt: StateFlow<Long?> = repository.totalOutstandingDebt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalPaidDebt: StateFlow<Long?> = repository.totalPaidDebt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalDebtCreated: StateFlow<Long?> = repository.totalDebtCreated
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val openDebtsCount: StateFlow<Int> = repository.openDebtsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val paidDebtsCount: StateFlow<Int> = repository.paidDebtsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // ==========================================
    // 6. SUPPLIER PAYABLE REPORTS (Global / Lifetime)
    // ==========================================
    val totalOutstandingPayable: StateFlow<Long?> = repository.totalOutstandingPayable
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalPaidPayable: StateFlow<Long?> = repository.totalPaidPayable
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalPayableCreated: StateFlow<Long?> = repository.totalPayableCreated
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val openPayablesCount: StateFlow<Int> = repository.openPayablesCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val paidPayablesCount: StateFlow<Int> = repository.paidPayablesCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    suspend fun buildBusinessSummaryData(
        userSettings: id.skmnetwork.bukuwarung.data.preferences.UserSettings,
        period: ReportPeriod = selectedPeriod.value
    ): id.skmnetwork.bukuwarung.pdf.reports.BusinessSummaryReportData {
        val range = calculateDateRange(period)
        val grossSales = repository.getSalesTotal(range.startDate, range.endDate).first() ?: 0L
        val returns = repository.getSalesReturnTotal(range.startDate, range.endDate).first() ?: 0L
        val netSales = grossSales - returns
        val salesCount = repository.getSalesCount(range.startDate, range.endDate).first()
        val returnCount = repository.getSalesReturnCount(range.startDate, range.endDate).first()
        val netCogs = repository.getNetCogsTotal(range.startDate, range.endDate).first()
        val grossProfit = netSales - netCogs
        val opExpense = repository.getOperatingExpenseTotal(range.startDate, range.endDate).first() ?: 0L
        val netProfit = grossProfit - opExpense

        val cashBalance = repository.totalCashBalance.first() ?: 0L
        val stockVal = repository.totalStockValue.first() ?: 0L
        val debt = repository.totalOutstandingDebt.first() ?: 0L
        val payable = repository.totalOutstandingPayable.first() ?: 0L

        val sdf = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.forLanguageTag("id-ID"))
        val printedAt = sdf.format(java.util.Date())
        val terminology = id.skmnetwork.bukuwarung.domain.business.BusinessTaxonomyRegistry.resolve(
            userSettings.primaryBusinessType,
            userSettings.secondaryActivities
        ).terminology

        return id.skmnetwork.bukuwarung.pdf.reports.BusinessSummaryReportData(
            shopName = userSettings.shopName.ifBlank { "Warung Saya" },
            address = userSettings.address,
            phone = userSettings.phone,
            periodLabel = period.label,
            printedAt = printedAt,
            grossSales = grossSales,
            salesReturn = returns,
            netSales = netSales,
            salesCount = salesCount,
            salesReturnCount = returnCount,
            netCogs = netCogs,
            grossProfit = grossProfit,
            operatingExpense = opExpense,
            netProfit = netProfit,
            cashBalance = cashBalance,
            stockValue = stockVal,
            outstandingDebt = debt,
            outstandingPayable = payable,
            terminology = terminology
        )
    }

    suspend fun buildSalesReportData(
        userSettings: id.skmnetwork.bukuwarung.data.preferences.UserSettings,
        period: ReportPeriod = selectedPeriod.value
    ): id.skmnetwork.bukuwarung.pdf.reports.SalesReportData {
        val range = calculateDateRange(period)
        val salesList = repository.getSalesWithCustomerByDateRange(range.startDate, range.endDate).first()
        val allReturns = repository.getAllReturnsList()
        val returnsBySaleId = allReturns.groupBy { it.saleTransactionId }

        val grossSales = repository.getSalesTotal(range.startDate, range.endDate).first() ?: 0L
        val salesTaxableBaseTotal = repository.getSalesTaxableBaseTotal(range.startDate, range.endDate).first() ?: 0L
        val salesTaxAmountTotal = repository.getSalesTaxAmountTotal(range.startDate, range.endDate).first() ?: 0L
        val returnsTaxableBaseTotal = repository.getReturnsTaxableBaseTotal(range.startDate, range.endDate).first() ?: 0L
        val returnsTaxAmountTotal = repository.getReturnsTaxAmountTotal(range.startDate, range.endDate).first() ?: 0L
        val netTaxableBase = salesTaxableBaseTotal - returnsTaxableBaseTotal
        val netTaxAmount = salesTaxAmountTotal - returnsTaxAmountTotal
        val totalRefunds = repository.getSalesReturnTotal(range.startDate, range.endDate).first() ?: 0L
        val netSales = grossSales - totalRefunds

        val sdfDate = java.text.SimpleDateFormat("dd/MM/yy HH:mm", java.util.Locale.forLanguageTag("id-ID"))
        val rows = salesList.map { sale ->
            val customerName = sale.customerName?.ifBlank { "Umum" } ?: "Umum"
            val paymentMethodLabel = when (sale.paymentMethod.uppercase()) {
                "CASH" -> "Tunai"
                "QRIS" -> "QRIS"
                "CREDIT" -> "Kredit"
                else -> sale.paymentMethod
            }

            val saleReturns = returnsBySaleId[sale.id] ?: emptyList()
            val totalRefundOnThisSale = saleReturns.sumOf { it.totalRefundAmount }

            val statusLabel = when {
                totalRefundOnThisSale >= sale.totalAmount && sale.totalAmount > 0 -> "Retur Total"
                totalRefundOnThisSale > 0 -> "Retur Sbg"
                sale.paymentMethod.uppercase() == "CREDIT" -> "Kredit"
                else -> "Lunas"
            }

            id.skmnetwork.bukuwarung.pdf.reports.SalesReportRow(
                dateFormatted = sdfDate.format(java.util.Date(sale.transactionDate)),
                transactionNumber = sale.transactionNumber,
                customerName = customerName,
                paymentMethod = paymentMethodLabel,
                totalAmount = sale.totalAmount,
                refundAmount = totalRefundOnThisSale,
                status = statusLabel,
                taxableBase = sale.taxableBaseSnapshot,
                taxAmount = sale.taxAmountSnapshot
            )
        }

        val sdfPrinted = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.forLanguageTag("id-ID"))
        val terminology = id.skmnetwork.bukuwarung.domain.business.BusinessTaxonomyRegistry.resolve(
            userSettings.primaryBusinessType,
            userSettings.secondaryActivities
        ).terminology

        return id.skmnetwork.bukuwarung.pdf.reports.SalesReportData(
            shopName = userSettings.shopName.ifBlank { "Warung Saya" },
            address = userSettings.address,
            phone = userSettings.phone,
            periodLabel = period.label,
            printedAt = sdfPrinted.format(java.util.Date()),
            items = rows,
            grossSales = grossSales,
            totalRefund = totalRefunds,
            netSales = netSales,
            totalTransactions = salesList.size,
            totalTaxableBase = netTaxableBase,
            totalTaxAmount = netTaxAmount,
            terminology = terminology
        )
    }

    suspend fun buildProductReportData(
        userSettings: id.skmnetwork.bukuwarung.data.preferences.UserSettings,
        period: ReportPeriod = selectedPeriod.value
    ): id.skmnetwork.bukuwarung.pdf.reports.ProductReportData {
        val range = calculateDateRange(period)
        val salesList = repository.getProductSalesSummaryByDateRange(range.startDate, range.endDate).first()
        val returnsList = repository.getProductReturnsSummaryByDateRange(range.startDate, range.endDate).first()

        val salesMap = salesList.associateBy { it.productUuid }
        val returnsMap = returnsList.associateBy { it.productUuid }

        val allKeys = (salesMap.keys + returnsMap.keys).distinct()

        val rows = allKeys.mapNotNull { key ->
            val sale = salesMap[key]
            val ret = returnsMap[key]

            val name = sale?.productName ?: ret?.productName ?: "Produk Tidak Dikenal"
            val grossQty = sale?.totalQuantity ?: 0.0
            val retQty = ret?.returnedQuantity ?: 0.0
            val netQty = (grossQty - retQty).coerceAtLeast(0.0)

            val grossRev = sale?.totalRevenue ?: 0L
            val retRev = ret?.returnedRevenue ?: 0L
            val netRev = grossRev - retRev

            val grossCogs = sale?.totalCogs ?: 0L
            val retCogs = ret?.returnedCogs ?: 0L
            val netCogs = grossCogs - retCogs

            val grossProfit = netRev - netCogs

            if (grossQty == 0.0 && retQty == 0.0 && grossRev == 0L && retRev == 0L) {
                null
            } else {
                id.skmnetwork.bukuwarung.pdf.reports.ProductReportRow(
                    productUuid = key,
                    productName = name,
                    quantitySold = netQty,
                    revenue = netRev,
                    cogs = netCogs,
                    grossProfit = grossProfit
                )
            }
        }.sortedWith(
            compareByDescending<id.skmnetwork.bukuwarung.pdf.reports.ProductReportRow> { it.revenue }
                .thenByDescending { it.quantitySold }
        )

        val totalProductsCount = rows.size
        val totalNetQuantity = rows.sumOf { it.quantitySold }
        val totalNetRevenue = rows.sumOf { it.revenue }
        val totalNetCogs = rows.sumOf { it.cogs }
        val totalGrossProfit = totalNetRevenue - totalNetCogs

        val sdfPrinted = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.forLanguageTag("id-ID"))
        val terminology = id.skmnetwork.bukuwarung.domain.business.BusinessTaxonomyRegistry.resolve(
            userSettings.primaryBusinessType,
            userSettings.secondaryActivities
        ).terminology

        return id.skmnetwork.bukuwarung.pdf.reports.ProductReportData(
            shopName = userSettings.shopName.ifBlank { "Warung Saya" },
            address = userSettings.address,
            phone = userSettings.phone,
            periodLabel = period.label,
            printedAt = sdfPrinted.format(java.util.Date()),
            items = rows,
            totalProductsCount = totalProductsCount,
            totalNetQuantity = totalNetQuantity,
            totalNetRevenue = totalNetRevenue,
            totalNetCogs = totalNetCogs,
            totalGrossProfit = totalGrossProfit,
            terminology = terminology
        )
    }

    suspend fun buildPurchaseReportData(
        userSettings: id.skmnetwork.bukuwarung.data.preferences.UserSettings,
        period: ReportPeriod = selectedPeriod.value
    ): id.skmnetwork.bukuwarung.pdf.reports.PurchaseReportData {
        val range = calculateDateRange(period)
        val purchaseList = repository.getPurchasesWithSupplierByDateRange(range.startDate, range.endDate).first()

        val totalPurchases = repository.getPurchaseTotal(range.startDate, range.endDate).first() ?: 0L
        val purchasesTaxableBaseTotal = repository.getPurchasesTaxableBaseTotal(range.startDate, range.endDate).first() ?: 0L
        val purchasesTaxAmountTotal = repository.getPurchasesTaxAmountTotal(range.startDate, range.endDate).first() ?: 0L
        val cashPurchases = purchaseList.filter { it.paymentMethod.uppercase() == "CASH" }.sumOf { it.totalAmount }
        val creditPurchases = purchaseList.filter { it.paymentMethod.uppercase() == "CREDIT" }.sumOf { it.totalAmount }

        val sdfDate = java.text.SimpleDateFormat("dd/MM/yy HH:mm", java.util.Locale.forLanguageTag("id-ID"))
        val rows = purchaseList.map { purchase ->
            val supplierName = purchase.supplierName?.ifBlank { "Umum" } ?: "Umum"
            val paymentMethodLabel = when (purchase.paymentMethod.uppercase()) {
                "CASH" -> "Tunai"
                "CREDIT" -> "Kredit"
                else -> purchase.paymentMethod
            }
            val statusLabel = if (purchase.paymentMethod.uppercase() == "CREDIT") "Kredit" else "Lunas"

            id.skmnetwork.bukuwarung.pdf.reports.PurchaseReportRow(
                dateFormatted = sdfDate.format(java.util.Date(purchase.transactionDate)),
                transactionNumber = purchase.transactionNumber,
                supplierName = supplierName,
                paymentMethod = paymentMethodLabel,
                totalAmount = purchase.totalAmount,
                status = statusLabel,
                taxableBase = purchase.taxableBaseSnapshot,
                taxAmount = purchase.taxAmountSnapshot
            )
        }

        val sdfPrinted = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.forLanguageTag("id-ID"))
        val terminology = id.skmnetwork.bukuwarung.domain.business.BusinessTaxonomyRegistry.resolve(
            userSettings.primaryBusinessType,
            userSettings.secondaryActivities
        ).terminology

        return id.skmnetwork.bukuwarung.pdf.reports.PurchaseReportData(
            shopName = userSettings.shopName.ifBlank { "Warung Saya" },
            address = userSettings.address,
            phone = userSettings.phone,
            periodLabel = period.label,
            printedAt = sdfPrinted.format(java.util.Date()),
            items = rows,
            totalPurchases = totalPurchases,
            purchasesTaxableBaseTotal = purchasesTaxableBaseTotal,
            purchasesTaxAmountTotal = purchasesTaxAmountTotal,
            cashPurchasesTotal = cashPurchases,
            creditPurchasesTotal = creditPurchases,
            totalTransactions = purchaseList.size,
            terminology = terminology
        )
    }

    suspend fun buildCustomerDebtReportData(
        userSettings: id.skmnetwork.bukuwarung.data.preferences.UserSettings,
        mode: id.skmnetwork.bukuwarung.pdf.reports.DebtReportMode = id.skmnetwork.bukuwarung.pdf.reports.DebtReportMode.CURRENT_OUTSTANDING,
        period: ReportPeriod = ReportPeriod.TODAY,
        customDateRange: DateRange? = null
    ): id.skmnetwork.bukuwarung.pdf.reports.DebtReportData {
        val debtList = if (mode == id.skmnetwork.bukuwarung.pdf.reports.DebtReportMode.CURRENT_OUTSTANDING) {
            repository.getOpenDebtsWithCustomer().first()
        } else {
            val range = customDateRange ?: calculateDateRange(period)
            repository.getDebtsWithCustomerByDateRange(range.startDate, range.endDate).first()
        }

        val sdfDate = java.text.SimpleDateFormat("dd/MM/yy HH:mm", java.util.Locale.forLanguageTag("id-ID"))
        val rows = debtList.map { debt ->
            val customerName = debt.customerName?.ifBlank { "Pelanggan Umum" } ?: "Pelanggan Umum"
            val customerPhone = debt.customerPhone?.ifBlank { "-" } ?: "-"
            val trxNumber = debt.transactionNumber ?: "TRX-${debt.uuid.take(8).uppercase()}"
            val remaining = (debt.totalDebt - debt.paidAmount).coerceAtLeast(0L)
            val statusLabel = if (debt.status.uppercase() == "PAID" || remaining == 0L) "Lunas" else "Belum Lunas"

            id.skmnetwork.bukuwarung.pdf.reports.DebtReportRow(
                dateFormatted = sdfDate.format(java.util.Date(debt.createdAt)),
                transactionNumber = trxNumber,
                customerName = customerName,
                customerPhone = customerPhone,
                totalDebt = debt.totalDebt,
                paidAmount = debt.paidAmount,
                remainingDebt = remaining,
                status = statusLabel
            )
        }

        val totalOutstanding = debtList.sumOf { (it.totalDebt - it.paidAmount).coerceAtLeast(0L) }
        val totalPaid = debtList.sumOf { it.paidAmount }
        val totalCreated = debtList.sumOf { it.totalDebt }
        val activeDebtors = debtList.filter { (it.totalDebt - it.paidAmount) > 0 }.map { it.customerId }.distinct().size

        val periodLabel = if (mode == id.skmnetwork.bukuwarung.pdf.reports.DebtReportMode.CURRENT_OUTSTANDING) {
            "Saldo Piutang Aktif Saat Ini"
        } else {
            "Mutasi: ${period.label}"
        }

        val sdfPrinted = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.forLanguageTag("id-ID"))
        val terminology = id.skmnetwork.bukuwarung.domain.business.BusinessTaxonomyRegistry.resolve(
            userSettings.primaryBusinessType,
            userSettings.secondaryActivities
        ).terminology

        return id.skmnetwork.bukuwarung.pdf.reports.DebtReportData(
            mode = mode,
            shopName = userSettings.shopName.ifBlank { "Warung Saya" },
            address = userSettings.address,
            phone = userSettings.phone,
            periodLabel = periodLabel,
            printedAt = sdfPrinted.format(java.util.Date()),
            items = rows,
            totalOutstanding = totalOutstanding,
            totalPaid = totalPaid,
            totalDebtCreated = totalCreated,
            activeDebtorsCount = activeDebtors,
            totalTransactions = debtList.size,
            terminology = terminology
        )
    }

    companion object {
        fun calculateDateRange(period: ReportPeriod): DateRange {
            val calendar = Calendar.getInstance()
            
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val endOfToday = calendar.timeInMillis

            return when (period) {
                ReportPeriod.TODAY -> {
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    DateRange(calendar.timeInMillis, endOfToday)
                }
                ReportPeriod.LAST_7_DAYS -> {
                    calendar.add(Calendar.DAY_OF_YEAR, -6)
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    DateRange(calendar.timeInMillis, endOfToday)
                }
                ReportPeriod.THIS_MONTH -> {
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    DateRange(calendar.timeInMillis, endOfToday)
                }
                ReportPeriod.ALL_TIME -> {
                    DateRange(0L, Long.MAX_VALUE)
                }
            }
        }
    }
}

class ReportViewModelFactory(
    private val repository: ReportRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReportViewModel::class.java)) {
            return ReportViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
