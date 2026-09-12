package id.skmnetwork.bukuwarung.data.repository

import id.skmnetwork.bukuwarung.data.local.dao.TopProductSummary
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import kotlinx.coroutines.flow.Flow

class ReportRepository(
    private val appDatabase: AppDatabase
) {
    private val saleDao = appDatabase.saleDao()
    private val saleReturnDao = appDatabase.saleReturnDao()
    private val productDao = appDatabase.productDao()
    private val purchaseDao = appDatabase.purchaseDao()
    private val cashDao = appDatabase.cashDao()
    private val debtDao = appDatabase.debtDao()
    private val supplierPayableDao = appDatabase.supplierPayableDao()

    // Sales Aggregations
    fun getSalesTotal(startDate: Long, endDate: Long): Flow<Long?> = saleDao.getSalesTotal(startDate, endDate)
    fun getSalesReturnTotal(startDate: Long, endDate: Long): Flow<Long?> = saleReturnDao.getSalesReturnTotal(startDate, endDate)
    fun getSalesCount(startDate: Long, endDate: Long): Flow<Int> = saleDao.getSalesCount(startDate, endDate)
    fun getSalesReturnCount(startDate: Long, endDate: Long): Flow<Int> = saleReturnDao.getReturnCount(startDate, endDate)
    fun getItemsSoldTotal(startDate: Long, endDate: Long): Flow<Double?> = saleDao.getItemsSoldTotal(startDate, endDate)
    fun getItemsReturnedTotal(startDate: Long, endDate: Long): Flow<Double?> = saleReturnDao.getItemsReturnedTotal(startDate, endDate)
    fun getCashSalesTotal(startDate: Long, endDate: Long): Flow<Long?> = saleDao.getCashSalesTotal(startDate, endDate)
    fun getCreditSalesTotal(startDate: Long, endDate: Long): Flow<Long?> = saleDao.getCreditSalesTotal(startDate, endDate)
    fun getTopSellingProducts(startDate: Long, endDate: Long, limit: Int = 5): Flow<List<TopProductSummary>> =
        saleDao.getTopSellingProducts(startDate, endDate, limit)

    // COGS & Profit Aggregations
    fun getSaleCogsTotal(startDate: Long, endDate: Long): Flow<Long?> = saleDao.getSaleCogsTotal(startDate, endDate)
    fun getReturnCogsTotal(startDate: Long, endDate: Long): Flow<Long?> = saleReturnDao.getReturnCogsTotal(startDate, endDate)
    fun getNetSalesTotal(startDate: Long, endDate: Long): Flow<Long> = kotlinx.coroutines.flow.combine(
        getSalesTotal(startDate, endDate),
        getSalesReturnTotal(startDate, endDate)
    ) { grossSales, returns ->
        (grossSales ?: 0L) - (returns ?: 0L)
    }
    fun getNetCogsTotal(startDate: Long, endDate: Long): Flow<Long> = kotlinx.coroutines.flow.combine(
        getSaleCogsTotal(startDate, endDate),
        getReturnCogsTotal(startDate, endDate)
    ) { saleCogs, returnCogs ->
        (saleCogs ?: 0L) - (returnCogs ?: 0L)
    }
    fun getGrossProfitTotal(startDate: Long, endDate: Long): Flow<Long> = kotlinx.coroutines.flow.combine(
        getNetSalesTotal(startDate, endDate),
        getNetCogsTotal(startDate, endDate)
    ) { netSales, netCogs ->
        netSales - netCogs
    }
    fun getOperatingExpenseTotal(startDate: Long, endDate: Long): Flow<Long?> = cashDao.getOperatingExpenseTotal(startDate, endDate)
    fun getNetProfitTotal(startDate: Long, endDate: Long): Flow<Long> = kotlinx.coroutines.flow.combine(
        getGrossProfitTotal(startDate, endDate),
        getOperatingExpenseTotal(startDate, endDate)
    ) { grossProfit, opExpense ->
        grossProfit - (opExpense ?: 0L)
    }

    // Purchase Aggregations
    fun getPurchaseTotal(startDate: Long, endDate: Long): Flow<Long?> = purchaseDao.getPurchaseTotal(startDate, endDate)
    fun getPurchaseCount(startDate: Long, endDate: Long): Flow<Int> = purchaseDao.getPurchaseCount(startDate, endDate)
    fun getItemsPurchasedTotal(startDate: Long, endDate: Long): Flow<Double?> = purchaseDao.getItemsPurchasedTotal(startDate, endDate)

    // Cash Aggregations
    fun getCashIncomeTotal(startDate: Long, endDate: Long): Flow<Long?> = cashDao.getCashIncomeTotal(startDate, endDate)
    fun getCashExpenseTotal(startDate: Long, endDate: Long): Flow<Long?> = cashDao.getCashExpenseTotal(startDate, endDate)
    val totalCashBalance: Flow<Long?> = cashDao.getTotalCashBalance()

    // Stock Valuation (All-time / Current)
    val totalStockValue: Flow<Long?> = productDao.getTotalStockValue()

    // Customer Debt Aggregations
    val totalOutstandingDebt: Flow<Long?> = debtDao.getTotalOutstandingDebt()
    val totalPaidDebt: Flow<Long?> = debtDao.getTotalPaidDebt()
    val totalDebtCreated: Flow<Long?> = debtDao.getTotalDebtCreated()
    val openDebtsCount: Flow<Int> = debtDao.getOpenDebtsCount()
    val paidDebtsCount: Flow<Int> = debtDao.getPaidDebtsCount()

    // Supplier Payable Aggregations
    val totalOutstandingPayable: Flow<Long?> = supplierPayableDao.getTotalOutstandingPayable()
    val totalPaidPayable: Flow<Long?> = supplierPayableDao.getTotalPaidPayable()
    val totalPayableCreated: Flow<Long?> = supplierPayableDao.getTotalPayableCreated()
    val openPayablesCount: Flow<Int> = supplierPayableDao.getOpenPayablesCount()
    val paidPayablesCount: Flow<Int> = supplierPayableDao.getPaidPayablesCount()
}
