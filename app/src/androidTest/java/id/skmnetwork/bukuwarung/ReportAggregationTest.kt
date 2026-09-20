package id.skmnetwork.bukuwarung

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.CustomerEntity
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.SupplierEntity
import id.skmnetwork.bukuwarung.data.repository.CustomerRepository
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.data.repository.ReportRepository
import id.skmnetwork.bukuwarung.data.repository.SupplierRepository
import id.skmnetwork.bukuwarung.ui.report.ReportPeriod
import id.skmnetwork.bukuwarung.ui.report.ReportViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class ReportAggregationTest {

    private lateinit var database: AppDatabase
    private lateinit var productRepository: ProductRepository
    private lateinit var customerRepository: CustomerRepository
    private lateinit var supplierRepository: SupplierRepository
    private lateinit var reportRepository: ReportRepository

    private var customerId: Long = 0
    private var supplierId: Long = 0

    private var prodCashSaleId: Long = 0
    private var prodQrisSaleId: Long = 0
    private var prodCreditSaleId: Long = 0
    private var prodCashPurId: Long = 0
    private var prodCreditPurId: Long = 0

    @Before
    fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        productRepository = ProductRepository(database, "LEGACY_BUSINESS")
        customerRepository = CustomerRepository(database, "LEGACY_BUSINESS")
        supplierRepository = SupplierRepository(database, "LEGACY_BUSINESS")
        reportRepository = ReportRepository(database, "LEGACY_BUSINESS")

        val catId = database.categoryDao().insertCategory(CategoryEntity(name = "General"))

        prodCashSaleId = database.productDao().insertProduct(
            ProductEntity(categoryId = catId, name = "Barang Tunai", purchasePrice = 4000, sellingPrice = 5000, stock = 100.0)
        )
        prodQrisSaleId = database.productDao().insertProduct(
            ProductEntity(categoryId = catId, name = "Barang QRIS", purchasePrice = 8000, sellingPrice = 10000, stock = 100.0)
        )
        prodCreditSaleId = database.productDao().insertProduct(
            ProductEntity(categoryId = catId, name = "Barang Kredit", purchasePrice = 8000, sellingPrice = 10000, stock = 100.0)
        )
        prodCashPurId = database.productDao().insertProduct(
            ProductEntity(categoryId = catId, name = "Barang Beli Tunai", purchasePrice = 2000, sellingPrice = 3000, stock = 100.0)
        )
        prodCreditPurId = database.productDao().insertProduct(
            ProductEntity(categoryId = catId, name = "Barang Beli Kredit", purchasePrice = 3000, sellingPrice = 4000, stock = 100.0)
        )

        customerId = customerRepository.saveCustomer("Pak Budi", "08123456789", "Alamat Budi")
        supplierId = supplierRepository.saveSupplier("PT Sembako Makmur", "08987654321", "Alamat Supplier")
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun test1_auditTestMatrixAccountingVerification() = runBlocking {
        // 1. Sale CASH: Rp 10.000 (2 pcs @ 5.000)
        productRepository.processAtomicCheckout(mapOf(prodCashSaleId to 2.0))

        // 2. Sale QRIS: Rp 20.000 (2 pcs @ 10.000)
        productRepository.processAtomicCheckout(mapOf(prodQrisSaleId to 2.0), paymentMethod = "QRIS")

        // 3. Sale CREDIT: Rp 30.000 (3 pcs @ 10.000)
        customerRepository.processAtomicCreditCheckout(mapOf(prodCreditSaleId to 3.0), customerId)

        // 4. Debt Payment: Rp 5.000
        val debts = database.debtDao().getDebtsForCustomer(customerId, "LEGACY_BUSINESS").first()
        val debtId = debts.first().id
        customerRepository.processAtomicDebtPayment(debtId, 5000L, "Cicilan 1")

        // 5. Cash Purchase: Rp 8.000 (4 pcs @ 2.000)
        productRepository.processAtomicPurchase(mapOf(prodCashPurId to 4.0))

        // 6. Credit Purchase: Rp 12.000 (4 pcs @ 3.000)
        supplierRepository.processAtomicCreditPurchase(mapOf(prodCreditPurId to 4.0), supplierId)

        // 7. Supplier Payment: Rp 4.000
        val payables = database.supplierPayableDao().getPayablesForSupplier(supplierId, "LEGACY_BUSINESS").first()
        val payableId = payables.first().id
        supplierRepository.processAtomicSupplierPayment(payableId, 4000L, "Bayar Hutang Supplier 1")

        val start = 0L
        val end = Long.MAX_VALUE

        // Sales Revenue = 10.000 + 20.000 + 30.000 = 60.000
        val salesRevenue = reportRepository.getSalesTotal(start, end).first() ?: 0L
        val salesCount = reportRepository.getSalesCount(start, end).first()
        val itemsSold = reportRepository.getItemsSoldTotal(start, end).first() ?: 0.0
        val cashSales = reportRepository.getCashSalesTotal(start, end).first() ?: 0L
        val creditSales = reportRepository.getCreditSalesTotal(start, end).first() ?: 0L

        assertEquals(60000L, salesRevenue)
        assertEquals(3, salesCount)
        assertEquals(7.0, itemsSold, 0.001) // 2 + 2 + 3 = 7 pcs
        assertEquals(10000L, cashSales)
        assertEquals(30000L, creditSales)

        // Purchase Total = 8.000 (cash purchase) + 12.000 (credit purchase) = 20.000
        val purchaseTotal = reportRepository.getPurchaseTotal(start, end).first() ?: 0L
        val purchaseCount = reportRepository.getPurchaseCount(start, end).first()
        val itemsPurchased = reportRepository.getItemsPurchasedTotal(start, end).first() ?: 0.0
        assertEquals(20000L, purchaseTotal)
        assertEquals(2, purchaseCount)
        assertEquals(8.0, itemsPurchased, 0.001) // 4 + 4 = 8 pcs

        // Cash Income = 10.000 (Cash Sale) + 5.000 (Debt Payment) = 15.000
        val cashIncome = reportRepository.getCashIncomeTotal(start, end).first() ?: 0L
        assertEquals(15000L, cashIncome)

        // Cash Expense = 8.000 (Cash Purchase) + 4.000 (Supplier Payment) = 12.000
        val cashExpense = reportRepository.getCashExpenseTotal(start, end).first() ?: 0L
        assertEquals(12000L, cashExpense)

        // Net Cash Movement = 15.000 - 12.000 = 3.000
        val netCash = cashIncome - cashExpense
        assertEquals(3000L, netCash)

        // Customer Debt Outstanding = 30.000 - 5.000 = 25.000
        val custOutstanding = reportRepository.totalOutstandingDebt.first() ?: 0L
        val custPaid = reportRepository.totalPaidDebt.first() ?: 0L
        val custCreated = reportRepository.totalDebtCreated.first() ?: 0L
        assertEquals(25000L, custOutstanding)
        assertEquals(5000L, custPaid)
        assertEquals(30000L, custCreated)

        // Supplier Payable Outstanding = 12.000 - 4.000 = 8.000
        val suppOutstanding = reportRepository.totalOutstandingPayable.first() ?: 0L
        val suppPaid = reportRepository.totalPaidPayable.first() ?: 0L
        val suppCreated = reportRepository.totalPayableCreated.first() ?: 0L
        val suppOpenCount = reportRepository.openPayablesCount.first()
        assertEquals(8000L, suppOutstanding)
        assertEquals(4000L, suppPaid)
        assertEquals(12000L, suppCreated)
        assertEquals(1, suppOpenCount)
    }

    @Test
    fun test2_dateBoundaryExactFiltering() = runBlocking {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfToday = cal.timeInMillis

        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val endOfToday = cal.timeInMillis

        // Transaction 1: exactly at startOfDay
        val trx1 = SaleTransactionEntity(
            transactionNumber = "TRX-START",
            transactionDate = startOfToday,
            totalAmount = 10000,
            paymentMethod = "CASH",
            createdAt = startOfToday
        )
        database.saleDao().insertTransaction(trx1)

        // Transaction 2: exactly at endOfDay
        val trx2 = SaleTransactionEntity(
            transactionNumber = "TRX-END",
            transactionDate = endOfToday,
            totalAmount = 20000,
            paymentMethod = "CASH",
            createdAt = endOfToday
        )
        database.saleDao().insertTransaction(trx2)

        // Transaction 3: 1 ms before startOfDay
        val trx3 = SaleTransactionEntity(
            transactionNumber = "TRX-BEFORE",
            transactionDate = startOfToday - 1,
            totalAmount = 50000,
            paymentMethod = "CASH",
            createdAt = startOfToday - 1
        )
        database.saleDao().insertTransaction(trx3)

        // Transaction 4: 1 ms after endOfDay
        val trx4 = SaleTransactionEntity(
            transactionNumber = "TRX-AFTER",
            transactionDate = endOfToday + 1,
            totalAmount = 70000,
            paymentMethod = "CASH",
            createdAt = endOfToday + 1
        )
        database.saleDao().insertTransaction(trx4)

        // Query Today Range
        val todayTotal = reportRepository.getSalesTotal(startOfToday, endOfToday).first() ?: 0L
        val todayCount = reportRepository.getSalesCount(startOfToday, endOfToday).first()

        // Today must contain exactly TRX-START (10k) and TRX-END (20k) = 30k
        assertEquals(30000L, todayTotal)
        assertEquals(2, todayCount)

        // Query All-Time
        val allTimeTotal = reportRepository.getSalesTotal(0L, Long.MAX_VALUE).first() ?: 0L
        val allTimeCount = reportRepository.getSalesCount(0L, Long.MAX_VALUE).first()
        assertEquals(150000L, allTimeTotal) // 10k + 20k + 50k + 70k
        assertEquals(4, allTimeCount)
    }

    @Test
    fun test3_topSellingProductsAggregationAndOrdering() = runBlocking {
        val now = System.currentTimeMillis()

        // Create Sale Trx
        val trx = SaleTransactionEntity(
            transactionNumber = "TRX-TOP",
            transactionDate = now,
            totalAmount = 100000,
            paymentMethod = "CASH",
            createdAt = now
        )
        val trxId = database.saleDao().insertTransaction(trx)

        // Insert sale items for 4 products with different quantities
        val item1 = SaleItemEntity(transactionId = trxId, productId = 1, productName = "Kopi Sachet", quantity = 5.0, price = 2000, subtotal = 10000)
        val item2 = SaleItemEntity(transactionId = trxId, productId = 2, productName = "Beras 5kg", quantity = 12.0, price = 60000, subtotal = 720000)
        val item3 = SaleItemEntity(transactionId = trxId, productId = 3, productName = "Garam Dapur", quantity = 2.0, price = 3000, subtotal = 6000)
        val item4 = SaleItemEntity(transactionId = trxId, productId = 4, productName = "Gula Pasir", quantity = 8.0, price = 15000, subtotal = 120000)

        database.saleDao().insertSaleItems(listOf(item1, item2, item3, item4))

        val topProducts = reportRepository.getTopSellingProducts(0L, Long.MAX_VALUE, limit = 3).first()

        assertEquals(3, topProducts.size)
        // Rank 1: Beras 5kg (12 qty)
        assertEquals("Beras 5kg", topProducts[0].productName)
        assertEquals(12.0, topProducts[0].totalQuantity, 0.001)
        assertEquals(720000L, topProducts[0].totalRevenue)

        // Rank 2: Gula Pasir (8 qty)
        assertEquals("Gula Pasir", topProducts[1].productName)
        assertEquals(8.0, topProducts[1].totalQuantity, 0.001)
        assertEquals(120000L, topProducts[1].totalRevenue)

        // Rank 3: Kopi Sachet (5 qty)
        assertEquals("Kopi Sachet", topProducts[2].productName)
        assertEquals(5.0, topProducts[2].totalQuantity, 0.001)
        assertEquals(10000L, topProducts[2].totalRevenue)
    }

    @Test
    fun test4_supplierPayableLifecycleFullRepayment() = runBlocking {
        // Credit purchase Rp 12.000
        supplierRepository.processAtomicCreditPurchase(mapOf(prodCreditPurId to 4.0), supplierId)

        var outstanding = reportRepository.totalOutstandingPayable.first() ?: 0L
        var paid = reportRepository.totalPaidPayable.first() ?: 0L
        var created = reportRepository.totalPayableCreated.first() ?: 0L
        var openCount = reportRepository.openPayablesCount.first()
        var paidCount = reportRepository.paidPayablesCount.first()

        assertEquals(12000L, outstanding)
        assertEquals(0L, paid)
        assertEquals(12000L, created)
        assertEquals(1, openCount)
        assertEquals(0, paidCount)

        // Partial payment Rp 4.000
        val payableId = database.supplierPayableDao().getPayablesForSupplier(supplierId, "LEGACY_BUSINESS").first().first().id
        supplierRepository.processAtomicSupplierPayment(payableId, 4000L, "Cicilan 1")

        outstanding = reportRepository.totalOutstandingPayable.first() ?: 0L
        paid = reportRepository.totalPaidPayable.first() ?: 0L
        openCount = reportRepository.openPayablesCount.first()
        paidCount = reportRepository.paidPayablesCount.first()

        assertEquals(8000L, outstanding)
        assertEquals(4000L, paid)
        assertEquals(1, openCount)
        assertEquals(0, paidCount)

        // Full remaining payment Rp 8.000
        supplierRepository.processAtomicSupplierPayment(payableId, 8000L, "Pelunasan")

        outstanding = reportRepository.totalOutstandingPayable.first() ?: 0L
        paid = reportRepository.totalPaidPayable.first() ?: 0L
        openCount = reportRepository.openPayablesCount.first()
        paidCount = reportRepository.paidPayablesCount.first()

        assertEquals(0L, outstanding)
        assertEquals(12000L, paid)
        assertEquals(0, openCount)
        assertEquals(1, paidCount)
    }

    @Test
    fun test5_dashboardConsistencyWithReportsToday() = runBlocking {
        // Perform transactions today
        productRepository.processAtomicCheckout(mapOf(prodCashSaleId to 3.0)) // 15.000
        productRepository.processAtomicPurchase(mapOf(prodCashPurId to 2.0)) // 4.000

        val range = ReportViewModel.calculateDateRange(ReportPeriod.TODAY)

        val homeSales = productRepository.getTodaySalesTotalFlow().first() ?: 0L
        val homeExpense = productRepository.getTodayExpenseTotalFlow().first() ?: 0L
        val homeCash = productRepository.totalCashBalance.first() ?: 0L

        val reportSales = reportRepository.getSalesTotal(range.startDate, range.endDate).first() ?: 0L
        val reportExpense = reportRepository.getCashExpenseTotal(range.startDate, range.endDate).first() ?: 0L
        val reportCash = reportRepository.totalCashBalance.first() ?: 0L

        assertEquals(homeSales, reportSales)
        assertEquals(homeExpense, reportExpense)
        assertEquals(homeCash, reportCash)
        assertEquals(15000L, reportSales)
        assertEquals(4000L, reportExpense)
        assertEquals(11000L, reportCash) // 15.000 - 4.000
    }
}



