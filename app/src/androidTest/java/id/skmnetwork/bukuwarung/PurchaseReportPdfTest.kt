package id.skmnetwork.bukuwarung

import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.data.repository.PurchaseRepository
import id.skmnetwork.bukuwarung.data.repository.ReportRepository
import id.skmnetwork.bukuwarung.data.repository.SupplierRepository
import id.skmnetwork.bukuwarung.pdf.PdfReportGenerator
import id.skmnetwork.bukuwarung.pdf.PdfShareManager
import id.skmnetwork.bukuwarung.pdf.reports.PurchaseReportPdfBuilder
import id.skmnetwork.bukuwarung.ui.report.ReportPeriod
import id.skmnetwork.bukuwarung.ui.report.ReportViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

/**
 * Gate G.1.5 — Validation Suite for Vertical Slice 4: LAPORAN PEMBELIAN -> PDF.
 * Proves that Purchase Report PDF derives strictly from persisted purchase transactions,
 * correctly distinguishes CASH vs CREDIT purchases, isolates supplier debt repayments,
 * reconciles 100% with authoritative ReportRepository purchase totals,
 * supports multi-page rendering, FileProvider sharing, and guarantees zero database mutation.
 *
 * Scenarios:
 * 1. Empty period
 * 2. Single CASH purchase
 * 3. Single CREDIT purchase
 * 4. Multiple purchases
 * 5. Supplier name
 * 6. Missing/deleted supplier
 * 7. Date range filtering
 * 8. Deterministic descending date ordering
 * 9. Purchase total reconciliation
 * 10. CASH total excludes supplier payments
 * 11. CREDIT total excludes supplier payments
 * 12. PDF structure valid
 * 13. Multi-page purchase report
 * 14. FileProvider/share integration
 * 15. Read-only Room guarantee
 * 16. Accounting regression test: CASH + CREDIT + Supplier Payment separation
 */
