package id.skmnetwork.bukuwarung

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.repository.CashRepository
import id.skmnetwork.bukuwarung.data.repository.CustomerRepository
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.data.repository.ReportRepository
import id.skmnetwork.bukuwarung.data.repository.SaleRepository
import id.skmnetwork.bukuwarung.data.repository.SupplierRepository
import id.skmnetwork.bukuwarung.ui.report.ReportPeriod
import id.skmnetwork.bukuwarung.ui.report.ReportViewModel
import id.skmnetwork.bukuwarung.ui.report.ReportsScreen
import id.skmnetwork.bukuwarung.ui.theme.BukuWarungTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SimpleAccountingReportTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: AppDatabase
    private lateinit var productRepository: ProductRepository
    private lateinit var saleRepository: SaleRepository
    private lateinit var customerRepository: CustomerRepository
    private lateinit var supplierRepository: SupplierRepository
    private lateinit var cashRepository: CashRepository
    private lateinit var reportRepository: ReportRepository
    private lateinit var reportViewModel: ReportViewModel

    private var catId: Long = 0
    private var prodId: Long = 0
    private var customerId: Long = 0
    private var supplierId: Long = 0

    @Before
    fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        productRepository = ProductRepository(database)
        saleRepository = SaleRepository(database)
        customerRepository = CustomerRepository(database)
        supplierRepository = SupplierRepository(database)
        cashRepository = CashRepository(database)
        reportRepository = ReportRepository(database)
        reportViewModel = ReportViewModel(reportRepository)

        catId = database.categoryDao().insertCategory(CategoryEntity(name = "Makanan"))
        prodId = database.productDao().insertProduct(
            ProductEntity(
                categoryId = catId,
                name = "Kopi Susu",
                purchasePrice = 6000,
                sellingPrice = 10000,
                stock = 100.0
            )
        )
        customerId = customerRepository.saveCustomer("Pak Budi", "08123456789", "Jl. Mawar")
        supplierId = supplierRepository.saveSupplier("PT Kopi Makmur", "08987654321", "Jl. Melati")
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * Requirement 14: ACCOUNTING EXAMPLE TEST
     * Initial Cash = Rp500.000
     * Product: HPP = Rp6.000, Selling = Rp10.000
     * Sale: qty 5 => gross sales = Rp50.000, COGS = Rp30.000
     * Return: qty 2 => return = Rp20.000, return COGS = Rp12.000
     * Operating expense: Rp5.000
     *
     * Expected:
     * Gross Sales = Rp50.000
     * Sales Return = Rp20.000
     * Net Sales = Rp30.000
     * Sale COGS = Rp30.000
     * Return COGS = Rp12.000
     * Net COGS = Rp18.000
     * Gross Profit = Rp12.000
     * Operating Expense = Rp5.000
     * Net Profit = Rp7.000
     */
    @Test
    fun testAccountingExampleScenario() = runBlocking {
        // 1. Initial Cash = Rp500.000
        cashRepository.recordManualIncome(500000L, "Modal Awal")

        // 2. Sale qty 5 (CASH)
        val saleResult = saleRepository.completeSale(
            cartItems = mapOf(prodId to 5.0),
            paymentMethod = "CASH"
        )
        val saleId = saleResult.getOrThrow()

        // 3. Return qty 2
        val saleItems = saleRepository.getItemsForTransaction(saleId)
        val saleItemId = saleItems.first().id

        val returnResult = saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItemId to 2.0),
            reason = "Pelanggan berubah pikiran",
            notes = "Kondisi baik"
        )
        assertTrue(returnResult.isSuccess)

        // 4. Operating Expense = Rp5.000
        cashRepository.recordManualExpense(5000L, "Beli Plastik Kresek")

        val start = 0L
        val end = Long.MAX_VALUE

        val grossSales = reportRepository.getSalesTotal(start, end).first() ?: 0L
        val salesReturn = reportRepository.getSalesReturnTotal(start, end).first() ?: 0L
        val netSales = reportRepository.getNetSalesTotal(start, end).first()

        val saleCogs = reportRepository.getSaleCogsTotal(start, end).first() ?: 0L
        val returnCogs = reportRepository.getReturnCogsTotal(start, end).first() ?: 0L
        val netCogs = reportRepository.getNetCogsTotal(start, end).first()

        val grossProfit = reportRepository.getGrossProfitTotal(start, end).first()
        val operatingExpense = reportRepository.getOperatingExpenseTotal(start, end).first() ?: 0L
        val netProfit = reportRepository.getNetProfitTotal(start, end).first()

        assertEquals(50000L, grossSales)
        assertEquals(20000L, salesReturn)
        assertEquals(30000L, netSales)

        assertEquals(30000L, saleCogs)
        assertEquals(12000L, returnCogs)
        assertEquals(18000L, netCogs)

        assertEquals(12000L, grossProfit)
        assertEquals(5000L, operatingExpense)
        assertEquals(7000L, netProfit)

        // Verify Cash: 500k + 50k (sale) - 20k (refund) - 5k (op expense) = 525k
        val cashBalance = reportRepository.totalCashBalance.first() ?: 0L
        assertEquals(525000L, cashBalance)
    }

    /**
     * Requirement 15: HISTORICAL HPP TEST
     * Sale 1: HPP = Rp6.000
     * Product.purchasePrice changed to Rp7.000
     * Sale 1 report remains HPP = Rp6.000
     * Sale 2: HPP = Rp7.000
     */
    @Test
    fun testHistoricalHppStabilityAfterPriceChange() = runBlocking {
        // Sale 1: 1 pcs @ 10.000 (HPP 6.000)
        saleRepository.completeSale(
            cartItems = mapOf(prodId to 1.0),
            paymentMethod = "CASH"
        ).getOrThrow()

        // Product purchasePrice changes to Rp 7.000
        val currentProd = productRepository.getProductById(prodId)!!
        database.productDao().updateProduct(currentProd.copy(purchasePrice = 7000))

        // Sale 2: 1 pcs @ 10.000 (HPP 7.000)
        saleRepository.completeSale(
            cartItems = mapOf(prodId to 1.0),
            paymentMethod = "CASH"
        ).getOrThrow()

        val start = 0L
        val end = Long.MAX_VALUE

        val netSales = reportRepository.getNetSalesTotal(start, end).first()
        val netCogs = reportRepository.getNetCogsTotal(start, end).first()
        val grossProfit = reportRepository.getGrossProfitTotal(start, end).first()

        assertEquals(20000L, netSales)
        // Expected HPP: 6.000 (Sale 1 snapshot) + 7.000 (Sale 2 snapshot) = 13.000
        assertEquals(13000L, netCogs)
        assertEquals(7000L, grossProfit)
    }

    /**
     * Requirement 16 & 17: NO DOUBLE COUNTING TEST
     * - Debt payment does NOT increase sales revenue
     * - Supplier payment does NOT increase operating expense
     * - Stock purchase does NOT enter operating expense
     * - Return refund does NOT enter operating expense
     * - QRIS sale does NOT enter physical cash
     */
    @Test
    fun testNoDoubleCountingIntegrity() = runBlocking {
        cashRepository.recordManualIncome(200000L, "Modal Kas")

        // 1. Credit Sale Rp 30.000 (3 pcs @ 10.000, COGS 18.000)
        customerRepository.processAtomicCreditCheckout(
            cartItems = mapOf(prodId to 3.0),
            customerId = customerId
        )

        // 2. Debt Payment Rp 10.000
        val debts = database.debtDao().getDebtsForCustomer(customerId).first()
        val debtId = debts.first().id
        customerRepository.processAtomicDebtPayment(debtId, 10000L, "Cicilan 1")

        // 3. Purchase Stock CASH Rp 12.000 (2 pcs @ 6.000)
        productRepository.processAtomicPurchase(
            purchaseItems = mapOf(prodId to 2.0)
        )

        // 4. Purchase Stock CREDIT Rp 18.000 (3 pcs @ 6.000)
        supplierRepository.processAtomicCreditPurchase(
            purchaseItems = mapOf(prodId to 3.0),
            supplierId = supplierId
        )

        // 5. Supplier Payment Rp 15.000
        val payables = database.supplierPayableDao().getPayablesForSupplier(supplierId).first()
        val payableId = payables.first().id
        supplierRepository.processAtomicSupplierPayment(payableId, 15000L, "Bayar Hutang 1")

        // 6. QRIS Sale Rp 10.000 (1 pcs @ 10.000, COGS 6.000)
        saleRepository.completeSale(
            cartItems = mapOf(prodId to 1.0),
            paymentMethod = "QRIS"
        ).getOrThrow()

        // 7. Manual Operating Expense Rp 4.000
        cashRepository.recordManualExpense(4000L, "Listrik Warung")

        val start = 0L
        val end = Long.MAX_VALUE

        // Verify Gross Sales = Credit (30k) + QRIS (10k) = 40k. (Debt payment 10k is NOT sales revenue)
        val grossSales = reportRepository.getSalesTotal(start, end).first() ?: 0L
        assertEquals(40000L, grossSales)

        // Verify Operating Expense = Rp 4.000 ONLY. (Stock purchase, supplier payment, etc. are NOT operating expenses)
        val operatingExpense = reportRepository.getOperatingExpenseTotal(start, end).first() ?: 0L
        assertEquals(4000L, operatingExpense)

        // Sale COGS: Credit 3 pcs (18k) + QRIS 1 pcs (6k) = 24k
        val netCogs = reportRepository.getNetCogsTotal(start, end).first()
        assertEquals(24000L, netCogs)

        // Gross Profit = 40k - 24k = 16k
        val grossProfit = reportRepository.getGrossProfitTotal(start, end).first()
        assertEquals(16000L, grossProfit)

        // Net Profit = 16k - 4k = 12k
        val netProfit = reportRepository.getNetProfitTotal(start, end).first()
        assertEquals(12000L, netProfit)

        // Verify Cash: 200k (initial) + 10k (debt payment) - 12k (cash purchase) - 15k (supplier payment) - 4k (expense) = 179.000
        val cashBalance = reportRepository.totalCashBalance.first() ?: 0L
        assertEquals(179000L, cashBalance)

        // Verify Outstanding Debt: 30k - 10k = 20k
        val outstandingDebt = reportRepository.totalOutstandingDebt.first() ?: 0L
        assertEquals(20000L, outstandingDebt)

        // Verify Outstanding Payable: 18k - 15k = 3k
        val outstandingPayable = reportRepository.totalOutstandingPayable.first() ?: 0L
        assertEquals(3000L, outstandingPayable)
    }

    /**
     * Requirement 7: NEGATIVE PROFIT PRESENTATION
     * When COGS / Expenses exceed sales, profit should be negative (not clamped to 0).
     */
    @Test
    fun testNegativeProfitCalculation() = runBlocking {
        // Product with selling price 5.000 but HPP 8.000 (Rugi Modal)
        val rugiProdId = database.productDao().insertProduct(
            ProductEntity(
                categoryId = catId,
                name = "Promo Cuci Gudang",
                purchasePrice = 8000,
                sellingPrice = 5000,
                stock = 10.0
            )
        )

        // Sale 1 pcs @ 5.000 (COGS 8.000)
        saleRepository.completeSale(
            cartItems = mapOf(rugiProdId to 1.0),
            paymentMethod = "CASH"
        ).getOrThrow()

        // Operating Expense Rp 3.000
        cashRepository.recordManualExpense(3000L, "Ongkir Barang")

        val start = 0L
        val end = Long.MAX_VALUE

        val netSales = reportRepository.getNetSalesTotal(start, end).first()
        val netCogs = reportRepository.getNetCogsTotal(start, end).first()
        val grossProfit = reportRepository.getGrossProfitTotal(start, end).first()
        val operatingExpense = reportRepository.getOperatingExpenseTotal(start, end).first() ?: 0L
        val netProfit = reportRepository.getNetProfitTotal(start, end).first()

        assertEquals(5000L, netSales)
        assertEquals(8000L, netCogs)
        assertEquals(-3000L, grossProfit) // Negative gross profit
        assertEquals(3000L, operatingExpense)
        assertEquals(-6000L, netProfit) // Negative net profit
    }

    /**
     * Requirement 10: FINANCIAL POSITION SEPARATION
     * Verifies Saldo Kas != Laba Bersih, Piutang != Revenue, Hutang != Expense, Nilai Stok != Laba.
     */
    @Test
    fun testFinancialPositionSeparation() = runBlocking {
        cashRepository.recordManualIncome(1000000L, "Modal Tabungan")

        // Total Stock Value = 100 pcs * 6.000 = 600.000
        val stockValue = reportRepository.totalStockValue.first() ?: 0L
        assertEquals(600000L, stockValue)

        val start = 0L
        val end = Long.MAX_VALUE

        val netProfit = reportRepository.getNetProfitTotal(start, end).first()
        val cashBalance = reportRepository.totalCashBalance.first() ?: 0L

        assertEquals(0L, netProfit)
        assertEquals(1000000L, cashBalance)
        assertNotEquals(cashBalance, netProfit)
    }

    /**
     * Requirement 18: UI SMOKE / INTEGRATION TEST
     * Checks Compose nodes: Laba Rugi Sederhana, Rincian Laba expandable, Posisi Keuangan.
     */
    @Test
    fun testReportsScreenUiElements() {
        runBlocking {
            // Add a sale
            saleRepository.completeSale(
                cartItems = mapOf(prodId to 2.0),
                paymentMethod = "CASH"
            ).getOrThrow()
            // Add operating expense
            cashRepository.recordManualExpense(2000L, "Biaya Kebersihan")
            reportViewModel.selectPeriod(ReportPeriod.ALL_TIME)
        }

        composeTestRule.setContent {
            BukuWarungTheme {
                ReportsScreen(reportViewModel = reportViewModel)
            }
        }

        // Verify Main Headers
        composeTestRule.onNodeWithText("Laporan & Laba").assertIsDisplayed()
        composeTestRule.onNodeWithText("Laba Rugi Sederhana").assertIsDisplayed()
        composeTestRule.onNodeWithText("Penjualan Bersih").assertIsDisplayed()
        composeTestRule.onNodeWithText("HPP / Modal Barang Terjual").assertIsDisplayed()
        composeTestRule.onNodeWithText("Laba Kotor").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pengeluaran Operasional").assertIsDisplayed()
        composeTestRule.onNodeWithText("Laba Bersih").assertIsDisplayed()

        // Verify Financial Position
        composeTestRule.onNodeWithText("Posisi Keuangan").assertIsDisplayed()
        composeTestRule.onNodeWithText("Saldo Kas").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nilai Stok Modal").assertIsDisplayed()
        composeTestRule.onNodeWithText("Piutang Pelanggan").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hutang Supplier").assertIsDisplayed()

        // Expand Rincian Laba
        composeTestRule.onNodeWithText("Lihat Rincian Laba").performClick()
        composeTestRule.onNodeWithText("Penjualan Bruto").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retur Penjualan").assertIsDisplayed()
    }
}
