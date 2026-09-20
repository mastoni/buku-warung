package id.skmnetwork.bukuwarung.data.repository

import id.skmnetwork.bukuwarung.data.local.dao.TopProductSummary
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import kotlinx.coroutines.flow.Flow

class ReportRepository(
    private val appDatabase: AppDatabase,
    private val businessId: String
) {
    private val saleDao = appDatabase.saleDao()
    private val saleReturnDao = appDatabase.saleReturnDao()
    private val productDao = appDatabase.productDao()
    private val purchaseDao = appDatabase.purchaseDao()
    private val cashDao = appDatabase.cashDao()
    private val debtDao = appDatabase.debtDao()
    private val supplierPayableDao = appDatabase.supplierPayableDao()

    // Sales Aggregations
    fun getSalesTotal(startDate: Long, endDate: Long): Flow<Long?> = saleDao.getSalesTotal(businessId, startDate, endDate)
    fun getSalesTaxableBaseTotal(startDate: Long, endDate: Long): Flow<Long?> = saleDao.getSalesTaxableBaseTotal(businessId, startDate, endDate)
    fun getSalesTaxAmountTotal(startDate: Long, endDate: Long): Flow<Long?> = saleDao.getSalesTaxAmountTotal(businessId, startDate, endDate)
    fun getSalesWithCustomerByDateRange(startDate: Long, endDate: Long): Flow<List<id.skmnetwork.bukuwarung.data.local.dao.SaleWithCustomerItem>> =
        saleDao.getSalesWithCustomerByDateRange(businessId, startDate, endDate)
    fun getSalesReturnTotal(startDate: Long, endDate: Long): Flow<Long?> = saleReturnDao.getSalesReturnTotal(businessId, startDate, endDate)
    suspend fun getAllReturnsList(): List<id.skmnetwork.bukuwarung.data.local.entity.SaleReturnTransactionEntity> = saleReturnDao.getAllReturnsList(businessId)
    fun getSalesCount(startDate: Long, endDate: Long): Flow<Int> = saleDao.getSalesCount(businessId, startDate, endDate)
    fun getSalesReturnCount(startDate: Long, endDate: Long): Flow<Int> = saleReturnDao.getReturnCount(businessId, startDate, endDate)
    fun getItemsSoldTotal(startDate: Long, endDate: Long): Flow<Double?> = saleDao.getItemsSoldTotal(businessId, startDate, endDate)
    fun getItemsReturnedTotal(startDate: Long, endDate: Long): Flow<Double?> = saleReturnDao.getItemsReturnedTotal(businessId, startDate, endDate)
    fun getCashSalesTotal(startDate: Long, endDate: Long): Flow<Long?> = saleDao.getCashSalesTotal(businessId, startDate, endDate)
    fun getCreditSalesTotal(startDate: Long, endDate: Long): Flow<Long?> = saleDao.getCreditSalesTotal(businessId, startDate, endDate)
    fun getTopSellingProducts(startDate: Long, endDate: Long, limit: Int = 5): Flow<List<TopProductSummary>> =
        saleDao.getTopSellingProducts(businessId, startDate, endDate, limit)
    fun getProductSalesSummaryByDateRange(startDate: Long, endDate: Long): Flow<List<id.skmnetwork.bukuwarung.data.local.dao.ProductSalesSummaryItem>> =
        saleDao.getProductSalesSummaryByDateRange(businessId, startDate, endDate)
    fun getProductReturnsSummaryByDateRange(startDate: Long, endDate: Long): Flow<List<id.skmnetwork.bukuwarung.data.local.dao.ProductReturnSummaryItem>> =
        saleReturnDao.getProductReturnsSummaryByDateRange(businessId, startDate, endDate)

    // COGS & Profit Aggregations
    fun getSaleCogsTotal(startDate: Long, endDate: Long): Flow<Long?> = saleDao.getSaleCogsTotal(businessId, startDate, endDate)
    fun getReturnCogsTotal(startDate: Long, endDate: Long): Flow<Long?> = saleReturnDao.getReturnCogsTotal(businessId, startDate, endDate)
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
    fun getOperatingExpenseTotal(startDate: Long, endDate: Long): Flow<Long?> = cashDao.getOperatingExpenseTotal(businessId, startDate, endDate)
    fun getNetProfitTotal(startDate: Long, endDate: Long): Flow<Long> = kotlinx.coroutines.flow.combine(
        getGrossProfitTotal(startDate, endDate),
        getOperatingExpenseTotal(startDate, endDate)
    ) { grossProfit, opExpense ->
        grossProfit - (opExpense ?: 0L)
    }

    // Purchase Aggregations
    fun getPurchaseTotal(startDate: Long, endDate: Long): Flow<Long?> = purchaseDao.getPurchaseTotal(businessId, startDate, endDate)
    fun getPurchasesTaxableBaseTotal(startDate: Long, endDate: Long): Flow<Long?> = purchaseDao.getPurchasesTaxableBaseTotal(businessId, startDate, endDate)
    fun getPurchasesTaxAmountTotal(startDate: Long, endDate: Long): Flow<Long?> = purchaseDao.getPurchasesTaxAmountTotal(businessId, startDate, endDate)
    fun getPurchaseCount(startDate: Long, endDate: Long): Flow<Int> = purchaseDao.getPurchaseCount(businessId, startDate, endDate)
    fun getItemsPurchasedTotal(startDate: Long, endDate: Long): Flow<Double?> = purchaseDao.getItemsPurchasedTotal(businessId, startDate, endDate)
    fun getPurchasesWithSupplierByDateRange(startDate: Long, endDate: Long): Flow<List<id.skmnetwork.bukuwarung.data.local.dao.PurchaseWithSupplierItem>> =
        purchaseDao.getPurchasesWithSupplierByDateRange(businessId, startDate, endDate)

    // Cash Aggregations
    fun getCashIncomeTotal(startDate: Long, endDate: Long): Flow<Long?> = cashDao.getCashIncomeTotal(businessId, startDate, endDate)
    fun getCashExpenseTotal(startDate: Long, endDate: Long): Flow<Long?> = cashDao.getCashExpenseTotal(businessId, startDate, endDate)
    val totalCashBalance: Flow<Long?> = cashDao.getTotalCashBalance(businessId)

    // Stock Valuation (All-time / Current)
    val totalStockValue: Flow<Long?> = productDao.getTotalStockValue(businessId)

    // Customer Debt Aggregations
    val totalOutstandingDebt: Flow<Long?> = debtDao.getTotalOutstandingDebt(businessId)
    val totalPaidDebt: Flow<Long?> = debtDao.getTotalPaidDebt(businessId)
    val totalDebtCreated: Flow<Long?> = debtDao.getTotalDebtCreated(businessId)
    val openDebtsCount: Flow<Int> = debtDao.getOpenDebtsCount(businessId)
    val paidDebtsCount: Flow<Int> = debtDao.getPaidDebtsCount(businessId)
    fun getOpenDebtsWithCustomer(): Flow<List<id.skmnetwork.bukuwarung.data.local.dao.DebtWithCustomerItem>> =
        debtDao.getOpenDebtsWithCustomer(businessId)
    fun getDebtsWithCustomerByDateRange(startDate: Long, endDate: Long): Flow<List<id.skmnetwork.bukuwarung.data.local.dao.DebtWithCustomerItem>> =
        debtDao.getDebtsWithCustomerByDateRange(businessId, startDate, endDate)


    // Supplier Payable Aggregations
    val totalOutstandingPayable: Flow<Long?> = supplierPayableDao.getTotalOutstandingPayable(businessId)
    val totalPaidPayable: Flow<Long?> = supplierPayableDao.getTotalPaidPayable(businessId)
    val totalPayableCreated: Flow<Long?> = supplierPayableDao.getTotalPayableCreated(businessId)
    val openPayablesCount: Flow<Int> = supplierPayableDao.getOpenPayablesCount(businessId)
    val paidPayablesCount: Flow<Int> = supplierPayableDao.getPaidPayablesCount(businessId)
}
