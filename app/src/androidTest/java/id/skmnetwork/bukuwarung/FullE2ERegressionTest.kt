package id.skmnetwork.bukuwarung

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.repository.CustomerRepository
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.data.repository.ReportRepository
import id.skmnetwork.bukuwarung.data.repository.SupplierRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FullE2ERegressionTest {

    private lateinit var database: AppDatabase
    private lateinit var productRepository: ProductRepository
    private lateinit var customerRepository: CustomerRepository
    private lateinit var supplierRepository: SupplierRepository
    private lateinit var reportRepository: ReportRepository

    private var customerId: Long = 0
    private var supplierId: Long = 0
    private var indomieId: Long = 0
    private var telurId: Long = 0
    private var berasId: Long = 0

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

        // Setup Categories & Products (Workflow A)
        val catSembako = database.categoryDao().insertCategory(CategoryEntity(name = "Sembako"))
        
        indomieId = database.productDao().insertProduct(
            ProductEntity(categoryId = catSembako, name = "Indomie Goreng", purchasePrice = 3000, sellingPrice = 3500, stock = 50.0, minimumStock = 5.0)
        )
        telurId = database.productDao().insertProduct(
            ProductEntity(categoryId = catSembako, name = "Telur Ayam", purchasePrice = 2000, sellingPrice = 2500, stock = 100.0, minimumStock = 10.0)
        )
        berasId = database.productDao().insertProduct(
            ProductEntity(categoryId = catSembako, name = "Beras Ramos 5kg", purchasePrice = 50000, sellingPrice = 60000, stock = 20.0, minimumStock = 2.0)
        )

        customerId = customerRepository.saveCustomer("Pak Joko", "081234567890", "Jl. Mawar 10")
        supplierId = supplierRepository.saveSupplier("PT Sumber Pangan", "089876543210", "Kawasan Industri")
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testCompleteEndToEndBusinessFlow() = runBlocking {
        // ==========================================
        // WORKFLOW B: CASH SALE
        // 2x Indomie @ 3.500 = 7.000
        // ==========================================
        val cashSaleResult = productRepository.processAtomicCheckout(
            cartItems = mapOf(indomieId to 2.0),
            paymentMethod = "CASH"
        )
        assertTrue(cashSaleResult.isSuccess)
        assertEquals(48.0, database.productDao().getProductById(indomieId, "LEGACY_BUSINESS")?.stock ?: 0.0, 0.001)

        // ==========================================
        // WORKFLOW C: QRIS SALE
        // 4x Telur @ 2.500 = 10.000
        // ==========================================
        val qrisSaleResult = productRepository.processAtomicCheckout(
            cartItems = mapOf(telurId to 4.0),
            paymentMethod = "QRIS"
        )
        assertTrue(qrisSaleResult.isSuccess)
        assertEquals(96.0, database.productDao().getProductById(telurId, "LEGACY_BUSINESS")?.stock ?: 0.0, 0.001)

        // ==========================================
        // WORKFLOW D: CREDIT SALE (HUTANG PELANGGAN)
        // 2x Beras @ 60.000 = 120.000 to Pak Joko
        // ==========================================
        val creditSaleResult = customerRepository.processAtomicCreditCheckout(
            cartItems = mapOf(berasId to 2.0),
            customerId = customerId
        )
        assertTrue(creditSaleResult.isSuccess)
        assertEquals(18.0, database.productDao().getProductById(berasId, "LEGACY_BUSINESS")?.stock ?: 0.0, 0.001)

        // Verify Debt Created (120.000 OPEN)
        var debts = database.debtDao().getDebtsForCustomer(customerId, "LEGACY_BUSINESS").first()
        assertEquals(1, debts.size)
        val debt = debts.first()
        assertEquals(120000L, debt.totalDebt)
        assertEquals(0L, debt.paidAmount)
        assertEquals("OPEN", debt.status)

        // ==========================================
        // WORKFLOW E: CUSTOMER DEBT PAYMENT
        // Partial: 50.000, then Full: 70.000
        // ==========================================
        customerRepository.processAtomicDebtPayment(debt.id, 50000L, "Cicilan 1")
        var updatedDebt = database.debtDao().getDebtById(debt.id, "LEGACY_BUSINESS")
        assertNotNull(updatedDebt)
        assertEquals(50000L, updatedDebt!!.paidAmount)
        assertEquals("OPEN", updatedDebt.status)

        customerRepository.processAtomicDebtPayment(debt.id, 70000L, "Pelunasan")
        updatedDebt = database.debtDao().getDebtById(debt.id, "LEGACY_BUSINESS")
        assertEquals(120000L, updatedDebt!!.paidAmount)
        assertEquals("PAID", updatedDebt.status)

        // ==========================================
        // WORKFLOW F: CASH PURCHASE (RESTOCK)
        // 10x Indomie @ 3.000 = 30.000
        // ==========================================
        val cashPurResult = productRepository.processAtomicPurchase(mapOf(indomieId to 10.0))
        assertTrue(cashPurResult.isSuccess)
        assertEquals(58.0, database.productDao().getProductById(indomieId, "LEGACY_BUSINESS")?.stock ?: 0.0, 0.001) // 48 + 10 = 58

        // ==========================================
        // WORKFLOW G: CREDIT PURCHASE (BELANJA KREDIT)
        // 5x Beras @ 50.000 = 250.000 from PT Sumber Pangan
        // ==========================================
        val creditPurResult = supplierRepository.processAtomicCreditPurchase(
            purchaseItems = mapOf(berasId to 5.0),
            supplierId = supplierId
        )
        assertTrue(creditPurResult.isSuccess)
        assertEquals(23.0, database.productDao().getProductById(berasId, "LEGACY_BUSINESS")?.stock ?: 0.0, 0.001) // 18 + 5 = 23

        var payables = database.supplierPayableDao().getPayablesForSupplier(supplierId, "LEGACY_BUSINESS").first()
        assertEquals(1, payables.size)
        val payable = payables.first()
        assertEquals(250000L, payable.totalDebt)
        assertEquals(0L, payable.paidAmount)
        assertEquals("OPEN", payable.status)

        // ==========================================
        // WORKFLOW H: SUPPLIER PAYMENT
        // Partial: 100.000, then Full: 150.000
        // ==========================================
        supplierRepository.processAtomicSupplierPayment(payable.id, 100000L, "Bayar DP Supplier")
        var updatedPayable = database.supplierPayableDao().getSupplierPayableById(payable.id, "LEGACY_BUSINESS")
        assertNotNull(updatedPayable)
        assertEquals(100000L, updatedPayable!!.paidAmount)
        assertEquals("OPEN", updatedPayable.status)

        supplierRepository.processAtomicSupplierPayment(payable.id, 150000L, "Pelunasan Supplier")
        updatedPayable = database.supplierPayableDao().getSupplierPayableById(payable.id, "LEGACY_BUSINESS")
        assertEquals(250000L, updatedPayable!!.paidAmount)
        assertEquals("PAID", updatedPayable.status)

        // ==========================================
        // WORKFLOW I: MANUAL CASH TRANSACTIONS
        // Income 200.000, Expense 15.000
        // ==========================================
        productRepository.addManualCashTransaction("INCOME", 200000L, "Modal Tambahan Toko")
        productRepository.addManualCashTransaction("EXPENSE", 15000L, "Bayar Iuran Kebersihan")

        // ==========================================
        // WORKFLOW J: FULL REPORTS RECONCILIATION
        // ==========================================
        val start = 0L
        val end = Long.MAX_VALUE

        // 1. Sales
        // Cash Sale (7.000) + QRIS (10.000) + Credit (120.000) = 137.000
        val salesRevenue = reportRepository.getSalesTotal(start, end).first() ?: 0L
        val salesCount = reportRepository.getSalesCount(start, end).first()
        val itemsSold = reportRepository.getItemsSoldTotal(start, end).first() ?: 0.0
        val cashSales = reportRepository.getCashSalesTotal(start, end).first() ?: 0L
        val creditSales = reportRepository.getCreditSalesTotal(start, end).first() ?: 0L

        assertEquals(137000L, salesRevenue)
        assertEquals(3, salesCount)
        assertEquals(8.0, itemsSold, 0.001) // 2 + 4 + 2 = 8 pcs
        assertEquals(7000L, cashSales)
        assertEquals(120000L, creditSales)

        // 2. Purchase
        // Cash Pur (30.000) + Credit Pur (250.000) = 280.000
        val purchaseTotal = reportRepository.getPurchaseTotal(start, end).first() ?: 0L
        val purchaseCount = reportRepository.getPurchaseCount(start, end).first()
        val itemsPurchased = reportRepository.getItemsPurchasedTotal(start, end).first() ?: 0.0
        assertEquals(280000L, purchaseTotal)
        assertEquals(2, purchaseCount)
        assertEquals(15.0, itemsPurchased, 0.001) // 10 + 5 = 15 pcs

        // 3. Cash Flow
        // Income: Cash Sale (7.000) + Debt Payments (50.000 + 70.000 = 120.000) + Manual (200.000) = 327.000
        // Expense: Cash Purchase (30.000) + Supplier Payments (100.000 + 150.000 = 250.000) + Manual (15.000) = 295.000
        // Net Cash: 327.000 - 295.000 = +32.000
        val cashIncome = reportRepository.getCashIncomeTotal(start, end).first() ?: 0L
        val cashExpense = reportRepository.getCashExpenseTotal(start, end).first() ?: 0L
        val totalCashBalance = reportRepository.totalCashBalance.first() ?: 0L

        assertEquals(327000L, cashIncome)
        assertEquals(295000L, cashExpense)
        assertEquals(32000L, cashIncome - cashExpense)
        assertEquals(32000L, totalCashBalance)

        // 4. Customer Debt (Fully Paid)
        assertEquals(0L, reportRepository.totalOutstandingDebt.first() ?: 0L)
        assertEquals(120000L, reportRepository.totalPaidDebt.first() ?: 0L)
        assertEquals(120000L, reportRepository.totalDebtCreated.first() ?: 0L)
        assertEquals(0, reportRepository.openDebtsCount.first())
        assertEquals(1, reportRepository.paidDebtsCount.first())

        // 5. Supplier Payable (Fully Paid)
        assertEquals(0L, reportRepository.totalOutstandingPayable.first() ?: 0L)
        assertEquals(250000L, reportRepository.totalPaidPayable.first() ?: 0L)
        assertEquals(250000L, reportRepository.totalPayableCreated.first() ?: 0L)
        assertEquals(0, reportRepository.openPayablesCount.first())
        assertEquals(1, reportRepository.paidPayablesCount.first())

        // 6. Top Selling Products
        val topProducts = reportRepository.getTopSellingProducts(start, end, limit = 5).first()
        assertEquals(3, topProducts.size)
        // Rank 1: Telur Ayam (4 pcs, 10.000)
        assertEquals("Telur Ayam", topProducts[0].productName)
        assertEquals(4.0, topProducts[0].totalQuantity, 0.001)
        assertEquals(10000L, topProducts[0].totalRevenue)
        // Rank 2: Indomie Goreng (2 pcs, 7.000)
        assertEquals("Indomie Goreng", topProducts[1].productName)
        assertEquals(2.0, topProducts[1].totalQuantity, 0.001)
        assertEquals(7000L, topProducts[1].totalRevenue)
        // Rank 3: Beras Ramos (2 pcs, 120.000)
        assertEquals("Beras Ramos 5kg", topProducts[2].productName)
        assertEquals(2.0, topProducts[2].totalQuantity, 0.001)
        assertEquals(120000L, topProducts[2].totalRevenue)
    }
}