@RunWith(AndroidJUnit4::class)
class PurchaseReportPdfTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var productRepository: ProductRepository
    private lateinit var purchaseRepository: PurchaseRepository
    private lateinit var supplierRepository: SupplierRepository
    private lateinit var reportRepository: ReportRepository
    private lateinit var reportViewModel: ReportViewModel
    private lateinit var pdfGenerator: PdfReportGenerator

    private val sampleSettings = UserSettings(
        shopName = "Kios Kiara Pembelian Test",
        address = "Jl. Sukajadi No. 101, Bandung",
        phone = "081244556677"
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        productRepository = ProductRepository(database)
        purchaseRepository = PurchaseRepository(database)
        supplierRepository = SupplierRepository(database)
        reportRepository = ReportRepository(database)
        reportViewModel = ReportViewModel(reportRepository)
        pdfGenerator = PdfReportGenerator(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createProduct(
        name: String = "Beras 5kg",
        purchasePrice: Long = 60000L,
        sellingPrice: Long = 75000L,
        stock: Double = 10.0
    ): Long = runBlocking {
        productRepository.insertProductWithCategory(
            name = name,
            categoryName = "Sembako",
            purchasePrice = purchasePrice,
            sellingPrice = sellingPrice,
            stock = stock,
            minimumStock = 2.0,
            unit = "karung",
            itemType = ItemType.PHYSICAL
        )
    }

    // 1. Empty period
    @Test
    fun test1_emptyPeriod_producesZeroRowsAndValidPdf() = runBlocking {
        val purchaseData = reportViewModel.buildPurchaseReportData(sampleSettings, ReportPeriod.TODAY)

        assertEquals("Kios Kiara Pembelian Test", purchaseData.shopName)
        assertEquals(0, purchaseData.totalTransactions)
        assertEquals(0L, purchaseData.totalPurchases)
        assertEquals(0L, purchaseData.cashPurchasesTotal)
        assertEquals(0L, purchaseData.creditPurchasesTotal)
        assertTrue(purchaseData.items.isEmpty())

        // Generate PDF
        val doc = PurchaseReportPdfBuilder.build(purchaseData)
        val result = pdfGenerator.generatePdf(doc)
        assertTrue(result.isSuccess)

        val file = result.getOrThrow()
        assertTrue(file.exists())
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        assertEquals(1, renderer.pageCount)
        renderer.close()
        pfd.close()
    }

    // 2. Single CASH purchase
    @Test
    fun test2_singleCashPurchase_formattedCorrectly() = runBlocking {
        val prodId = createProduct("Minyak 1L", purchasePrice = 14000L, sellingPrice = 17000L, stock = 10.0)
        purchaseRepository.completePurchase(mapOf(prodId to 5.0), paymentMethod = "CASH").getOrThrow() // 70,000

        val purchaseData = reportViewModel.buildPurchaseReportData(sampleSettings, ReportPeriod.TODAY)

        assertEquals(1, purchaseData.totalTransactions)
        assertEquals(70000L, purchaseData.totalPurchases)
        assertEquals(70000L, purchaseData.cashPurchasesTotal)
        assertEquals(0L, purchaseData.creditPurchasesTotal)

        val row = purchaseData.items.first()
        assertEquals("Tunai", row.paymentMethod)
        assertEquals(70000L, row.totalAmount)
        assertEquals("Lunas", row.status)
        assertEquals("Umum", row.supplierName)
    }

    // 3. Single CREDIT purchase
    @Test
    fun test3_singleCreditPurchase_formattedCorrectly() = runBlocking {
        val prodId = createProduct("Tepung Terigu", purchasePrice = 10000L, sellingPrice = 12000L, stock = 10.0)
        val suppId = supplierRepository.saveSupplier("PT Sembako Makmur", "0811998877", "Gudang Barat")

        supplierRepository.processAtomicCreditPurchase(mapOf(prodId to 10.0), suppId) // 100,000

        val purchaseData = reportViewModel.buildPurchaseReportData(sampleSettings, ReportPeriod.TODAY)

        assertEquals(1, purchaseData.totalTransactions)
        assertEquals(100000L, purchaseData.totalPurchases)
        assertEquals(0L, purchaseData.cashPurchasesTotal)
        assertEquals(100000L, purchaseData.creditPurchasesTotal)

        val row = purchaseData.items.first()
        assertEquals("Kredit", row.paymentMethod)
        assertEquals(100000L, row.totalAmount)
        assertEquals("Kredit", row.status)
        assertEquals("PT Sembako Makmur", row.supplierName)
    }

    // 4. Multiple purchases
    @Test
    fun test4_multiplePurchases_accumulateTotals() = runBlocking {
        val prodA = createProduct("Gula Pasir", purchasePrice = 12000L, sellingPrice = 15000L, stock = 10.0)
        val prodB = createProduct("Garam Dapur", purchasePrice = 2000L, sellingPrice = 3000L, stock = 10.0)

        purchaseRepository.completePurchase(mapOf(prodA to 5.0), "CASH").getOrThrow() // 60,000
        purchaseRepository.completePurchase(mapOf(prodB to 10.0), "CASH").getOrThrow() // 20,000

        val purchaseData = reportViewModel.buildPurchaseReportData(sampleSettings, ReportPeriod.TODAY)

        assertEquals(2, purchaseData.totalTransactions)
        assertEquals(80000L, purchaseData.totalPurchases)
        assertEquals(80000L, purchaseData.cashPurchasesTotal)
    }

    // 5. Supplier name
    @Test
    fun test5_supplierName_displayedCorrectly() = runBlocking {
        val prodId = createProduct("Kopi Kapal", purchasePrice = 1000L, sellingPrice = 1500L, stock = 10.0)
        val suppId = supplierRepository.saveSupplier("Agen Kopi Nusantara", "08123456", "Pasar Induk")

        purchaseRepository.completePurchase(mapOf(prodId to 50.0), "CASH", supplierId = suppId).getOrThrow()

        val purchaseData = reportViewModel.buildPurchaseReportData(sampleSettings, ReportPeriod.TODAY)
        val row = purchaseData.items.first()

        assertEquals("Agen Kopi Nusantara", row.supplierName)
        assertEquals(50000L, row.totalAmount)
    }

    // 6. Missing/deleted supplier
    @Test
    fun test6_missingSupplier_fallsBackToUmum() = runBlocking {
        val prodId = createProduct("Mie Instan", purchasePrice = 2500L, sellingPrice = 3500L, stock = 10.0)
        purchaseRepository.completePurchase(mapOf(prodId to 10.0), "CASH", supplierId = null).getOrThrow()

        val purchaseData = reportViewModel.buildPurchaseReportData(sampleSettings, ReportPeriod.TODAY)
        val row = purchaseData.items.first()

        assertEquals("Umum", row.supplierName)
    }

    // 7. Date range filtering
    @Test
    fun test7_dateRangeFiltering_excludesOutsideTransactions() = runBlocking {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -10) // 10 days ago
        val tenDaysAgo = cal.timeInMillis

        val prodA = createProduct("Barang A", purchasePrice = 10000L, sellingPrice = 12000L, stock = 10.0)
        val prodB = createProduct("Barang B", purchasePrice = 20000L, sellingPrice = 25000L, stock = 10.0)

        // 10 days ago purchase
        purchaseRepository.completePurchase(mapOf(prodA to 2.0), "CASH", now = tenDaysAgo).getOrThrow() // 20,000
        // Today purchase
        purchaseRepository.completePurchase(mapOf(prodB to 3.0), "CASH").getOrThrow() // 60,000

        val todayData = reportViewModel.buildPurchaseReportData(sampleSettings, ReportPeriod.TODAY)
        assertEquals(1, todayData.totalTransactions)
        assertEquals(60000L, todayData.totalPurchases)

        val allTimeData = reportViewModel.buildPurchaseReportData(sampleSettings, ReportPeriod.ALL_TIME)
        assertEquals(2, allTimeData.totalTransactions)
        assertEquals(80000L, allTimeData.totalPurchases)
    }

    // 8. Deterministic descending date ordering
    @Test
    fun test8_deterministicDescendingDateOrdering() = runBlocking {
        val prodId = createProduct("Item", purchasePrice = 5000L, sellingPrice = 7000L, stock = 100.0)

        val now = System.currentTimeMillis()
        val t1 = now - 50000 // oldest
        val t2 = now - 30000
        val t3 = now - 10000 // newest

        purchaseRepository.completePurchase(mapOf(prodId to 1.0), "CASH", now = t1).getOrThrow()
        purchaseRepository.completePurchase(mapOf(prodId to 2.0), "CASH", now = t2).getOrThrow()
        purchaseRepository.completePurchase(mapOf(prodId to 3.0), "CASH", now = t3).getOrThrow()

        val purchaseData = reportViewModel.buildPurchaseReportData(sampleSettings, ReportPeriod.TODAY)

        assertEquals(3, purchaseData.totalTransactions)
        assertEquals(5000L * 3, purchaseData.items[0].totalAmount) // t3 newest = 15,000
        assertEquals(5000L * 2, purchaseData.items[1].totalAmount) // t2 middle = 10,000
        assertEquals(5000L * 1, purchaseData.items[2].totalAmount) // t1 oldest = 5,000
    }

    // 9. Purchase total reconciliation
    @Test
    fun test9_purchaseTotalReconciliation_matchesReportRepository() = runBlocking {
        val prodId = createProduct("Susu Kental", purchasePrice = 11000L, sellingPrice = 13000L, stock = 20.0)
        purchaseRepository.completePurchase(mapOf(prodId to 4.0), "CASH").getOrThrow() // 44,000

        val range = ReportViewModel.calculateDateRange(ReportPeriod.TODAY)
        val authoritativeTotal = reportRepository.getPurchaseTotal(range.startDate, range.endDate).first() ?: 0L

        val purchaseData = reportViewModel.buildPurchaseReportData(sampleSettings, ReportPeriod.TODAY)
        val sumRows = purchaseData.items.sumOf { it.totalAmount }

        assertEquals(authoritativeTotal, sumRows)
        assertEquals(authoritativeTotal, purchaseData.totalPurchases)
        assertEquals(44000L, authoritativeTotal)
    }

    // 10. CASH total excludes supplier payments
    @Test
    fun test10_cashTotalExcludesSupplierPayments() = runBlocking {
        val prodId = createProduct("Sabun Cuci", purchasePrice = 8000L, sellingPrice = 10000L, stock = 20.0)
        val suppId = supplierRepository.saveSupplier("Distributor Sabun", "081234", "Alamat")

        // 1. Credit Purchase = 80,000
        supplierRepository.processAtomicCreditPurchase(mapOf(prodId to 10.0), suppId).getOrThrow()
        val payableId = database.supplierPayableDao().getOpenPayablesForSupplierList(suppId).first().id

        // 2. Cash Purchase = 24,000
        purchaseRepository.completePurchase(mapOf(prodId to 3.0), "CASH").getOrThrow()

        // 3. Supplier Payment = 50,000 (pays debt, creates CashTransaction EXPENSE)
        supplierRepository.processAtomicSupplierPayment(payableId, 50000L, "Bayar sebagian").getOrThrow()

        val purchaseData = reportViewModel.buildPurchaseReportData(sampleSettings, ReportPeriod.TODAY)

        // Cash purchases should only be 24,000 (NOT including the 50,000 payment)
        assertEquals(24000L, purchaseData.cashPurchasesTotal)
        assertEquals(80000L, purchaseData.creditPurchasesTotal)
        assertEquals(104000L, purchaseData.totalPurchases)
    }

    // 11. CREDIT total excludes supplier payments
    @Test
    fun test11_creditTotalExcludesSupplierPayments() = runBlocking {
        val prodId = createProduct("Biskuit", purchasePrice = 4000L, sellingPrice = 6000L, stock = 20.0)
        val suppId = supplierRepository.saveSupplier("Agen Biskuit", "0855", "Alamat")

        supplierRepository.processAtomicCreditPurchase(mapOf(prodId to 5.0), suppId).getOrThrow() // 20,000
        val payableId = database.supplierPayableDao().getOpenPayablesForSupplierList(suppId).first().id
        supplierRepository.processAtomicSupplierPayment(payableId, 20000L, "Bayar lunas").getOrThrow()

        val purchaseData = reportViewModel.buildPurchaseReportData(sampleSettings, ReportPeriod.TODAY)

        assertEquals(20000L, purchaseData.creditPurchasesTotal)
        assertEquals(1, purchaseData.totalTransactions)
    }

    // 12. PDF structure valid
    @Test
    fun test12_pdfReportDocumentStructure_containsExpectedElements() = runBlocking {
        val prodId = createProduct("Teh Pucuk", purchasePrice = 2500L, sellingPrice = 3500L, stock = 50.0)
        purchaseRepository.completePurchase(mapOf(prodId to 10.0), "CASH").getOrThrow()

        val purchaseData = reportViewModel.buildPurchaseReportData(sampleSettings, ReportPeriod.TODAY)
        val doc = PurchaseReportPdfBuilder.build(purchaseData)

        assertEquals("Kios Kiara Pembelian Test", doc.header.shopName)
        assertEquals("LAPORAN PEMBELIAN", doc.header.reportTitle)
        assertEquals(2, doc.sections.size)

        val summarySection = doc.sections[0]
        assertEquals("RINGKASAN PEMBELIAN", summarySection.title)
        assertTrue(summarySection.summaryPairs.any { it.label == "Total Transaksi Pembelian" && it.value == "1 transaksi" })
        assertTrue(summarySection.summaryPairs.any { it.label == "Total Pembelian (Kulakan)" && it.value.contains("25.000") })

        val tableSection = doc.sections[1]
        assertTrue(tableSection.title?.contains("DAFTAR PEMBELIAN") == true)
        assertEquals(6, tableSection.tableColumns.size) // No, Waktu / No. Trx, Supplier, Metode, Total, Status
        assertEquals(2, tableSection.tableRows.size) // 1 data row + 1 total row

        val result = pdfGenerator.generatePdf(doc)
        assertTrue(result.isSuccess)
    }

    // 13. Multi-page purchase report
    @Test
    fun test13_multiPagePurchaseReport_paginatesAcrossPages() = runBlocking {
        val prodId = createProduct("Snack", purchasePrice = 1000L, sellingPrice = 2000L, stock = 1000.0)

        // Insert 60 purchases to guarantee multi-page rendering
        for (i in 1..60) {
            purchaseRepository.completePurchase(mapOf(prodId to 1.0), "CASH").getOrThrow()
        }

        val purchaseData = reportViewModel.buildPurchaseReportData(sampleSettings, ReportPeriod.TODAY)
        assertEquals(60, purchaseData.totalTransactions)

        val doc = PurchaseReportPdfBuilder.build(purchaseData)
        val result = pdfGenerator.generatePdf(doc)
        assertTrue(result.isSuccess)

        val file = result.getOrThrow()
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        assertTrue("Expected >= 2 pages for 60 purchase rows, got ${renderer.pageCount}", renderer.pageCount >= 2)
        renderer.close()
        pfd.close()
    }

    // 14. FileProvider/share integration
    @Test
    fun test14_fileProviderAndShareIntegration() = runBlocking {
        val prodId = createProduct("Kopi Instan", purchasePrice = 1500L, sellingPrice = 2000L, stock = 20.0)
        purchaseRepository.completePurchase(mapOf(prodId to 2.0), "CASH").getOrThrow()

        val purchaseData = reportViewModel.buildPurchaseReportData(sampleSettings, ReportPeriod.TODAY)
        val doc = PurchaseReportPdfBuilder.build(purchaseData)
        val file = pdfGenerator.generatePdf(doc).getOrThrow()

        val shareIntent = PdfShareManager.createSharePdfIntent(context, file, "Bagikan Laporan Pembelian")
        val viewIntent = PdfShareManager.createViewPdfIntent(context, file)

        assertEquals(Intent.ACTION_CHOOSER, shareIntent.action)
        val targetedIntent = shareIntent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(targetedIntent)
        assertEquals(Intent.ACTION_SEND, targetedIntent!!.action)
        assertEquals("application/pdf", targetedIntent.type)
        assertTrue((targetedIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)

        assertEquals(Intent.ACTION_VIEW, viewIntent.action)
        assertEquals("application/pdf", viewIntent.type)
        assertTrue((viewIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
    }

    // 15. Read-only Room guarantee
    @Test
    fun test15_readOnlyGuarantee_databaseUntouchedDuringPdfGeneration() = runBlocking {
        val prodId = createProduct("Permen", purchasePrice = 500L, sellingPrice = 1000L, stock = 50.0)
        purchaseRepository.completePurchase(purchaseItems = mapOf(prodId to 2.0), paymentMethod = "CASH").getOrThrow()

        val beforeCategories = database.categoryDao().getAllCategories().first().size
        val beforeProducts = database.productDao().getAllProducts().first().size
        val beforePurchases = database.purchaseDao().getAllPurchaseTransactions().first().size
        val beforeCash = database.cashDao().getAllCashTransactions().first().size
        val beforeDebts = database.debtDao().getOpenDebtsCount().first()
        val beforePayables = database.supplierPayableDao().getOpenPayablesCount().first()

        // Build data & generate PDF
        val purchaseData = reportViewModel.buildPurchaseReportData(sampleSettings, ReportPeriod.TODAY)
        val doc = PurchaseReportPdfBuilder.build(purchaseData)
        val result = pdfGenerator.generatePdf(doc)
        assertTrue(result.isSuccess)

        // Assert all Room tables remain untouched
        assertEquals(beforeCategories, database.categoryDao().getAllCategories().first().size)
        assertEquals(beforeProducts, database.productDao().getAllProducts().first().size)
        assertEquals(beforePurchases, database.purchaseDao().getAllPurchaseTransactions().first().size)
        assertEquals(beforeCash, database.cashDao().getAllCashTransactions().first().size)
        assertEquals(beforeDebts, database.debtDao().getOpenDebtsCount().first())
        assertEquals(beforePayables, database.supplierPayableDao().getOpenPayablesCount().first())
    }

    // 16. Accounting regression test: CASH + CREDIT + Supplier Payment separation
    @Test
    fun test16_accountingRegression_cashAndCreditAndPaymentSeparation() = runBlocking {
        val prodId = createProduct("Beras Ramos", purchasePrice = 50000L, sellingPrice = 60000L, stock = 50.0)
        val suppId = supplierRepository.saveSupplier("Gudang Beras", "0812345", "Jl Pergudangan")

        // 1. CASH Purchase = 100,000 (2 pcs @ 50,000) -> creates PurchaseTransaction + CashTransaction EXPENSE
        purchaseRepository.completePurchase(mapOf(prodId to 2.0), "CASH").getOrThrow()

        // 2. CREDIT Purchase = 150,000 (3 pcs @ 50,000) -> creates PurchaseTransaction + SupplierPayable (NO CashTransaction)
        supplierRepository.processAtomicCreditPurchase(mapOf(prodId to 3.0), suppId).getOrThrow()
        val payableId = database.supplierPayableDao().getOpenPayablesForSupplierList(suppId).first().id

        // 3. Supplier Payment = 80,000 -> creates SupplierPayment + CashTransaction EXPENSE (NO PurchaseTransaction)
        supplierRepository.processAtomicSupplierPayment(payableId, 80000L, "Bayar hutang").getOrThrow()

        val range = ReportViewModel.calculateDateRange(ReportPeriod.TODAY)
        val purchaseData = reportViewModel.buildPurchaseReportData(sampleSettings, ReportPeriod.TODAY)

        // VERIFICATION:
        // Total Purchase MUST BE 250,000 (100,000 Cash + 150,000 Credit)
        assertEquals(250000L, purchaseData.totalPurchases)
        assertEquals(100000L, purchaseData.cashPurchasesTotal)
        assertEquals(150000L, purchaseData.creditPurchasesTotal)
        assertEquals(2, purchaseData.totalTransactions)

        // Authoritative ReportRepository check
        val authoritativePurchaseTotal = reportRepository.getPurchaseTotal(range.startDate, range.endDate).first() ?: 0L
        assertEquals(250000L, authoritativePurchaseTotal)
        assertEquals(authoritativePurchaseTotal, purchaseData.totalPurchases)

        // Ensure Supplier Payment did NOT increase purchase count or purchase total
        val purchaseCount = reportRepository.getPurchaseCount(range.startDate, range.endDate).first()
        assertEquals(2, purchaseCount)
    }
}
