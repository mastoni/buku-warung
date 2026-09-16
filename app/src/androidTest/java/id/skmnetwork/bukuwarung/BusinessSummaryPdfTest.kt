package id.skmnetwork.bukuwarung

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CashTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.data.repository.CustomerRepository
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.data.repository.ReportRepository
import id.skmnetwork.bukuwarung.data.repository.SaleRepository
import id.skmnetwork.bukuwarung.data.repository.SupplierRepository
import id.skmnetwork.bukuwarung.pdf.PdfReportGenerator
import id.skmnetwork.bukuwarung.pdf.reports.BusinessSummaryPdfBuilder
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

/**
 * Gate G.1.2 — Validation Suite for Vertical Slice: RINGKASAN USAHA -> PDF.
 * Proves that all PDF figures strictly derive from ReportRepository accounting source of truth.
 *
 * Scenarios:
 * 1. Empty period
 * 2. Sales tanpa return
 * 3. Sales + return
 * 4. Sales + HPP snapshot
 * 5. Sales + operating expense
 * 6. Net profit calculation
 * 7. Cash / current position
 * 8. Receivable (Piutang)
 * 9. Payable (Hutang)
 * 10. Selected date range
 * 11. Read-only guarantee (Zero Room DB mutation)
 */
@RunWith(AndroidJUnit4::class)
class BusinessSummaryPdfTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var productRepository: ProductRepository
    private lateinit var customerRepository: CustomerRepository
    private lateinit var supplierRepository: SupplierRepository
    private lateinit var saleRepository: SaleRepository
    private lateinit var reportRepository: ReportRepository
    private lateinit var reportViewModel: ReportViewModel
    private lateinit var pdfGenerator: PdfReportGenerator

    private val sampleSettings = UserSettings(
        shopName = "Kios Kiara Test",
        address = "Jl. Pasar Baru No. 12",
        phone = "081234567890"
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        productRepository = ProductRepository(database)
        customerRepository = CustomerRepository(database)
        supplierRepository = SupplierRepository(database)
        saleRepository = SaleRepository(database)
        reportRepository = ReportRepository(database)
        reportViewModel = ReportViewModel(reportRepository)
        pdfGenerator = PdfReportGenerator(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createProduct(
        name: String = "Kopi Sachet",
        purchasePrice: Long = 3000L,
        sellingPrice: Long = 5000L,
        stock: Double = 100.0
    ): Long = runBlocking {
        productRepository.insertProductWithCategory(
            name = name,
            categoryName = "Minuman",
            purchasePrice = purchasePrice,
            sellingPrice = sellingPrice,
            stock = stock,
            minimumStock = 2.0,
            unit = "pcs",
            itemType = ItemType.PHYSICAL
        )
    }

    // 1. Empty period
    @Test
    fun test1_emptyPeriod_producesZeroValuesAndValidPdf() = runBlocking {
        val summaryData = reportViewModel.buildBusinessSummaryData(sampleSettings, ReportPeriod.TODAY)

        assertEquals("Kios Kiara Test", summaryData.shopName)
        assertEquals(0L, summaryData.grossSales)
        assertEquals(0L, summaryData.salesReturn)
        assertEquals(0L, summaryData.netSales)
        assertEquals(0, summaryData.salesCount)
        assertEquals(0L, summaryData.netCogs)
        assertEquals(0L, summaryData.grossProfit)
        assertEquals(0L, summaryData.operatingExpense)
        assertEquals(0L, summaryData.netProfit)
        assertEquals(0L, summaryData.cashBalance)
        assertEquals(0L, summaryData.stockValue)
        assertEquals(0L, summaryData.outstandingDebt)
        assertEquals(0L, summaryData.outstandingPayable)

        // Generate PDF
        val doc = BusinessSummaryPdfBuilder.build(summaryData)
        val result = pdfGenerator.generatePdf(doc)
        assertTrue(result.isSuccess)

        val file = result.getOrThrow()
        assertTrue(file.exists())
        assertTrue(file.length() > 0)

        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        assertEquals(1, renderer.pageCount)
        renderer.close()
        pfd.close()
    }

    // 2. Sales tanpa return
    @Test
    fun test2_salesWithoutReturn_matchesReportRepository() = runBlocking {
        val prodId = createProduct("Kopi Susu", purchasePrice = 3000L, sellingPrice = 5000L, stock = 100.0)

        // 2 Sales transactions (2 pcs @ 5000 = 10000, 3 pcs @ 5000 = 15000)
        saleRepository.completeSale(cartItems = mapOf(prodId to 2.0), paymentMethod = "CASH").getOrThrow()
        saleRepository.completeSale(cartItems = mapOf(prodId to 3.0), paymentMethod = "CASH").getOrThrow()

        val range = ReportViewModel.calculateDateRange(ReportPeriod.TODAY)
        val repoGrossSales = reportRepository.getSalesTotal(range.startDate, range.endDate).first() ?: 0L
        val repoNetSales = reportRepository.getNetSalesTotal(range.startDate, range.endDate).first()

        val summaryData = reportViewModel.buildBusinessSummaryData(sampleSettings, ReportPeriod.TODAY)

        assertEquals(25000L, repoGrossSales)
        assertEquals(repoGrossSales, summaryData.grossSales)
        assertEquals(0L, summaryData.salesReturn)
        assertEquals(repoNetSales, summaryData.netSales)
        assertEquals(2, summaryData.salesCount)
    }

    // 3. Sales + return
    @Test
    fun test3_salesWithReturn_matchesReportRepository() = runBlocking {
        val prodId = createProduct("Teh Botol", purchasePrice = 2000L, sellingPrice = 4000L, stock = 50.0)

        // Sell 5 pcs = 20,000
        val saleId = saleRepository.completeSale(cartItems = mapOf(prodId to 5.0), paymentMethod = "CASH").getOrThrow()
        val saleItems = saleRepository.getItemsForTransaction(saleId)
        val saleItemId = saleItems.first().id

        // Return 1 pcs = 4,000
        val returnResult = saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItemId to 1.0),
            reason = "Rusak"
        )
        assertTrue(returnResult.isSuccess)

        val range = ReportViewModel.calculateDateRange(ReportPeriod.TODAY)
        val expectedNetSales = reportRepository.getNetSalesTotal(range.startDate, range.endDate).first()
        val expectedReturn = reportRepository.getSalesReturnTotal(range.startDate, range.endDate).first() ?: 0L

        val summaryData = reportViewModel.buildBusinessSummaryData(sampleSettings, ReportPeriod.TODAY)

        assertEquals(20000L, summaryData.grossSales)
        assertEquals(4000L, expectedReturn)
        assertEquals(4000L, summaryData.salesReturn)
        assertEquals(16000L, expectedNetSales)
        assertEquals(16000L, summaryData.netSales)
        assertEquals(1, summaryData.salesReturnCount)
    }

    // 4. Sales + HPP snapshot
    @Test
    fun test4_salesWithHppSnapshot_matchesReportRepository() = runBlocking {
        val prodId = createProduct("Beras 5kg", purchasePrice = 60000L, sellingPrice = 75000L, stock = 10.0)

        // Sell 2 pcs = 150,000 (COGS = 120,000, Gross Profit = 30,000)
        saleRepository.completeSale(cartItems = mapOf(prodId to 2.0), paymentMethod = "CASH").getOrThrow()

        val range = ReportViewModel.calculateDateRange(ReportPeriod.TODAY)
        val repoCogs = reportRepository.getNetCogsTotal(range.startDate, range.endDate).first()
        val repoGrossProfit = reportRepository.getGrossProfitTotal(range.startDate, range.endDate).first()

        val summaryData = reportViewModel.buildBusinessSummaryData(sampleSettings, ReportPeriod.TODAY)

        assertEquals(120000L, repoCogs)
        assertEquals(120000L, summaryData.netCogs)
        assertEquals(30000L, repoGrossProfit)
        assertEquals(30000L, summaryData.grossProfit)
    }

    // 5. Sales + operating expense
    @Test
    fun test5_salesWithOperatingExpense_matchesReportRepository() = runBlocking {
        database.cashDao().insertCashTransaction(
            CashTransactionEntity(
                type = "EXPENSE",
                amount = 15000L,
                description = "Token Listrik Warung"
            )
        )

        val range = ReportViewModel.calculateDateRange(ReportPeriod.TODAY)
        val repoOpExpense = reportRepository.getOperatingExpenseTotal(range.startDate, range.endDate).first() ?: 0L

        val summaryData = reportViewModel.buildBusinessSummaryData(sampleSettings, ReportPeriod.TODAY)

        assertEquals(15000L, repoOpExpense)
        assertEquals(15000L, summaryData.operatingExpense)
    }

    // 6. Net profit calculation
    @Test
    fun test6_netProfitCalculation_matchesReportRepository() = runBlocking {
        val prodId = createProduct("Keripik", purchasePrice = 5000L, sellingPrice = 10000L, stock = 20.0)

        // Sell 5 pcs = 50,000 (COGS = 25,000, Gross Profit = 25,000)
        saleRepository.completeSale(cartItems = mapOf(prodId to 5.0), paymentMethod = "CASH").getOrThrow()

        // Operating expense = 10,000 -> Net profit = 15,000
        database.cashDao().insertCashTransaction(
            CashTransactionEntity(
                type = "EXPENSE",
                amount = 10000L,
                description = "Iuran Kebersihan"
            )
        )

        val range = ReportViewModel.calculateDateRange(ReportPeriod.TODAY)
        val repoNetProfit = reportRepository.getNetProfitTotal(range.startDate, range.endDate).first()

        val summaryData = reportViewModel.buildBusinessSummaryData(sampleSettings, ReportPeriod.TODAY)

        assertEquals(25000L, summaryData.grossProfit)
        assertEquals(10000L, summaryData.operatingExpense)
        assertEquals(15000L, repoNetProfit)
        assertEquals(15000L, summaryData.netProfit)
    }

    // 7. Cash / current position
    @Test
    fun test7_cashAndStockPosition_matchesReportRepository() = runBlocking {
        createProduct("Minyak Goreng 2L", purchasePrice = 30000L, sellingPrice = 35000L, stock = 10.0) // stock val = 300,000

        database.cashDao().insertCashTransaction(
            CashTransactionEntity(
                type = "INCOME",
                amount = 500000L,
                description = "Setoran Modal Awal"
            )
        )
        database.cashDao().insertCashTransaction(
            CashTransactionEntity(
                type = "EXPENSE",
                amount = 50000L,
                description = "Beli Perlengkapan"
            )
        )

        val repoCash = reportRepository.totalCashBalance.first() ?: 0L
        val repoStock = reportRepository.totalStockValue.first() ?: 0L

        val summaryData = reportViewModel.buildBusinessSummaryData(sampleSettings, ReportPeriod.ALL_TIME)

        assertEquals(450000L, repoCash)
        assertEquals(450000L, summaryData.cashBalance)
        assertEquals(300000L, repoStock)
        assertEquals(300000L, summaryData.stockValue)
    }

    // 8. Receivable (Piutang)
    @Test
    fun test8_receivable_matchesReportRepository() = runBlocking {
        val prodId = createProduct("Gula Pasir", purchasePrice = 12000L, sellingPrice = 15000L, stock = 50.0)
        val custId = customerRepository.saveCustomer("Pak Budi", "08123456789", "Alamat")

        // Credit sale = 75,000 (5 pcs @ 15,000)
        customerRepository.processAtomicCreditCheckout(mapOf(prodId to 5.0), custId)

        val repoDebt = reportRepository.totalOutstandingDebt.first() ?: 0L
        val summaryData = reportViewModel.buildBusinessSummaryData(sampleSettings, ReportPeriod.TODAY)

        assertEquals(75000L, repoDebt)
        assertEquals(75000L, summaryData.outstandingDebt)
    }

    // 9. Payable (Hutang ke supplier)
    @Test
    fun test9_payable_matchesReportRepository() = runBlocking {
        val prodId = createProduct("Tepung Terigu", purchasePrice = 10000L, sellingPrice = 12000L, stock = 10.0)
        val suppId = supplierRepository.saveSupplier("PT Sembako Agen", "08987654321", "Alamat")

        // Credit purchase = 250,000 (25 pcs @ 10,000)
        supplierRepository.processAtomicCreditPurchase(mapOf(prodId to 25.0), suppId)

        val repoPayable = reportRepository.totalOutstandingPayable.first() ?: 0L
        val summaryData = reportViewModel.buildBusinessSummaryData(sampleSettings, ReportPeriod.TODAY)

        assertEquals(250000L, repoPayable)
        assertEquals(250000L, summaryData.outstandingPayable)
    }

    // 10. Selected date range
    @Test
    fun test10_selectedDateRange_correctlyFiltersTransactions() = runBlocking {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -10) // 10 days ago
        val oldTimestamp = cal.timeInMillis

        val prodId = createProduct("Barang Lama", purchasePrice = 1000L, sellingPrice = 2000L, stock = 10.0)

        // Complete sale with 10 days ago timestamp
        saleRepository.completeSale(
            cartItems = mapOf(prodId to 10.0),
            paymentMethod = "CASH",
            now = oldTimestamp
        ).getOrThrow()

        // Today's summary should have 0 sales
        val todaySummary = reportViewModel.buildBusinessSummaryData(sampleSettings, ReportPeriod.TODAY)
        assertEquals(0L, todaySummary.grossSales)

        // All time summary should include the 20000
        val allTimeSummary = reportViewModel.buildBusinessSummaryData(sampleSettings, ReportPeriod.ALL_TIME)
        assertEquals(20000L, allTimeSummary.grossSales)
    }

    // 11. Read-only guarantee: zero Room mutation
    @Test
    fun test11_readOnlyGuarantee_databaseUntouchedDuringPdfGeneration() = runBlocking {
        val prodId = createProduct("Mie Instan", purchasePrice = 2500L, sellingPrice = 3500L, stock = 50.0)
        saleRepository.completeSale(cartItems = mapOf(prodId to 2.0), paymentMethod = "CASH").getOrThrow()

        val beforeCategories = database.categoryDao().getAllCategories().first().size
        val beforeProducts = database.productDao().getAllProducts().first().size
        val beforeSales = database.saleDao().getAllTransactions().first().size
        val beforeCash = database.cashDao().getAllCashTransactions().first().size
        val beforeDebts = database.debtDao().getOpenDebtsCount().first()
        val beforePayables = database.supplierPayableDao().getOpenPayablesCount().first()

        // Build data & generate PDF
        val summaryData = reportViewModel.buildBusinessSummaryData(sampleSettings, ReportPeriod.TODAY)
        val doc = BusinessSummaryPdfBuilder.build(summaryData)
        val result = pdfGenerator.generatePdf(doc)
        assertTrue(result.isSuccess)

        // Assert all Room tables remain unchanged
        assertEquals(beforeCategories, database.categoryDao().getAllCategories().first().size)
        assertEquals(beforeProducts, database.productDao().getAllProducts().first().size)
        assertEquals(beforeSales, database.saleDao().getAllTransactions().first().size)
        assertEquals(beforeCash, database.cashDao().getAllCashTransactions().first().size)
        assertEquals(beforeDebts, database.debtDao().getOpenDebtsCount().first())
        assertEquals(beforePayables, database.supplierPayableDao().getOpenPayablesCount().first())
    }
}
